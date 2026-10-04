package org.mpashka.vocabulary.importer;

import org.mpashka.vocabulary.core.AdjectiveDeclension;
import org.mpashka.vocabulary.core.Chunk;
import org.mpashka.vocabulary.core.EntryParser;
import org.mpashka.vocabulary.core.Form;
import org.mpashka.vocabulary.core.MarkupParser;
import org.mpashka.vocabulary.core.PartOfSpeech;
import org.mpashka.vocabulary.core.PartOfSpeechRules;
import org.mpashka.vocabulary.core.SourceForms;

import java.util.ArrayList;
import java.util.List;

/**
 * Отчёт по формам прилагательных (этап 3 плана).
 *
 * <p>Смысл отчёта не в том, чтобы выводить формы правилами — в исходной базе они уже
 * выписаны. Отчёт показывает, <b>сколько форм есть готовыми</b> и насколько правила
 * способны их восполнить там, где словарь их опустил.
 *
 * <p>Запуск: {@code ./gradlew :importer:runAdjectiveForms}
 */
// @tag:word-forms @tag:import
public final class AdjectiveFormsReport {

    public static void main(String[] args) {
        String path = args.length > 0 ? args[0] : SourceReader.DEFAULT_PATH;

        int adjectives = 0;
        int withForms = 0;
        int covered = 0;
        long formsTotal = 0;
        long accented = 0;
        long generatedTotal = 0;
        List<String> gaps = new ArrayList<>();

        List<SourceReader.Row> rows = new ArrayList<>(46_000);
        new SourceReader(path).forEach(rows::add);

        for (var row : rows) {
            List<Chunk> chunks = MarkupParser.parse(row.xml());
            if (chunks.isEmpty() || PartOfSpeechRules.detectByStructure(chunks) != PartOfSpeech.ADJECTIVE) {
                continue;
            }
            adjectives++;
            var entry = EntryParser.parse(row.name(), row.kw(), row.xml());
            List<Form> written = SourceForms.inArticle(entry, null, chunks);
            if (written.isEmpty()) {
                continue;
            }
            List<String> listed = written.stream().map(Form::value).toList();
            withForms++;
            formsTotal += listed.size();
            accented += written.stream().filter(form -> form.accented().isPresent()).count();

            List<String> generated = AdjectiveDeclension.genderForms(chunks.getFirst().text());
            generatedTotal += generated.size();
            boolean all = generated.containsAll(listed);
            if (all) {
                covered++;
            } else if (gaps.size() < 12) {
                gaps.add("  %-20s в словаре %s%n                       правила  %s"
                        .formatted(row.name(), listed, generated));
            }
        }

        System.out.println("=== Прилагательные: формы родов ===");
        System.out.printf("Прилагательных (по строению статьи): %d%n", adjectives);
        System.out.printf("Из них формы родов выписаны:         %d (%.1f%%)%n",
                withForms, 100.0 * withForms / Math.max(adjectives, 1));
        System.out.printf("Готовых форм в словаре:              %d%n", formsTotal);
        System.out.printf("Из них с ударением:                  %d%n%n", accented);
        System.out.printf("Правила воспроизводят все формы:     %d из %d (%.1f%%)%n",
                covered, withForms, 100.0 * covered / Math.max(withForms, 1));
        System.out.printf("В среднем порождается вариантов:     %.2f%n",
                withForms == 0 ? 0 : (double) generatedTotal / withForms);

        System.out.println("\n--- Где правила не дотягивают ---");
        gaps.forEach(System.out::println);
    }

    private AdjectiveFormsReport() {
    }
}
