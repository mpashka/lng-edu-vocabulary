package org.mpashka.vocabulary.importer;

import org.mpashka.vocabulary.core.Serbian;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Записывает заглавным формам без ударения ударение из Hrvatski jezični portal — по выверенному
 * списку {@code hjp-accents.tsv}: статью HJP выбирает человек или агент по толкованию, код только
 * кладёт её знаки на наши буквы. Наше ударение не затирается: несогласие уходит в
 * {@code discrepancy}.
 *
 * <p>Запуск после викисловаря: {@code ./gradlew :importer:wiktionaryAccents :importer:hjpAccents}.
 * Повторный запуск сначала снимает записанное прошлым.
 */
// @tag:hjp @tag:accent
public final class HjpAccents {

    private static final String LIST = "/hjp-accents.tsv";

    private HjpAccents() {
    }

    public static void main(String[] args) throws IOException, SQLException {
        List<Entry> entries = readList();
        Map<String, List<String>> outcomes = new TreeMap<>();
        try (Connection pg = TargetDatabase.connect(null)) {
            pg.setAutoCommit(false);
            removePrevious(pg);
            try (PreparedStatement select = pg.prepareStatement("""
                         select f.id, f.word_id, f.form, f.form_plain, f.accent_source
                           from word_form f join word w on w.id = f.word_id
                          where w.headword_plain = ? and f.grammar = 'nom.sg'""");
                 PreparedStatement update = pg.prepareStatement(
                         "update word_form set form = ?, accent_source = 'HJP', accent_url = ? where id = ?");
                 PreparedStatement discrepancy = pg.prepareStatement("""
                         insert into discrepancy (word_id, field, current_value, proposed_value,
                             current_source, proposed_source, proposed_url)
                         values (?, 'accent.nom.sg', ?, ?, cast(? as data_source), 'HJP', ?)""")) {
                for (Entry entry : entries) {
                    select.setString(1, entry.headword());
                    boolean found = false;
                    try (ResultSet rs = select.executeQuery()) {
                        while (rs.next()) {
                            found = true;
                            String outcome = apply(entry, rs, update, discrepancy);
                            outcomes.computeIfAbsent(outcome, o -> new ArrayList<>()).add(entry.headword());
                        }
                    }
                    if (!found) {
                        outcomes.computeIfAbsent("слова нет в базе", o -> new ArrayList<>()).add(entry.headword());
                    }
                }
            }
            pg.commit();
        }
        outcomes.forEach((outcome, words) ->
                System.out.printf("%-40s %3d  %s%n", outcome, words.size(), String.join(", ", words)));
    }

    private static String apply(Entry entry, ResultSet form, PreparedStatement update,
                                PreparedStatement discrepancy) throws SQLException {
        Optional<String> accented = Serbian.withLatinAccents(form.getString(4), entry.accented());
        if (accented.isEmpty()) {
            return "буквы HJP не совпали с нашими";
        }
        String accentSource = form.getString(5);
        if (accentSource == null) {
            update.setString(1, accented.get());
            update.setString(2, entry.url());
            update.setLong(3, form.getLong(1));
            update.executeUpdate();
            return "записано";
        }
        if (WiktionaryMatch.sameTone(form.getString(3), accented.get())) {
            return "совпало с нашим ударением";
        }
        discrepancy.setLong(1, form.getLong(2));
        discrepancy.setString(2, form.getString(3));
        discrepancy.setString(3, accented.get());
        discrepancy.setString(4, accentSource);
        discrepancy.setString(5, entry.url());
        discrepancy.executeUpdate();
        return "расхождение — в discrepancy";
    }

    private static void removePrevious(Connection pg) throws SQLException {
        try (Statement st = pg.createStatement()) {
            st.executeUpdate("""
                    update word_form f
                       set form = (select headword from word w where w.id = f.word_id),
                           accent_source = null,
                           accent_url = null
                     where accent_source = 'HJP'""");
            st.executeUpdate("delete from discrepancy where proposed_source = 'HJP' and not resolved");
        }
    }

    private static List<Entry> readList() throws IOException {
        List<Entry> entries = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(HjpAccents.class.getResourceAsStream(LIST), LIST),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] columns = line.split("\t");
                if (columns.length < 3) {
                    throw new IllegalStateException(LIST + ": нужно хотя бы три столбца через табуляцию — " + line);
                }
                entries.add(new Entry(columns[0], columns[1], columns[2]));
            }
        }
        return entries;
    }

    private record Entry(String headword, String accented, String url) {
    }
}
