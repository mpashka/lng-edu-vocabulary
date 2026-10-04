package org.mpashka.vocabulary.importer;

import org.mpashka.vocabulary.core.Accent;
import org.mpashka.vocabulary.core.PartOfSpeech;
import org.mpashka.vocabulary.core.Serbian;
import org.mpashka.vocabulary.core.WiktionaryEntry;
import org.mpashka.vocabulary.importer.WiktionaryMatch.OurWord;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Часть речи из викисловаря словам, у которых правила её не определили. Берётся, только если
 * заглавное ударение совпало ровно с одной частью речи: иначе это может быть омограф. Точность
 * меряется тут же — на словах, чья часть речи известна по помете словаря.
 *
 * <p>Запуск после переноса и до записи ударений (слову без части речи викисловарь ударений не
 * подберёт): {@code ./gradlew :importer:migrate :importer:wiktionaryPartsOfSpeech
 * :importer:wiktionaryAccents}. Повторный запуск сначала возвращает записанное прошлым.
 */
// @tag:wiktionary @tag:part-of-speech
public final class WiktionaryPartsOfSpeech {

    private static final String UNKNOWN_REASON = "часть речи не определена";

    /**
     * Часть речи берётся, только если на словах с пометой она совпала не реже — союзы и
     * частицы совпадают в половине случаев и реже, и довод из них никакой.
     */
    private static final double MIN_ACCURACY = 0.95;
    private static final int MIN_CHECKED = 30;

    private WiktionaryPartsOfSpeech() {
    }

    public static void main(String[] args) throws IOException, XMLStreamException, SQLException {
        Map<String, List<WiktionaryEntry>> wiktionary = WiktionaryMatch.readExtract();
        try (Connection pg = TargetDatabase.connect(null)) {
            pg.setAutoCommit(false);
            removePrevious(pg);
            List<OurWord> words = WiktionaryMatch.readOurWords(pg);
            Map<String, Integer> checked = new TreeMap<>();
            Map<String, Integer> applied = new TreeMap<>();
            Map<PartOfSpeech, int[]> accuracy = new EnumMap<>(PartOfSpeech.class);
            try (PreparedStatement update = pg.prepareStatement("""
                    update word
                       set part_of_speech = cast(? as part_of_speech),
                           part_of_speech_source = 'WIKTIONARY',
                           review_reason = nullif(regexp_replace(regexp_replace(review_reason,
                               '%1$s(, )?', ''), ', $', ''), ''),
                           needs_language_review = nullif(regexp_replace(regexp_replace(review_reason,
                               '%1$s(, )?', ''), ', $', ''), '') is not null
                     where id = ?
                       and not exists (select 1 from word o where o.headword_plain = word.headword_plain
                                         and o.part_of_speech = cast(? as part_of_speech)
                                         and o.homonym_index = word.homonym_index)""".formatted(UNKNOWN_REASON))) {
                for (OurWord word : words) {
                    if (word.partOfSpeech() != PartOfSpeech.UNKNOWN) {
                        anchored(word, wiktionary).ifPresent(part -> accuracy
                                .computeIfAbsent(part, p -> new int[2])[part == word.partOfSpeech() ? 0 : 1]++);
                    }
                }
                for (OurWord word : words) {
                    if (word.partOfSpeech() != PartOfSpeech.UNKNOWN) {
                        continue;
                    }
                    Optional<PartOfSpeech> found = anchored(word, wiktionary);
                    if (found.isEmpty()) {
                        checked.merge("сверить не по чему или неоднозначно", 1, Integer::sum);
                    } else if (!trusted(accuracy.get(found.get()))) {
                        checked.merge("часть речи викисловаря ненадёжна (" + found.get().title() + ")", 1,
                                Integer::sum);
                    } else {
                        update.setString(1, found.get().name());
                        update.setLong(2, word.id());
                        update.setString(3, found.get().name());
                        boolean done = update.executeUpdate() == 1;
                        checked.merge(done ? "определена" : "занято: такое слово этой части речи уже есть",
                                1, Integer::sum);
                        if (done) {
                            applied.merge(found.get().title() + ": " + word.headword(), 1, Integer::sum);
                        }
                    }
                }
            }
            pg.commit();
            print(accuracy, checked, applied);
        }
    }

    private static boolean trusted(int[] counts) {
        return counts != null && counts[0] + counts[1] >= MIN_CHECKED
                && counts[0] >= MIN_ACCURACY * (counts[0] + counts[1]);
    }

    /**
     * Части речи статьи, чьё заглавное ударение совпало с нашим по тону; ровно одна, кроме
     * «не определена», — она, иначе пусто.
     */
    private static Optional<PartOfSpeech> anchored(OurWord word, Map<String, List<WiktionaryEntry>> wiktionary) {
        if (Accent.toneCount(word.headword()) == 0) {
            return Optional.empty();
        }
        Set<PartOfSpeech> parts = wiktionary.getOrDefault(word.title().toLowerCase(), List.of()).stream()
                .filter(e -> e.partOfSpeech() != PartOfSpeech.UNKNOWN)
                .filter(e -> e.headword().flatMap(h -> Serbian.withLatinAccents(word.plain(), h))
                        .filter(h -> WiktionaryMatch.sameTone(h, word.headword())).isPresent())
                .map(WiktionaryEntry::partOfSpeech)
                .collect(Collectors.toSet());
        return parts.size() == 1 ? Optional.of(parts.iterator().next()) : Optional.empty();
    }

    private static void removePrevious(Connection pg) throws SQLException {
        try (Statement st = pg.createStatement()) {
            st.executeUpdate("""
                    update word
                       set part_of_speech = 'UNKNOWN',
                           part_of_speech_source = 'RULES',
                           review_reason = case when review_reason is null then '%1$s'
                                                else '%1$s, ' || review_reason end,
                           needs_language_review = true
                     where part_of_speech_source = 'WIKTIONARY'""".formatted(UNKNOWN_REASON));
        }
    }

    private static void print(Map<PartOfSpeech, int[]> accuracy, Map<String, Integer> checked,
                              Map<String, Integer> applied) {
        System.out.printf("Точность по части речи викисловаря на словах с пометой (совпало / разошлось),"
                + " берётся от %.0f%% и %d слов:%n", MIN_ACCURACY * 100, MIN_CHECKED);
        int[] total = new int[2];
        accuracy.forEach((part, c) -> {
            System.out.printf("  %-16s %,6d / %,4d  %5.1f%%  %s%n", part.title(), c[0], c[1],
                    100.0 * c[0] / (c[0] + c[1]), trusted(c) ? "берётся" : "не берётся");
            total[0] += c[0];
            total[1] += c[1];
        });
        System.out.printf("  %-16s %,6d / %,4d  %.1f%%%n", "всего", total[0], total[1],
                100.0 * total[0] / Math.max(1, total[0] + total[1]));
        System.out.printf("%nСлова без части речи:%n");
        checked.forEach((outcome, n) -> System.out.printf("  %-45s %,5d%n", outcome, n));
        System.out.printf("%nОпределено:%n");
        applied.keySet().forEach(line -> System.out.println("  " + line));
    }
}
