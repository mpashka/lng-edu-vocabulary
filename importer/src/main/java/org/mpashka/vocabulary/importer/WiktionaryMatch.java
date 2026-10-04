package org.mpashka.vocabulary.importer;

import org.mpashka.vocabulary.core.Accent;
import org.mpashka.vocabulary.core.Form;
import org.mpashka.vocabulary.core.PartOfSpeech;
import org.mpashka.vocabulary.core.Serbian;
import org.mpashka.vocabulary.core.WiktionaryEntry;
import org.mpashka.vocabulary.core.WiktionaryParser;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

/**
 * Сопоставление слов и форм нашей базы со словами викисловаря — общее у отчёта
 * {@link WiktionaryAccentReport} и записи {@link WiktionaryAccents}, чтобы отчёт мерил ровно
 * то, что потом записывается.
 */
// @tag:wiktionary
final class WiktionaryMatch {

    static final Path EXTRACT = Path.of(".data/wiktionary/sh-pages.xml.gz");

    record OurForm(long id, String form, String plain, String grammar, String source, String accentSource) {

        String latinKey() {
            return grammar + " " + Serbian.toLatin(plain).toLowerCase();
        }
    }

    record OurWord(long id, String headword, String plain, PartOfSpeech partOfSpeech, List<OurForm> forms) {

        String title() {
            return Serbian.toLatin(plain);
        }
    }

    private WiktionaryMatch() {
    }

    /** Слова выписки по заголовку статьи в нижнем регистре. */
    static Map<String, List<WiktionaryEntry>> readExtract() throws IOException, XMLStreamException {
        Map<String, List<WiktionaryEntry>> byTitle = new HashMap<>();
        WiktionaryPages.forEachArticle(EXTRACT, page -> {
            List<WiktionaryEntry> entries = WiktionaryParser.serboCroatian(page.title(), page.text());
            if (!entries.isEmpty()) {
                byTitle.computeIfAbsent(page.title().toLowerCase(), t -> new ArrayList<>()).addAll(entries);
            }
        });
        return byTitle;
    }

    /**
     * Слова с формами, кроме латинских, — такими, какими они были до викисловаря: его строки и
     * ударения не читаются, иначе сверка сравнивала бы его сам с собой. Варианты ударения
     * заглавного слова сравниваются как заглавная форма.
     */
    static List<OurWord> readOurWords(Connection pg) throws SQLException {
        Map<Long, OurWord> words = new HashMap<>();
        List<OurWord> ordered = new ArrayList<>();
        try (Statement st = pg.createStatement()) {
            try (ResultSet rs = st.executeQuery(
                    "select id, headword, headword_plain, part_of_speech from word order by id")) {
                while (rs.next()) {
                    OurWord word = new OurWord(rs.getLong(1), rs.getString(2), rs.getString(3),
                            PartOfSpeech.valueOf(rs.getString(4)), new ArrayList<>());
                    words.put(word.id, word);
                    ordered.add(word);
                }
            }
            try (ResultSet rs = st.executeQuery("""
                    select id, word_id, case when accent_source = 'WIKTIONARY' then null else form end,
                           form_plain, grammar, source, nullif(accent_source, 'WIKTIONARY')
                      from word_form
                     where grammar <> 'nom.sg.lat' and source <> 'WIKTIONARY'
                     order by id""")) {
                while (rs.next()) {
                    words.get(rs.getLong(2)).forms.add(new OurForm(rs.getLong(1), rs.getString(3),
                            rs.getString(4), rs.getString(5).replace(".вариант", ""), rs.getString(6),
                            rs.getString(7)));
                }
            }
        }
        return ordered;
    }

    /**
     * Слово той же части речи; у омонимов — то, чьё заглавное ударение совпало с нашим.
     * Статья с тем же регистром заголовка важнее: {@code grad} и {@code Grad} — разные слова.
     */
    static Optional<WiktionaryEntry> matching(OurWord word, Map<String, List<WiktionaryEntry>> wiktionary) {
        List<WiktionaryEntry> samePart = wiktionary.getOrDefault(word.title().toLowerCase(), List.of()).stream()
                .filter(e -> e.partOfSpeech() == word.partOfSpeech)
                .sorted(java.util.Comparator.comparing(e -> !e.title().equals(word.title())))
                .toList();
        return samePart.stream()
                .filter(e -> e.headword().flatMap(h -> Serbian.withLatinAccents(word.plain, h))
                        .filter(word.headword::equals).isPresent())
                .findFirst()
                .or(() -> samePart.stream().findFirst());
    }

