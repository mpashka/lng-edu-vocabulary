package org.mpashka.vocabulary.importer;

import org.mpashka.vocabulary.core.Serbian;
import org.mpashka.vocabulary.importer.LessonFindingsParser.Accent;
import org.mpashka.vocabulary.importer.LessonFindingsParser.Root;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Принимает находки уроков сербского: ударения словоформ — в {@code word_form}, связи слова с
 * другими языками — в {@code word_root}. Источником остаётся тот, кто ударение подтвердил
 * (викисловарь или HJP): урок — канал, а не источник. Наше ударение не затирается — несогласие
 * уходит в {@code discrepancy}; формы, которой нет в парадигме слова, — в отчёт.
 *
 * <p>Запуск после прочих источников:
 * {@code ./gradlew :importer:wiktionaryAccents :importer:hjpAccents :importer:lessonFindings}.
 * Повторный прогон ничего не плодит: своя же прошлая запись узнаётся по той же ссылке.
 */
// @tag:lesson-findings @tag:accent @tag:word-roots
public final class LessonFindings {

    private static final String ACCENTS = "/lesson-accents.tsv";
    private static final String ROOTS = "/lesson-roots.tsv";

    private LessonFindings() {
    }

    public static void main(String[] args) throws IOException, SQLException {
        List<Accent> accents = LessonFindingsParser.readAccents(ACCENTS, resource(ACCENTS));
        List<Root> roots = LessonFindingsParser.readRoots(ROOTS, resource(ROOTS));
        Report accentReport = new Report();
        Report rootReport = new Report();
        try (Connection pg = TargetDatabase.connect(null)) {
            pg.setAutoCommit(false);
            try (Writers w = new Writers(pg)) {
                for (Accent accent : accents) {
                    applyAccent(accent, w, accentReport);
                }
                for (Root root : roots) {
                    applyRoot(root, w, rootReport);
                }
            }
            pg.commit();
        }
        System.out.println("Ударения словоформ:");
        accentReport.print();
        System.out.println("Связи с другими языками:");
        rootReport.print();
    }

    private static void applyAccent(Accent accent, Writers w, Report report) throws SQLException {
        String what = accent.lemma() + "/" + accent.form();
        List<Long> words = w.wordsByHeadword(accent.lemma());
        if (words.isEmpty()) {
            report.add("слова нет в базе", what);
            return;
        }
        List<FormRow> matched = new ArrayList<>();
        for (long word : words) {
            matched.addAll(w.formsByLetters(word, accent.form()));
        }
        if (matched.isEmpty()) {
            report.add("формы нет в парадигме слова", what);
            return;
        }
        Optional<String> accented = Serbian.withLatinAccents(accent.form(), accent.accented());
        if (accented.isEmpty()) {
            report.add("знаки источника не легли на наши буквы", what);
            return;
        }
        List<FormRow> target = accent.grammar()
                .map(grammar -> matched.stream().filter(f -> grammar.equals(f.grammar())).toList())
                .orElse(matched);
        if (target.isEmpty()) {
            report.add("помета не совпала с парадигмой", what);
            return;
        }
        if (accent.grammar().isEmpty() && target.stream().map(FormRow::grammar).distinct().count() > 1) {
            report.add("буквы под несколькими пометами — нужна помета", what);
            return;
        }
        for (FormRow form : target) {
            report.add(applyTo(form, accent, accented.get(), w), what);
        }
    }

    /** Своя же прошлая запись узнаётся по той же ссылке и тому же источнику — её можно перезаписать. */
    private static String applyTo(FormRow form, Accent accent, String accented, Writers w) throws SQLException {
        boolean oursAlready = accent.source().equals(form.accentSource()) && accent.url().equals(form.accentUrl());
        if (form.accentSource() == null || oursAlready) {
            w.setAccent(form.id(), accented, accent.source(), accent.url());
            return "записано";
        }
        if (WiktionaryMatch.sameTone(form.form(), accented)) {
            return "совпало с нашим ударением";
        }
        String field = form.grammar() == null ? "accent" : "accent." + form.grammar();
        w.addDiscrepancy(form.wordId(), field, form.form(), accented, form.accentSource(), accent.source(),
                accent.url());
        return "расхождение — в discrepancy";
    }

    // Связь пишется каждому омониму леммы: какой из них имеется в виду, файл не говорит.
    private static void applyRoot(Root root, Writers w, Report report) throws SQLException {
        String what = root.lemma() + "/" + root.value();
        List<Long> words = w.wordsByHeadword(root.lemma());
        if (words.isEmpty()) {
            report.add("слова нет в базе", what);
            return;
        }
        for (long word : words) {
            report.add(w.setRoot(word, root) ? "записано" : "связь уже есть с другой ссылкой — не тронута", what);
        }
    }

