package org.mpashka.vocabulary.core;

import java.util.Set;

/**
 * Безударные служебные слова: предлоги, союзы, частицы, краткие формы местоимений
 * ({@code за}, {@code из}, {@code и}, {@code да}, {@code га}). Ударения у них нет по природе,
 * и сочинять его нельзя.
 *
 * <p>Опора — два факта из самой статьи: словарь написал заглавное слово без тона, и перевод
 * его — русское служебное слово. Часть речи от правил для этого не годится ({@code из},
 * {@code око}, {@code преко} у них существительные), викисловарь тоже ({@code niz} у него и
 * предлог, а наше {@code низ} — «ряд»). Перевод разводит омографы: {@code без} «без» —
 * безударное, {@code без} «бязь» — нет. Разбор — [docs/implementation/word-forms.md],
 * «Безударные слова».
 */
// @tag:accent
public final class UnstressedWords {

    /** Закрытый список русских служебных слов, которыми словарь переводит безударные. */
    private static final Set<String> RUSSIAN_FUNCTION_WORDS = Set.of(
            "в", "во", "на", "за", "из", "с", "со", "о", "об", "к", "ко", "по", "при", "у", "от",
            "до", "для", "без", "над", "под", "пред", "перед", "через", "сквозь", "после", "около",
            "возле", "близ", "вокруг", "среди", "между", "кроме", "поверх", "против",
            "и", "а", "но", "да", "что", "ли", "не", "ни", "хотя", "хоть", "если", "когда",
            "я", "ты", "он", "она", "оно", "мы", "вы", "они", "себя");

    private UnstressedWords() {
    }

    public static boolean isUnstressed(Entry entry) {
        return entry.partOfSpeech() != PartOfSpeech.INTERJECTION
                && Accent.toneCount(entry.headword()) == 0
                && entry.senses().stream().flatMap(s -> s.translations().stream()).findFirst()
                .map(t -> RUSSIAN_FUNCTION_WORDS.contains(
                        Serbian.stripCombiningAccents(t).strip().toLowerCase()))
                .orElse(false);
    }
}
