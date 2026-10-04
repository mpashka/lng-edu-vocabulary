package org.mpashka.vocabulary.importer;

import org.mpashka.vocabulary.core.Accent;
import org.mpashka.vocabulary.core.Form;
import org.mpashka.vocabulary.core.PartOfSpeech;
import org.mpashka.vocabulary.core.Serbian;
import org.mpashka.vocabulary.core.WiktionaryEntry;
import org.mpashka.vocabulary.importer.WiktionaryMatch.OurForm;
import org.mpashka.vocabulary.importer.WiktionaryMatch.OurWord;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Записывает в базу ударения викисловаря: формам без ударения — его ударение со ссылкой на
 * статью, допустимые варианты — отдельными формами, расхождения с выписанным в словаре — в
 * {@code discrepancy}. Наше ударение не затирается никогда.
 *
 * <p>Запуск после переноса: {@code ./gradlew :importer:migrate :importer:wiktionaryAccents}.
 * Повторный запуск сначала снимает записанное прошлым.
 */
// @tag:wiktionary @tag:accent
public final class WiktionaryAccents {

    /** Служебные слова: викисловарь даёт им ударение при переносе на них ({@code ȉz grada}). */
    private static final Set<PartOfSpeech> UNSTRESSED_PARTS =
            Set.of(PartOfSpeech.PREPOSITION, PartOfSpeech.CONJUNCTION, PartOfSpeech.PARTICLE);

    /** Пометы, которые умеет назвать интерфейс (frontend/src/labels.js). */
    private static final String SHOWN_GRAMMAR = "(nom|gen|dat|acc|voc|loc|ins)\\.(sg|pl)|praes\\.1sg";

    private WiktionaryAccents() {
    }

    public static void main(String[] args) throws IOException, XMLStreamException, SQLException {
        Map<String, List<WiktionaryEntry>> wiktionary = WiktionaryMatch.readExtract();
        try (Connection pg = TargetDatabase.connect(null)) {
            pg.setAutoCommit(false);
            removePrevious(pg);
            List<OurWord> words = WiktionaryMatch.readOurWords(pg);
            var counters = new Counters();
            try (var w = new Writers(pg)) {
                for (OurWord word : words) {
                    Optional<WiktionaryEntry> entry = WiktionaryMatch.matching(word, wiktionary);
                    if (entry.isPresent() && maybeFunctionWord(word, wiktionary)) {
                        counters.functionWordSkipped++;
                    } else if (entry.isPresent()) {
                        apply(word, entry.get(), w, counters);
                    }
                }
            }
            pg.commit();
            counters.print();
        }
    }

    /**
     * Заглавное слово без тона, у которого в викисловаре есть служебное слово с теми же буквами
     * ({@code око}, {@code преко}, {@code из}): словарь писал без тона именно служебное, а
     * часть речи ему наши правила дали неверно. Ударение существительного ему выдумало бы
     * ударение.
     */
    private static boolean maybeFunctionWord(OurWord word, Map<String, List<WiktionaryEntry>> wiktionary) {
        return Accent.toneCount(word.headword()) == 0
                && wiktionary.getOrDefault(word.title().toLowerCase(), List.of()).stream()
                .anyMatch(e -> UNSTRESSED_PARTS.contains(e.partOfSpeech()));
    }

    private static void removePrevious(Connection pg) throws SQLException {
        try (Statement st = pg.createStatement()) {
            st.executeUpdate("delete from word_form where source = 'WIKTIONARY'");
            st.executeUpdate("""
                    update word_form f
                       set form = case when f.grammar = 'nom.sg'
                                       then (select headword from word w where w.id = f.word_id) end,
                           accent_source = null,
                           accent_url = null
                     where accent_source = 'WIKTIONARY'""");
            st.executeUpdate("delete from discrepancy where proposed_source = 'WIKTIONARY' and not resolved");
        }
    }

    private static void apply(OurWord word, WiktionaryEntry entry, Writers w, Counters c) throws SQLException {
        String url = WiktionaryMatch.url(entry);
        Map<String, List<Form>> theirsByKey = WiktionaryMatch.formsByKey(entry);
        Map<String, String> ourPlainByLetters = new HashMap<>();
        Set<String> handled = new java.util.HashSet<>();
        for (OurForm ours : word.forms()) {
            ourPlainByLetters.putIfAbsent(Serbian.toLatin(ours.plain()).toLowerCase(), ours.plain());
            List<String> accented = WiktionaryMatch.accentsOn(ours.plain(),
                    theirsByKey.getOrDefault(ours.latinKey(), List.of()));
            if (accented.isEmpty() || !handled.add(ours.latinKey())) {
                continue;
            }
            List<OurForm> group = WiktionaryMatch.group(word, ours);
            List<OurForm> stressed = group.stream().filter(f -> f.accentSource() != null).toList();
            if (stressed.isEmpty()) {
                if (ours.grammar().equals("nom.sg") && UNSTRESSED_PARTS.contains(word.partOfSpeech())) {
                    c.unstressedSkipped++;
                    continue;
                }
                w.setAccent(ours.id(), accented.getFirst(), url);
                c.filled++;
                addVariants(word, ours, accented.subList(1, accented.size()), url, w, c);
            } else if (WiktionaryMatch.groupAgrees(group, accented)) {
                c.agreed++;
                addVariants(word, ours, accented.stream()
                        .filter(a -> stressed.stream().noneMatch(f -> WiktionaryMatch.sameTone(a, f.form())))
                        .toList(), url, w, c);
            } else {
                w.addDiscrepancy(word.id(), "accent." + ours.grammar(),
                        String.join(" / ", stressed.stream().map(OurForm::form).toList()),
                        String.join(" / ", accented), stressed.getFirst().accentSource(), url);
                c.discrepancies++;
            }
        }
        addFormsUnderOtherGrammar(word, entry, ourPlainByLetters, url, w, c);
    }

