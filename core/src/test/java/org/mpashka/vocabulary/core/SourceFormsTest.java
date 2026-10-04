package org.mpashka.vocabulary.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Формы, выписанные в статье. Разметка подлинная, из исходной базы. */
class SourceFormsTest {

    private static List<Form> formsOf(String markup) {
        var chunks = MarkupParser.parse(markup);
        var entry = EntryParser.parse("", "", markup);
        return SourceForms.inArticle(entry, Gender.fromMarks(entry.grammar()), chunks);
    }

    @Nested
    class Nouns {

        @Test
        @DisplayName("родительный падеж выписан целиком — ударение своё")
        void genitiveWrittenInFull() {
            assertThat(formsOf("аба‛жу_р$C#,#абажу’ра$C#м$#.#абажу’р$RV#.#"))
                    .containsExactly(new Form("gen.sg", "абажура",
                            Optional.of("абажу́ра")));
        }

        @Test
        @DisplayName("тильда вместо основы: буквы из словаря, ударения нет")
        void tildeKeepsLettersOnly() {
            assertThat(formsOf("бе‛збедн||о_ст$C#,#~ости$C#ж$#.#безопа’сность$RV#.#"))
                    .containsExactly(new Form("gen.sg", "безбедности"));
        }

        @Test
        @DisplayName("тильда, но тон напечатан в видимой части — ударение своё")
        void tildeWithPrintedTone() {
            assertThat(formsOf("пи“п||авац$C#,#~а’вца$C#м$#.#копу’ша$RV#.#"))
                    .containsExactly(new Form("gen.sg", "пипавца",
                            Optional.of("пипа́вца")));
        }

        @Test
        @DisplayName("повтор заглавного слова — это вариант ударения, а не форма")
        void accentVariantIsNotAForm() {
            assertThat(formsOf("го‛ра$C#ж$#.#,#го“ра$C#ж$#.#гора’$RV#.#")).isEmpty();
        }

        @Test
        @DisplayName("многословное название: второй фрагмент — второе слово, не форма")
        void secondWordOfNameIsNotAForm() {
            assertThat(formsOf(
                    "Аралско Је“зеро$C#(#а‛ралско_$C#)#с$#.#Ара’льское$RV#мо’ре$RV#.#"))
                    .isEmpty();
        }

        @Test
        @DisplayName("слово только во множественном числе: помета у формы своя")
        void pluralOnlyGetsItsOwnGrammar() {
            assertThat(formsOf("би‛саге$C#,#би“са_га_$C#ж$#.#мн$#.#перемётные$RV#су’мки$RV#.#"))
                    .containsExactly(new Form("gen.pl", "бисага",
                            Optional.of("би̏са̄га̄")));
        }
    }

    @Nested
    class Verbs {

        @Test
        @DisplayName("настоящее время выписано целиком")
        void presentWrittenInFull() {
            assertThat(formsOf("ра’дити$C#,#ра^ди_м$C#рабо’тать$RV#.#"))
                    .containsExactly(new Form("praes.1sg", "радим",
                            Optional.of("ра̑дӣм")));
        }

        @Test
        @DisplayName("сокращённая запись: буквы начала берутся из инфинитива")
        void abbreviatedTakesLettersFromInfinitive() {
            // Тона в напечатанном хвосте нет — только долгота, поэтому и ударения нет.
            assertThat(formsOf("а‛ванзовати$C#,#-#зује_м$C#продвига’ться$RV#.#"))
                    .containsExactly(new Form("praes.1sg", "аванзујем"));
        }

        @Test
        @DisplayName("сокращённая запись с тоном в хвосте: ударение формы своё")
        void abbreviatedKeepsPrintedTone() {
            assertThat(formsOf("акцентова‛ти$C#,#-#ту’јем$C#акценти’ровать$RV#.#"))
                    .containsExactly(new Form("praes.1sg", "акцентујем",
                            Optional.of("акценту́јем")));
        }
    }

    @Nested
    class Adjectives {

        @Test
        @DisplayName("формы родов берутся готовыми, тильда раскрывается")
        void genderFormsFromEntry() {
            assertThat(formsOf("а‛псурд||ан$C#,#~ни_$C#,#~на$C#,#~но$C#абсу’рдный$RV#.#"))
                    .containsExactly(
                            new Form("adj", "апсурдни"),
                            new Form("adj", "апсурдна"),
                            new Form("adj", "апсурдно"));
        }
    }

    @Test
    @DisplayName("у прочих частей речи выписанных форм не бывает")
    void otherPartsOfSpeechHaveNoForms() {
        assertThat(formsOf("за$C#предл$#.#за$RV#.#")).isEmpty();
    }

    @Nested
    class StemTone {

        private static String nfd(String value) {
            return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD);
        }

        @Test
        @DisplayName("тон основы переходит на форму, долгота — нет: в форме она меняется")
        void toneWithoutLength() {
            assertThat(SourceForms.stemTone(nfd("бо̏ле̄ст"), "болести")).contains(nfd("бо̏лести"));
            assertThat(SourceForms.stemTone(nfd("бѐзбедно̄ст"), "безбедности")).contains(nfd("бѐзбедности"));
        }

        @Test
        @DisplayName("беглое «а» за тоном не мешает: тон стоит в общем начале")
        void fleetingA() {
            assertThat(SourceForms.stemTone(nfd("ба̏лавац"), "балавца")).contains(nfd("ба̏лавца"));
        }

        @Test
        @DisplayName("на несверенной помете тон основы не выводится: форм родов викисловарь не даёт")
        void onlyCheckedGrammar() {
            assertThat(SourceForms.stemTone(nfd("бо̏ле̄ст"), new Form("gen.sg", "болести"))).isPresent();
            assertThat(SourceForms.stemTone(nfd("абеце̏дан"), new Form("adj", "абецедна"))).isEmpty();
        }

        @Test
        @DisplayName("буквы разошлись раньше тона — тона нет")
        void toneOutsideCommonStart() {
            assertThat(SourceForms.stemTone(nfd("човѐк"), "људи")).isEmpty();
            assertThat(SourceForms.stemTone("за", "за")).isEmpty();
        }
    }
}
