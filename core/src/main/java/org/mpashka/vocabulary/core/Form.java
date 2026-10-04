package org.mpashka.vocabulary.core;

import java.util.Optional;

/**
 * Словоформа с грамматической пометой.
 *
 * <p>Помета — внутреннее обозначение указателя, а не помета словаря:
 * {@code nom.sg}, {@code gen.sg}, {@code loc.pl} у существительных,
 * {@code praes.1sg} у глаголов, {@code adj} у форм прилагательного, род которых
 * из статьи не выводится.
 *
 * @param grammar  помета формы
 * @param value    сама форма без знаков ударения — по ней ищут
 * @param accented та же форма с комбинируемыми знаками ударения; пусто, когда ударение
 *                 неизвестно: правила его не выводят, а словарь выписывает его не у
 *                 каждой формы ([docs/implementation/word-forms.md])
 */
// @tag:word-forms
public record Form(String grammar, String value, Optional<String> accented) {

    /** Форма, у которой известны только буквы: такими их дают правила. */
    public Form(String grammar, String value) {
        this(grammar, value, Optional.empty());
    }
}
