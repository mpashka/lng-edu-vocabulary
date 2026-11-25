package org.mpashka.vocabulary.importer;

import org.mpashka.vocabulary.core.RootKind;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Разбор выверенных файлов находок уроков. Формат описан в
 * {@code docs/implementation/sources.md}, раздел «Находки уроков»; код — его единственная
 * проверка, поэтому битая строка останавливает прогон с указанием файла, номера строки и
 * того, что именно не так.
 */
// @tag:lesson-findings
public final class LessonFindingsParser {

    /**
     * Источники, чьё ударение годится: у них назван тон, а не только место ударения.
     * Govori и подобные справочники места ударения в файл не идут.
     */
    private static final Set<String> ACCENT_SOURCES = Set.of("WIKTIONARY", "HJP");

    private LessonFindingsParser() {
    }

    /**
     * @param lemma    заглавное слово кириллицей без ударений — ключ {@code word.headword_plain}
     * @param form     словоформа кириллицей без ударений — ключ {@code word_form.form_plain}
     * @param grammar  помета формы ({@code nom.sg}, {@code praes.1sg}); пусто, когда урок её не знает
     * @param accented та же форма латиницей со знаками ударения, как в источнике
     */
    public record Accent(String lemma, String form, Optional<String> grammar, String accented,
                         String source, String url, String note) {
    }

    /**
     * @param value корень или родственное слово, как оно написано в источнике: {@code *mъlinъ}, {@code mill}
     * @param note  пояснение связи; у вида {@link RootKind#OTHER} называет язык
     */
    public record Root(String lemma, RootKind kind, String value, String note, String source, String url) {
    }

    public static List<Accent> readAccents(String name, Reader source) throws IOException {
        List<Accent> accents = new ArrayList<>();
        for (Row row : rows(name, source, 6)) {
            accents.add(new Accent(
                    row.required(1, "лемма кириллицей без ударений"),
                    row.required(2, "словоформа кириллицей без ударений"),
                    row.optional(3),
                    row.required(4, "форма латиницей со знаками ударения"),
                    row.oneOf(5, "источник", ACCENT_SOURCES),
                    row.required(6, "ссылка на статью источника"),
                    row.optional(7).orElse("")));
        }
        return accents;
    }

    public static List<Root> readRoots(String name, Reader source) throws IOException {
        List<Root> roots = new ArrayList<>();
        for (Row row : rows(name, source, 6)) {
            RootKind kind = row.kind(2);
            roots.add(new Root(
                    row.required(1, "лемма кириллицей без ударений"),
                    kind,
                    row.required(3, "корень или родственное слово"),
                    kind == RootKind.OTHER ? row.required(4, "язык связи вида OTHER") : row.optional(4).orElse(""),
                    row.oneOf(5, "источник", ACCENT_SOURCES),
                    row.required(6, "ссылка на статью источника")));
        }
        return roots;
    }

    private static List<Row> rows(String name, Reader source, int columns) throws IOException {
        List<Row> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(source)) {
            String line;
            int number = 0;
            while ((line = reader.readLine()) != null) {
                number++;
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] values = line.split("\t", -1);
                if (values.length < columns) {
                    throw new IllegalStateException("%s, строка %d: столбцов через табуляцию %d, нужно %d — %s"
                            .formatted(name, number, values.length, columns, line));
                }
                rows.add(new Row(name, number, values));
            }
        }
        return rows;
    }

    private record Row(String file, int number, String[] values) {

        String required(int column, String meaning) {
            return optional(column).orElseThrow(() -> new IllegalStateException(
                    "%s, строка %d: столбец %d пуст, а это %s".formatted(file, number, column, meaning)));
        }

        Optional<String> optional(int column) {
            if (column > values.length) {
                return Optional.empty();
            }
            String value = values[column - 1].trim();
            return value.isEmpty() ? Optional.empty() : Optional.of(value);
        }

        String oneOf(int column, String meaning, Set<String> allowed) {
            String value = required(column, meaning);
            if (!allowed.contains(value)) {
                throw new IllegalStateException("%s, строка %d: %s «%s» не из списка %s"
                        .formatted(file, number, meaning, value, allowed.stream().sorted().toList()));
            }
            return value;
        }

        RootKind kind(int column) {
            String value = required(column, "вид связи");
            return RootKind.find(value).orElseThrow(() -> new IllegalStateException(
                    "%s, строка %d: вид связи «%s» не из списка %s".formatted(file, number, value,
                            Arrays.stream(RootKind.values()).map(Enum::name).toList())));
        }
    }
}
