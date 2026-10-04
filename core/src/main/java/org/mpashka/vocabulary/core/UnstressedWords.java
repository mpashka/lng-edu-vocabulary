package org.mpashka.vocabulary.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    /**
     * Закрытый список русских служебных слов, которыми словарь переводит безударные, с их
     * частью речи. {@code да} и {@code хоть} бывают и союзом, и частицей — части речи не дают.
     */
    private static final Map<String, PartOfSpeech> RUSSIAN_FUNCTION_WORDS = functionWords();

    private static Map<String, PartOfSpeech> functionWords() {
        Map<String, PartOfSpeech> words = new HashMap<>();
        for (String preposition : List.of("в", "во", "на", "за", "из", "с", "со", "о", "об", "к", "ко", "по",
                "при", "у", "от", "до", "для", "без", "над", "под", "пред", "перед", "через", "сквозь",
                "после", "около", "возле", "близ", "вокруг", "среди", "между", "кроме", "поверх", "против")) {
            words.put(preposition, PartOfSpeech.PREPOSITION);
        }
        for (String conjunction : List.of("и", "а", "но", "что", "хотя", "если", "когда")) {
            words.put(conjunction, PartOfSpeech.CONJUNCTION);
        }
        for (String particle : List.of("ли", "не", "ни")) {
            words.put(particle, PartOfSpeech.PARTICLE);
        }
        for (String pronoun : List.of("я", "ты", "он", "она", "оно", "мы", "вы", "они", "себя")) {
            words.put(pronoun, PartOfSpeech.PRONOUN);
        }
        words.put("да", PartOfSpeech.UNKNOWN);
        words.put("хоть", PartOfSpeech.UNKNOWN);
        return Map.copyOf(words);
    }

    private UnstressedWords() {
    }

    public static boolean isUnstressed(Entry entry) {
        return entry.partOfSpeech() != PartOfSpeech.INTERJECTION
                && functionWord(entry.headword(), entry.senses()).isPresent();
    }

    /**
     * Часть речи безударного слова по его переводу: русский предлог — предлог и так далее.
     * {@code UNKNOWN} — слово не безударное или перевод двусмысленный ({@code да}).
     */
    public static PartOfSpeech partOfSpeech(String headword, List<Entry.Sense> senses) {
        return functionWord(headword, senses).orElse(PartOfSpeech.UNKNOWN);
    }

    private static Optional<PartOfSpeech> functionWord(String headword, List<Entry.Sense> senses) {
        if (Accent.toneCount(headword) != 0) {
            return Optional.empty();
        }
        return senses.stream().flatMap(s -> s.translations().stream()).findFirst()
                .map(t -> RUSSIAN_FUNCTION_WORDS.get(Serbian.stripCombiningAccents(t).strip().toLowerCase()));
    }
}
