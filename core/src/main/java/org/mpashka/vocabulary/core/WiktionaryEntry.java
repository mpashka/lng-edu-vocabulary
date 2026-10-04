package org.mpashka.vocabulary.core;

import java.util.List;
import java.util.Optional;

/**
 * Одно слово из сербохорватского раздела статьи викисловаря: статья может нести несколько
 * таких — по части речи и по происхождению ({@code bez} — предлог и существительное).
 *
 * @param title        заголовок статьи — латиница без ударения; по нему строится ссылка
 * @param partOfSpeech часть речи по заголовку раздела; {@code UNKNOWN} — раздел есть, но
 *                     нашей части речи у него нет (приставка, оборот)
 * @param headword     заглавное слово с ударением, латиницей; пусто, если ударения нет
 * @param forms        формы из таблицы склонения или спряжения: латиница, ударение — где
 *                     его дал викисловарь
 * @param etymons      предки и источники заимствования
 */
// @tag:wiktionary
public record WiktionaryEntry(String title, PartOfSpeech partOfSpeech, Optional<String> headword,
                              List<Form> forms, List<Etymon> etymons) {

    /**
     * Предок слова из шаблона {@code {{inh|sh|sla-pro|*voda}}}.
     *
     * @param relation {@code inh} — унаследовано, {@code der} — произведено, {@code bor} —
     *                 заимствовано
     * @param language код языка викисловаря: {@code sla-pro}, {@code ine-pro}, {@code ota}
     */
    public record Etymon(String relation, String language, String term) {
    }
}
