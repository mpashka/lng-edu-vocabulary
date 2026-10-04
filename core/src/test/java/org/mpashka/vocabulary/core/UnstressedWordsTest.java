package org.mpashka.vocabulary.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UnstressedWordsTest {

    private static Entry entry(String headword, PartOfSpeech partOfSpeech, String translation) {
        return new Entry("", java.text.Normalizer.normalize(headword, java.text.Normalizer.Form.NFD), "",
                List.of(), partOfSpeech, List.of(new Entry.Sense(null, List.of(translation), List.of())),
                List.of(), WordStatus.IMPORTED);
    }

    @ParameterizedTest(name = "{0} «{2}» — {3}")
    @CsvSource({
            "за,   UNKNOWN,      за,       true",
            "из,   NOUN,         из,       true",
            "и,    CONJUNCTION,  и,        true",
            "да,   PARTICLE,     да,       true",
            "га,   PRONOUN,      он,       true",
            "без,  NOUN,         без,      true",
            "без,  NOUN,         бязь,     false",
            "низ,  NOUN,         ряд,      false",
            "и,    INTERJECTION, ах!,      false",
            "а,    INTERJECTION, а,        false",
            "во̀да, NOUN,         вода́,     false",
    })
    @DisplayName("безударное — заглавное без тона, а перевод служебный")
    void unstressed(String headword, PartOfSpeech partOfSpeech, String translation, boolean expected) {
        assertThat(UnstressedWords.isUnstressed(entry(headword, partOfSpeech, translation))).isEqualTo(expected);
    }
}
