package org.mpashka.vocabulary.importer;

import org.mpashka.vocabulary.core.Form;
import org.mpashka.vocabulary.core.PartOfSpeech;
import org.mpashka.vocabulary.core.Serbian;
import org.mpashka.vocabulary.core.WiktionaryEntry;
import org.mpashka.vocabulary.importer.WiktionaryMatch.OurForm;
import org.mpashka.vocabulary.importer.WiktionaryMatch.OurWord;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Сверка ударений викисловаря с нашей базой, ничего не меняя: сколько слов он покрывает,
 * у каких частей речи его формы несут ударение, совпадает ли оно с выписанным в старом
 * словаре и подтверждает ли он, что форма через тильду читается с ударением основы.
 *
 * <p>Запуск: {@code ./gradlew :importer:runWiktionaryAccents}; аргументы — слова латиницей,
 * которые показать подробно; примеров по каждому исходу — 15, все —
 * {@code JAVA_TOOL_OPTIONS=-Dsamples=100000}. Нужны выписка ({@link WiktionaryExtract}) и
 * перенесённая база.
 */
// @tag:wiktionary @tag:accent
public final class WiktionaryAccentReport {

    private static final int SAMPLES = Integer.getInteger("samples", 15);

    private WiktionaryAccentReport() {
    }

    public static void main(String[] args) throws IOException, XMLStreamException, SQLException {
        Map<String, List<WiktionaryEntry>> wiktionary = WiktionaryMatch.readExtract();
        printWiktionaryAccents(wiktionary);

        List<OurWord> words;
        try (Connection pg = TargetDatabase.connect(null)) {
            pg.setReadOnly(true);
            words = WiktionaryMatch.readOurWords(pg);
        }
        var counters = new Counters();
        Set<String> detailed = Set.of(args.length > 0 ? args : new String[]{"voda", "abažur", "raditi"});
        for (OurWord word : words) {
            String title = word.title();
            Optional<WiktionaryEntry> entry = WiktionaryMatch.matching(word, wiktionary);
            counters.words.merge(word.partOfSpeech(), new int[]{1, entry.isPresent() ? 1 : 0}, Counters::add);
            if (entry.isPresent()) {
                compare(word, entry.get(), counters, detailed.contains(title));
            } else if (detailed.contains(title)) {
                System.out.printf("%n%s — в викисловаре нет слова этой части речи%n", title);
            }
        }
        counters.print();
    }

    private static void printWiktionaryAccents(Map<String, List<WiktionaryEntry>> wiktionary) {
        Map<PartOfSpeech, int[]> byPart = new TreeMap<>();
        for (List<WiktionaryEntry> entries : wiktionary.values()) {
            for (WiktionaryEntry entry : entries) {
                int[] c = byPart.computeIfAbsent(entry.partOfSpeech(), p -> new int[5]);
                c[0]++;
                c[1] += entry.headword().isPresent() ? 1 : 0;
                c[2] += entry.forms().isEmpty() ? 0 : 1;
                c[3] += entry.forms().size();
                c[4] += (int) entry.forms().stream().filter(f -> f.accented().isPresent()).count();
            }
        }
        System.out.printf("Статей с сербохорватским разделом: %,d%n%n", wiktionary.size());
        System.out.printf("%-14s %8s %14s %14s %9s %15s%n",
                "часть речи", "слов", "с ударением", "с таблицей", "форм", "с ударением");
        byPart.forEach((part, c) -> System.out.printf("%-14s %,8d %,8d %4.0f%% %,8d %4.0f%% %,9d %,9d %4.0f%%%n",
                part.title(), c[0], c[1], percent(c[1], c[0]), c[2], percent(c[2], c[0]),
                c[3], c[4], percent(c[4], c[3])));
    }

    private static void compare(OurWord word, WiktionaryEntry entry, Counters c, boolean detailed) {
        WiktionaryMatch.Identity identity = WiktionaryMatch.identity(word, entry);
        c.identities.merge(word.partOfSpeech() + " " + identity.name(), 1, Integer::sum);
        if (detailed) {
            System.out.printf("%n%s (%s) — викисловарь: %s, %s%n", word.headword(), word.partOfSpeech().title(),
                    entry.headword().orElse("без ударения"), identity.title);
        }
        if (identity == WiktionaryMatch.Identity.UNANCHORED) {
            return;
        }
        Map<String, List<Form>> byKey = WiktionaryMatch.formsByKey(entry);
        c.wiktionaryForms[word.partOfSpeech().ordinal()] += entry.forms().size();
        Set<String> ourKeys = new java.util.HashSet<>();
        Set<String> ourLetters = new java.util.HashSet<>();
        for (OurForm ours : word.forms()) {
            String letters = Serbian.toLatin(ours.plain()).toLowerCase();
            ourKeys.add(ours.grammar() + " " + letters);
            ourLetters.add(letters);
        }
        for (Form form : entry.forms()) {
            if (form.accented().isPresent() && !ourKeys.contains(form.grammar() + " " + form.value().toLowerCase())) {
                c.missing[ourLetters.contains(form.value().toLowerCase()) ? 0 : 1]++;
            }
        }
        for (OurForm ours : word.forms()) {
            if (identity != WiktionaryMatch.Identity.SAME && !ours.grammar().equals("nom.sg")) {
                continue;
            }
            List<Form> theirs = byKey.getOrDefault(ours.latinKey(), List.of());
            List<String> accented = WiktionaryMatch.accentsOn(ours.plain(), theirs);
            Outcome outcome = outcome(word, ours, theirs, accented);
            String kind = ours.grammar().equals("nom.sg") ? "заглавная" : "словоформа";
            c.outcomes.merge(word.partOfSpeech() + " " + kind + " " + outcome, 1, Integer::sum);
            if (detailed) {
                System.out.printf("  %-10s %-16s наше: %-16s викисловарь: %-24s %s%n", ours.grammar(), ours.plain(),
                        Optional.ofNullable(ours.form()).filter(f -> ours.accentSource() != null).orElse("—"),
                        accented.isEmpty() ? "—" : String.join(" / ", accented), outcome.title);
            }
            if (outcome == Outcome.DISAGREE || outcome == Outcome.STEM_TONE_DISAGREE || outcome == Outcome.STEM_TONE_AGREE) {
                c.sample(outcome, String.format("%s %s: наше %s, викисловарь %s", word.headword(), ours.grammar(),
                        ours.form(),
                        String.join(" / ", accented)));
            }
        }
    }