    private static InputStreamReader resource(String name) {
        InputStream stream = Objects.requireNonNull(LessonFindings.class.getResourceAsStream(name), name);
        return new InputStreamReader(stream, StandardCharsets.UTF_8);
    }

    private record FormRow(long id, long wordId, String form, String grammar, String accentSource,
                           String accentUrl) {
    }

    private static final class Report {

        private final Map<String, List<String>> byOutcome = new TreeMap<>();

        void add(String outcome, String what) {
            byOutcome.computeIfAbsent(outcome, o -> new ArrayList<>()).add(what);
        }

        void print() {
            byOutcome.forEach((outcome, items) ->
                    System.out.printf("  %-38s %3d  %s%n", outcome, items.size(), String.join(", ", items)));
        }
    }

    private static final class Writers implements AutoCloseable {

        private final PreparedStatement word;
        private final PreparedStatement forms;
        private final PreparedStatement accent;
        private final PreparedStatement discrepancy;
        private final PreparedStatement root;

        Writers(Connection pg) throws SQLException {
            word = pg.prepareStatement("select id from word where headword_plain = ?");
            forms = pg.prepareStatement("""
                    select id, word_id, form, grammar, accent_source, accent_url
                      from word_form
                     where word_id = ? and form_plain = ? and coalesce(grammar, '') not like '%.lat'""");
            accent = pg.prepareStatement("update word_form set form = ?, accent_source = cast(? as data_source), "
                    + "accent_url = ? where id = ?");
            discrepancy = pg.prepareStatement("""
                    insert into discrepancy (word_id, field, current_value, proposed_value,
                        current_source, proposed_source, proposed_url)
                    select ?, ?, ?, ?, cast(? as data_source), cast(? as data_source), ?
                     where not exists (select 1 from discrepancy d
                                        where d.word_id = ? and d.field = ? and d.current_value = ?
                                          and d.proposed_value = ?)""");
            root = pg.prepareStatement("""
                    insert into word_root (word_id, kind, value, note, source, source_url)
                    values (?, cast(? as root_kind), ?, ?, cast(? as data_source), ?)
                    on conflict (word_id, kind, value)
                        do update set note = excluded.note, source = excluded.source
                        where word_root.source_url = excluded.source_url""");
        }

        List<Long> wordsByHeadword(String headword) throws SQLException {
            word.setString(1, headword);
            List<Long> ids = new ArrayList<>();
            try (ResultSet rs = word.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
            }
            return ids;
        }

        List<FormRow> formsByLetters(long wordId, String letters) throws SQLException {
            forms.setLong(1, wordId);
            forms.setString(2, letters);
            List<FormRow> rows = new ArrayList<>();
            try (ResultSet rs = forms.executeQuery()) {
                while (rs.next()) {
                    rows.add(new FormRow(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                            rs.getString(5), rs.getString(6)));
                }
            }
            return rows;
        }

        void setAccent(long formId, String accented, String source, String url) throws SQLException {
            accent.setString(1, accented);
            accent.setString(2, source);
            accent.setString(3, url);
            accent.setLong(4, formId);
            accent.executeUpdate();
        }

        void addDiscrepancy(long wordId, String field, String current, String proposed, String currentSource,
                            String proposedSource, String url) throws SQLException {
            discrepancy.setLong(1, wordId);
            discrepancy.setString(2, field);
            discrepancy.setString(3, current);
            discrepancy.setString(4, proposed);
            discrepancy.setString(5, currentSource);
            discrepancy.setString(6, proposedSource);
            discrepancy.setString(7, url);
            discrepancy.setLong(8, wordId);
            discrepancy.setString(9, field);
            discrepancy.setString(10, current);
            discrepancy.setString(11, proposed);
            discrepancy.executeUpdate();
        }

        boolean setRoot(long wordId, Root value) throws SQLException {
            root.setLong(1, wordId);
            root.setString(2, value.kind().name());
            root.setString(3, value.value());
            root.setString(4, value.note().isEmpty() ? null : value.note());
            root.setString(5, value.source());
            root.setString(6, value.url());
            return root.executeUpdate() > 0;
        }

        @Override
        public void close() throws SQLException {
            for (PreparedStatement st : List.of(word, forms, accent, discrepancy, root)) {
                st.close();
            }
        }
    }
}