    /**
     * Формы викисловаря по помете и буквам. Заглавное слово статьи добавляется к именительному:
     * таблица часто без тона ({@code Afrika}), а заглавный шаблон — с ним ({@code Àfrika}).
     */
    static Map<String, List<Form>> formsByKey(WiktionaryEntry entry) {
        Map<String, List<Form>> byKey = new HashMap<>();
        for (Form form : entry.forms()) {
            byKey.computeIfAbsent(form.grammar() + " " + form.value().toLowerCase(), k -> new ArrayList<>()).add(form);
        }
        entry.headword().ifPresent(headword -> byKey
                .computeIfAbsent("nom.sg " + entry.title().toLowerCase(), k -> new ArrayList<>())
                .add(new Form("nom.sg", entry.title(), Optional.of(headword))));
        return byKey;
    }

    /** Ударения викисловаря, положенные на наши кириллические буквы, без повторов. */
    static List<String> accentsOn(String cyrillicPlain, List<Form> theirs) {
        return theirs.stream().flatMap(f -> f.accented().stream())
                .flatMap(a -> Serbian.withLatinAccents(cyrillicPlain, a).stream()).distinct().toList();
    }

    /** Насколько мы уверены, что слово викисловаря — то же, что наше. */
    enum Identity {
        /** Заглавное ударение совпало либо это имя собственное: формы можно брать. */
        SAME("то же слово"),
        /** Заглавное ударение разошлось: другое слово или разночтение — формы не берутся. */
        HEADWORD_DIFFERS("заглавное ударение разошлось"),
        /**
         * Сверить не по чему: у нас или у викисловаря заглавное слово без тона. Совпадения
         * букв и части речи мало — {@code бег} «бегство» нашлось бы как {@code bȇg} «бей».
         */
        UNANCHORED("сверить не по чему");

        final String title;

        Identity(String title) {
            this.title = title;
        }
    }

    static Identity identity(OurWord word, WiktionaryEntry entry) {
        Optional<OurForm> nominative = word.forms().stream().filter(f -> f.grammar().equals("nom.sg")).findFirst();
        if (nominative.isEmpty()) {
            return Identity.UNANCHORED;
        }
        List<OurForm> group = group(word, nominative.get());
        List<String> accented = accentsOn(nominative.get().plain(),
                formsByKey(entry).getOrDefault(nominative.get().latinKey(), List.of()));
        if (group.stream().allMatch(f -> f.accentSource() == null)) {
            return Character.isUpperCase(word.headword().charAt(0)) && !accented.isEmpty()
                    ? Identity.SAME : Identity.UNANCHORED;
        }
        if (accented.isEmpty()) {
            return Identity.UNANCHORED;
        }
        return groupAgrees(group, accented) ? Identity.SAME : Identity.HEADWORD_DIFFERS;
    }

    /** Наши формы с той же пометой и теми же буквами: заглавная форма и её варианты ударения. */
    static List<OurForm> group(OurWord word, OurForm ours) {
        return word.forms().stream().filter(f -> f.latinKey().equals(ours.latinKey())).toList();
    }

    /**
     * Наше ударение совпало, если хоть одна форма группы совпала хоть с одним вариантом
     * викисловаря: у {@code го̀ра / го̏ра} второй вариант — не расхождение.
     */
    static boolean groupAgrees(List<OurForm> group, List<String> accented) {
        return group.stream().filter(f -> f.accentSource() != null)
                .anyMatch(f -> accented.stream().anyMatch(a -> sameTone(a, f.form())));
    }

    /** Совпадение тона: тот же знак над той же буквой; долгота не сравнивается. */
    static boolean sameTone(String a, String b) {
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

    static String url(WiktionaryEntry entry) {
        return "https://en.wiktionary.org/wiki/"
                + URLEncoder.encode(entry.title().replace(' ', '_'), StandardCharsets.UTF_8)
                + "#Serbo-Croatian";
    }
}