    private enum Outcome {
        NO_COUNTERPART("у викисловаря такой формы нет"),
        NOT_ACCENTED("у викисловаря без ударения"),
        AGREE("совпало"),
        DISAGREE("разошлось"),
        GAIN_RULES("ударение для формы правил"),
        STEM_TONE_AGREE("тон основы подтвердился"),
        STEM_TONE_DISAGREE("тон основы не подтвердился"),
        STEM_TONE_UNKNOWN("словарь без тона, тон основы не вывести");

        private final String title;

        Outcome(String title) {
            this.title = title;
        }
    }

    private static Outcome outcome(OurWord word, OurForm ours, List<Form> theirs, List<String> accented) {
        if (theirs.isEmpty()) {
            return Outcome.NO_COUNTERPART;
        }
        if (accented.isEmpty()) {
            return Outcome.NOT_ACCENTED;
        }
        boolean agrees = WiktionaryMatch.groupAgrees(WiktionaryMatch.group(word, ours), accented);
        if ("RULES".equals(ours.accentSource())) {
            return agrees ? Outcome.STEM_TONE_AGREE : Outcome.STEM_TONE_DISAGREE;
        }
        if (ours.accentSource() != null) {
            return agrees ? Outcome.AGREE : Outcome.DISAGREE;
        }
        return ours.source().equals("SOURCE_DICTIONARY") ? Outcome.STEM_TONE_UNKNOWN : Outcome.GAIN_RULES;
    }

    private static double percent(int part, int whole) {
        return whole == 0 ? 0 : 100.0 * part / whole;
    }

    private static final class Counters {
        final Map<PartOfSpeech, int[]> words = new TreeMap<>();
        final int[] wiktionaryForms = new int[PartOfSpeech.values().length];
        final int[] missing = new int[2];
        final Map<String, Integer> outcomes = new TreeMap<>();
        final Map<String, Integer> identities = new TreeMap<>();
        final Map<Outcome, List<String>> samples = new TreeMap<>();

        static int[] add(int[] a, int[] b) {
            return new int[]{a[0] + b[0], a[1] + b[1]};
        }

        void sample(Outcome outcome, String line) {
            List<String> list = samples.computeIfAbsent(outcome, o -> new ArrayList<>());
            if (list.size() < SAMPLES) {
                list.add(line);
            }
        }

        void print() {
            System.out.printf("%nНаши слова, у которых в викисловаре есть слово той же части речи:%n");
            words.forEach((part, c) -> System.out.printf("  %-14s %,7d из %,7d  %4.1f%%   %s%n",
                    part.title(), c[1], c[0], percent(c[1], c[0]),
                    String.join(", ", java.util.Arrays.stream(WiktionaryMatch.Identity.values())
                            .map(i -> i.title + " " + identities.getOrDefault(part + " " + i.name(), 0)).toList())));
            System.out.printf("Словоформы сверяются только у того же слова, заглавное — ещё и при расхождении.%n");
            System.out.printf("%nНаши формы этих слов по исходу сверки:%n");
            for (PartOfSpeech part : words.keySet()) {
                int total = outcomes.entrySet().stream().filter(e -> e.getKey().startsWith(part + " "))
                        .mapToInt(Map.Entry::getValue).sum();
                if (total == 0) {
                    continue;
                }
                System.out.printf("  %s — %,d форм (у викисловаря %,d):%n", part.title(), total,
                        wiktionaryForms[part.ordinal()]);
                for (String kind : List.of("заглавная", "словоформа")) {
                    for (Outcome outcome : Outcome.values()) {
                        Integer n = outcomes.get(part + " " + kind + " " + outcome);
                        if (n != null) {
                            System.out.printf("    %-10s %-42s %,8d  %4.1f%%%n", kind, outcome.title, n,
                                    percent(n, total));
                        }
                    }
                }
            }
            System.out.printf("%nФормы викисловаря с ударением, которых у нас нет с той же пометой:%n"
                    + "  буквы у нас есть под другой пометой: %,d%n  букв у нас нет вовсе:               %,d%n",
                    missing[0], missing[1]);
            samples.forEach((outcome, lines) -> {
                System.out.printf("%nПримеры — %s:%n", outcome.title);
                lines.forEach(line -> System.out.println("  " + line));
            });
        }
    }
}
