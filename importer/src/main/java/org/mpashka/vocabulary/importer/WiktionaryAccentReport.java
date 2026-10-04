package org.mpashka.vocabulary.importer;

import org.mpashka.vocabulary.core.Accent;
import org.mpashka.vocabulary.core.Form;
import org.mpashka.vocabulary.core.PartOfSpeech;
import org.mpashka.vocabulary.core.Serbian;
import org.mpashka.vocabulary.core.WiktionaryEntry;
import org.mpashka.vocabulary.core.WiktionaryParser;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
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

    private static final Path EXTRACT = Path.of(".data/wiktionary/sh-pages.xml.gz");
    private static final int SAMPLES = Integer.getInteger("samples", 15);

    private record OurForm(String form, String plain, String grammar, String source, String accentSource) {
    }

    private record OurWord(long id, String headword, String plain, PartOfSpeech partOfSpeech, List<OurForm> forms) {
    }

    private WiktionaryAccentReport() {
    }

    public static void main(String[] args) throws IOException, XMLStreamException, SQLException {
        Map<String, List<WiktionaryEntry>> wiktionary = readExtract();
        printWiktionaryAccents(wiktionary);

        List<OurWord> words = readOurWords();
        var counters = new Counters();
        Set<String> detailed = Set.of(args.length > 0 ? args : new String[]{"voda", "abažur", "raditi"});
        for (OurWord word : words) {
            String title = Serbian.toLatin(word.plain);
            Optional<WiktionaryEntry> entry = matching(word, wiktionary.getOrDefault(title, List.of()));
            counters.words.merge(word.partOfSpeech, new int[]{1, entry.isPresent() ? 1 : 0}, Counters::add);
            if (entry.isPresent()) {
                compare(word, entry.get(), counters, detailed.contains(title));
            } else if (detailed.contains(title)) {
                System.out.printf("%n%s — в викисловаре нет слова этой части речи%n", title);
            }
        }
        counters.print();
    }

    private static Map<String, List<WiktionaryEntry>> readExtract() throws IOException, XMLStreamException {
        Map<String, List<WiktionaryEntry>> byTitle = new HashMap<>();
        WiktionaryPages.forEachArticle(EXTRACT, page -> {
            List<WiktionaryEntry> entries = WiktionaryParser.serboCroatian(page.title(), page.text());
            if (!entries.isEmpty()) {
                byTitle.computeIfAbsent(page.title().toLowerCase(), t -> new ArrayList<>()).addAll(entries);
            }
        });
        return byTitle;
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

    private static List<OurWord> readOurWords() throws SQLException {
        Map<Long, OurWord> words = new HashMap<>();
        List<OurWord> ordered = new ArrayList<>();
        try (Connection pg = TargetDatabase.connect(null); Statement st = pg.createStatement()) {
            pg.setReadOnly(true);
            try (ResultSet rs = st.executeQuery(
                    "select id, headword, headword_plain, part_of_speech from word order by id")) {
                while (rs.next()) {
                    OurWord word = new OurWord(rs.getLong(1), rs.getString(2), rs.getString(3),
                            PartOfSpeech.valueOf(rs.getString(4)), new ArrayList<>());
                    words.put(word.id, word);
                    ordered.add(word);
                }
            }
            try (ResultSet rs = st.executeQuery("select word_id, form, form_plain, grammar, source, accent_source "
                    + "from word_form where grammar <> 'nom.sg.lat'")) {
                while (rs.next()) {
                    words.get(rs.getLong(1)).forms.add(new OurForm(rs.getString(2), rs.getString(3),
                            rs.getString(4).replace(".вариант", ""), rs.getString(5), rs.getString(6)));
                }
            }
        }
        return ordered;
    }

    /** Слово той же части речи; у омонимов — то, чьё заглавное ударение совпало с нашим. */
    private static Optional<WiktionaryEntry> matching(OurWord word, List<WiktionaryEntry> entries) {
        List<WiktionaryEntry> samePart = entries.stream()
                .filter(e -> e.partOfSpeech() == word.partOfSpeech).toList();
        return samePart.stream()
                .filter(e -> e.headword().flatMap(h -> Serbian.withLatinAccents(word.plain, h))
                        .filter(word.headword::equals).isPresent())
                .findFirst()
                .or(() -> samePart.stream().findFirst());
    }

    private static void compare(OurWord word, WiktionaryEntry entry, Counters c, boolean detailed) {
        if (detailed) {
            System.out.printf("%n%s (%s) — викисловарь: %s%n", word.headword, word.partOfSpeech.title(),
                    entry.headword().orElse("без ударения"));
        }
        Map<String, List<Form>> byKey = new HashMap<>();
        for (Form form : entry.forms()) {
            byKey.computeIfAbsent(form.grammar() + " " + form.value().toLowerCase(), k -> new ArrayList<>()).add(form);
        }
        c.wiktionaryForms[word.partOfSpeech.ordinal()] += entry.forms().size();
        Set<String> ourKeys = new java.util.HashSet<>();
        Set<String> ourLetters = new java.util.HashSet<>();
        for (OurForm ours : word.forms) {
            String letters = Serbian.toLatin(ours.plain).toLowerCase();
            ourKeys.add(ours.grammar + " " + letters);
            ourLetters.add(letters);
        }
        for (Form form : entry.forms()) {
            if (form.accented().isPresent() && !ourKeys.contains(form.grammar() + " " + form.value().toLowerCase())) {
                c.missing[ourLetters.contains(form.value().toLowerCase()) ? 0 : 1]++;
            }
        }
        for (OurForm ours : word.forms) {
            List<Form> theirs = byKey.getOrDefault(ours.grammar + " " + Serbian.toLatin(ours.plain).toLowerCase(),
                    List.of());
            if (ours.grammar.equals("nom.sg") && theirs.isEmpty() && entry.headword().isPresent()) {
                theirs = List.of(new Form("nom.sg", ours.plain, entry.headword()));
            }
            List<String> accented = theirs.stream().flatMap(f -> f.accented().stream())
                    .flatMap(a -> Serbian.withLatinAccents(ours.plain, a).stream()).distinct().toList();
            Outcome outcome = outcome(word, ours, theirs, accented);
            String kind = ours.grammar.equals("nom.sg") ? "заглавная" : "словоформа";
            c.outcomes.merge(word.partOfSpeech + " " + kind + " " + outcome, 1, Integer::sum);
            if (detailed) {
                System.out.printf("  %-10s %-16s наше: %-16s викисловарь: %-24s %s%n", ours.grammar, ours.plain,
                        Optional.ofNullable(ours.form).filter(f -> ours.accentSource != null).orElse("—"),
                        accented.isEmpty() ? "—" : String.join(" / ", accented), outcome.title);
            }
            if (outcome == Outcome.DISAGREE || outcome == Outcome.TILDE_DISAGREE || outcome == Outcome.TILDE_AGREE) {
                c.sample(outcome, String.format("%s %s: наше %s, викисловарь %s", word.headword, ours.grammar,
                        outcome == Outcome.DISAGREE ? ours.form : stemAccented(word, ours).orElse("?"),
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
        TILDE_AGREE("тильда: ударение основы подтвердилось"),
        TILDE_DISAGREE("тильда: ударение основы не подтвердилось"),
        TILDE_UNKNOWN("тильда: тон основы вне общей части");

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
        if (ours.accentSource != null) {
            return accented.stream().anyMatch(a -> sameTone(a, ours.form)) ? Outcome.AGREE : Outcome.DISAGREE;
        }
        if (!ours.source.equals("SOURCE_DICTIONARY")) {
            return Outcome.GAIN_RULES;
        }
        Optional<String> predicted = stemAccented(word, ours);
        if (predicted.isEmpty()) {
            return Outcome.TILDE_UNKNOWN;
        }
        return accented.stream().anyMatch(a -> sameTone(a, predicted.get()))
                ? Outcome.TILDE_AGREE : Outcome.TILDE_DISAGREE;
    }

    /**
     * Гипотеза о тильде: форма читается с ударением основы — знаки заглавного слова над общим
     * с формой началом. Пусто, если тон заглавного слова стоит дальше общего начала.
     */
    private static Optional<String> stemAccented(OurWord word, OurForm ours) {
        String headword = word.headword;
        StringBuilder result = new StringBuilder();
        int letter = 0;
        boolean toneCopied = false;
        for (int i = 0; i < headword.length() && letter < ours.plain.length(); i++) {
            char ch = headword.charAt(i);
            if (Accent.fromCombining(ch).isPresent()) {
                result.append(ch);
                toneCopied |= Accent.fromCombining(ch).get().isTone();
                continue;
            }
            if (Character.toLowerCase(ch) != Character.toLowerCase(ours.plain.charAt(letter))) {
                break;
            }
            result.append(ours.plain.charAt(letter++));
        }
        return toneCopied ? Optional.of(result + ours.plain.substring(letter)) : Optional.empty();
    }

    /** Совпадение тона: тот же знак над той же буквой; долгота не сравнивается. */
    private static boolean sameTone(String a, String b) {
        return tone(a).equals(tone(b));
    }

    private static String tone(String rendered) {
        StringBuilder tones = new StringBuilder();
        int letter = 0;
        for (char ch : rendered.toCharArray()) {
            Optional<Accent> accent = Accent.fromCombining(ch);
            if (accent.isEmpty()) {
                letter++;
            } else if (accent.get().isTone()) {
                tones.append(letter).append(ch);
            }
        }
        return tones.toString();
    }

    private static double percent(int part, int whole) {
        return whole == 0 ? 0 : 100.0 * part / whole;
    }

    private static final class Counters {
        final Map<PartOfSpeech, int[]> words = new TreeMap<>();
        final int[] wiktionaryForms = new int[PartOfSpeech.values().length];
        final int[] missing = new int[2];
        final Map<String, Integer> outcomes = new TreeMap<>();
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
            words.forEach((part, c) -> System.out.printf("  %-14s %,7d из %,7d  %4.1f%%%n",
                    part.title(), c[1], c[0], percent(c[1], c[0])));
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