    private static void addVariants(OurWord word, OurForm ours, List<String> variants, String url, Writers w,
                                    Counters c) throws SQLException {
        for (String variant : variants) {
            w.insertForm(word.id(), variant, ours.plain(), ours.grammar(), url);
            c.variants++;
        }
    }

    /**
     * Перенос кладёт одинаковые буквы одной строкой ({@code воде} — {@code gen.sg}), а у
     * викисловаря те же буквы под другой пометой несут своё ударение ({@code nom.pl vȍde}).
     */
    private static void addFormsUnderOtherGrammar(OurWord word, WiktionaryEntry entry,
                                                  Map<String, String> ourPlainByLetters, String url, Writers w,
                                                  Counters c) throws SQLException {
        Set<String> ourKeys = new java.util.HashSet<>();
        word.forms().forEach(f -> ourKeys.add(f.latinKey()));
        for (Form form : entry.forms()) {
            String letters = form.value().toLowerCase();
            if (form.accented().isEmpty() || ourKeys.contains(form.grammar() + " " + letters)) {
                continue;
            }
            String plain = ourPlainByLetters.get(letters);
            Optional<String> accented = plain == null ? Optional.empty()
                    : Serbian.withLatinAccents(plain, form.accented().get());
            if (accented.isEmpty()) {
                c.lettersMissing++;
            } else if (!form.grammar().matches(SHOWN_GRAMMAR)) {
                c.grammarNotShown++;
            } else if (ourKeys.add(form.grammar() + " " + letters + " " + accented.get())) {
                w.insertForm(word.id(), accented.get(), plain, form.grammar(), url);
                c.otherGrammar++;
            }
        }
    }

    private static final class Writers implements AutoCloseable {
        private final PreparedStatement accent;
        private final PreparedStatement form;
        private final PreparedStatement discrepancy;

        Writers(Connection pg) throws SQLException {
            accent = pg.prepareStatement("update word_form set form = ?, accent_source = 'WIKTIONARY', "
                    + "accent_url = ? where id = ?");
            form = pg.prepareStatement("insert into word_form (word_id, form, form_plain, grammar, source, "
                    + "accent_source, accent_url, is_preferred) "
                    + "values (?, ?, ?, ?, 'WIKTIONARY', 'WIKTIONARY', ?, false)");
            discrepancy = pg.prepareStatement("""
                    insert into discrepancy (word_id, field, current_value, proposed_value,
                        current_source, proposed_source, proposed_url)
                    select ?, ?, ?, ?, cast(? as data_source), 'WIKTIONARY', ?
                     where not exists (select 1 from discrepancy d
                                        where d.word_id = ? and d.field = ? and d.proposed_value = ?
                                          and d.current_value = ? and d.proposed_source = 'WIKTIONARY')""");
        }

        void setAccent(long formId, String accented, String url) throws SQLException {
            accent.setString(1, accented);
            accent.setString(2, url);
            accent.setLong(3, formId);
            accent.executeUpdate();
        }

        void insertForm(long wordId, String accented, String plain, String grammar, String url)
                throws SQLException {
            form.setLong(1, wordId);
            form.setString(2, accented);
            form.setString(3, plain);
            form.setString(4, grammar);
            form.setString(5, url);
            form.executeUpdate();
        }

        void addDiscrepancy(long wordId, String field, String current, String proposed, String currentSource,
                            String url) throws SQLException {
            discrepancy.setLong(1, wordId);
            discrepancy.setString(2, field);
            discrepancy.setString(3, current);
            discrepancy.setString(4, proposed);
            discrepancy.setString(5, currentSource);
            discrepancy.setString(6, url);
            discrepancy.setLong(7, wordId);
            discrepancy.setString(8, field);
            discrepancy.setString(9, proposed);
            discrepancy.setString(10, current);
            discrepancy.executeUpdate();
        }

        @Override
        public void close() throws SQLException {
            for (PreparedStatement st : List.of(accent, form, discrepancy)) {
                st.close();
            }
        }
    }

    private static final class Counters {
        int filled;
        int variants;
        int otherGrammar;
        int agreed;
        int discrepancies;
        int unstressedSkipped;
        int functionWordSkipped;
        int lettersMissing;
        int grammarNotShown;

        void print() {
            System.out.printf("Ударение из викисловаря записано:%n");
            System.out.printf("  формам без ударения:                  %,7d%n", filled);
            System.out.printf("  допустимым вариантам — новыми формами: %,7d%n", variants);
            System.out.printf("  формам под другой пометой — новыми:    %,7d%n", otherGrammar);
            System.out.printf("Совпало с нашим ударением:              %,7d%n", agreed);
            System.out.printf("Расхождений в discrepancy:              %,7d%n", discrepancies);
            System.out.printf("Не записано:%n");
            System.out.printf("  служебные слова (безударные):          %,7d%n", unstressedSkipped);
            System.out.printf("  без тона, а в викисловаре служебное:   %,7d слов%n", functionWordSkipped);
            System.out.printf("  букв у нас нет — нечем записать:       %,7d%n", lettersMissing);
            System.out.printf("  помету интерфейс не покажет:           %,7d%n", grammarNotShown);
        }
    }
}
