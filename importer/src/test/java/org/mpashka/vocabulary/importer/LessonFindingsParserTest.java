package org.mpashka.vocabulary.importer;

import org.junit.jupiter.api.Test;
import org.mpashka.vocabulary.core.RootKind;
import org.mpashka.vocabulary.importer.LessonFindingsParser.Accent;
import org.mpashka.vocabulary.importer.LessonFindingsParser.Root;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LessonFindingsParserTest {

    private static final String URL = "https://en.wiktionary.org/wiki/mlin#Serbo-Croatian";

    @Test
    void skipsCommentsAndBlankLines() throws IOException {
        List<Accent> accents = accents("""
                # лемма	форма	помета	ударение	источник	ссылка

                млин	млин	nom.sg	mlȉn	WIKTIONARY	%s	u mlin
                """.formatted(URL));

        assertThat(accents).containsExactly(
                new Accent("млин", "млин", Optional.of("nom.sg"), "mlȉn", "WIKTIONARY", URL, "u mlin"));
    }

    @Test
    void readsFormWithoutGrammarAndWithoutNote() throws IOException {
        List<Accent> accents = accents("млин	млина		mlȉna	HJP	" + URL);

        assertThat(accents).containsExactly(
                new Accent("млин", "млина", Optional.empty(), "mlȉna", "HJP", URL, ""));
    }

    @Test
    void refusesRowWithMissingColumns() {
        assertThatThrownBy(() -> accents("млин	млин	nom.sg	mlȉn	WIKTIONARY"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("строка 1")
                .hasMessageContaining("столбцов через табуляцию 5, нужно 6");
    }

    @Test
    void refusesRowWithoutUrl() {
        assertThatThrownBy(() -> accents("млин	млин	nom.sg	mlȉn	WIKTIONARY	"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("столбец 6 пуст")
                .hasMessageContaining("ссылка на статью источника");
    }

    @Test
    void refusesAccentSourceThatGivesOnlyPlaceOfStress() {
        assertThatThrownBy(() -> accents("млин	млин	nom.sg	mlȉn	GOVORI	" + URL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("источник «GOVORI» не из списка [HJP, WIKTIONARY]");
    }

    @Test
    void countsLineNumbersIncludingSkippedOnes() {
        assertThatThrownBy(() -> accents("""
                # столбцы: лемма, форма, помета, ударение, источник, ссылка
                млин	млин	nom.sg	mlȉn	WIKTIONARY	%s
                млин		nom.sg	mlȉn	WIKTIONARY	%s
                """.formatted(URL, URL)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("строка 3");
    }

    @Test
    void readsRootKindAsEnum() throws IOException {
        List<Root> roots = roots("""
                млин	PROTO_SLAVIC	*mъlinъ	через древневерхненемецкий из позднелатинского molīna	WIKTIONARY	%s
                млин	ENGLISH	mill	из того же позднелатинского molīna	WIKTIONARY	%s
                """.formatted(URL, URL));

        assertThat(roots).extracting(Root::kind).containsExactly(RootKind.PROTO_SLAVIC, RootKind.ENGLISH);
        assertThat(roots.getFirst().value()).isEqualTo("*mъlinъ");
    }

    @Test
    void readsRootWithoutNote() throws IOException {
        List<Root> roots = roots("млин	RUSSIAN	млин		WIKTIONARY	" + URL);

        assertThat(roots).containsExactly(
                new Root("млин", RootKind.RUSSIAN, "млин", "", "WIKTIONARY", URL));
    }

    @Test
    void refusesUnknownRootKind() {
        assertThatThrownBy(() -> roots("млин	TURKISH	kapı	из турецкого	WIKTIONARY	" + URL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("вид связи «TURKISH»")
                .hasMessageContaining("OTHER");
    }

    private static List<Accent> accents(String content) throws IOException {
        return LessonFindingsParser.readAccents("lesson-accents.tsv", new StringReader(content));
    }

    private static List<Root> roots(String content) throws IOException {
        return LessonFindingsParser.readRoots("lesson-roots.tsv", new StringReader(content));
    }
}
