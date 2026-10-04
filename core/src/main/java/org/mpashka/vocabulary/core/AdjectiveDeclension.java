package org.mpashka.vocabulary.core;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Формы прилагательного по родам.
 *
 * <p><b>Важно: у прилагательных эти формы в исходной базе уже есть.</b> Словарь указывает
 * их сразу после заглавного слова ({@code абеце’д||ан, ~ни_, ~на, ~но}), и забирает их
 * оттуда {@link SourceForms} — вместе с ударением, где словарь его дал. Правила нужны
 * лишь там, где словарь форму опустил.
 */
// @tag:word-forms
public final class AdjectiveDeclension {

    private AdjectiveDeclension() {
    }

    /**
     * Порождённые формы родов для случая, когда словарь их не выписал.
     *
     * <p>Прилагательное даётся в краткой форме мужского рода. Женский и средний
     * получаются прибавлением окончания, причём у слов с беглым «а» оно выпадает:
     * {@code абеце’дан → абеце’дна, абеце’дно}.
     */
    public static List<String> genderForms(String headword) {
        String bare = Serbian.bare(headword);
        if (bare.isEmpty()) {
            return List.of();
        }
        Set<String> forms = new LinkedHashSet<>();

        // Заглавное слово бывает и в краткой форме (абеце’дан), и в определённой
        // (адјекти’вни). Во втором случае окончание надо сперва снять, иначе выйдет
        // «адјективнии».
        String stem = bare.endsWith("и") ? bare.substring(0, bare.length() - 1) : bare;
        addForms(forms, stem);

        // Беглое «а» последнего слога: абеце’дан → абеце’дн- + окончание.
        String withoutFleeting = dropFleetingA(stem);
        if (withoutFleeting != null) {
            addForms(forms, withoutFleeting);
        }
        return List.copyOf(forms);
    }

    private static void addForms(Set<String> target, String stem) {
        target.add(stem + "и");
        target.add(stem + "а");
        target.add(stem + "о");
        // Мягкая основа даёт средний род на -е: ба“бљи → ба“бље.
        target.add(stem + "е");
    }

    /** Убирает беглое «а» последнего слога: {@code абецедан → абецедн}. */
    private static String dropFleetingA(String bare) {
        if (bare.length() < 3) {
            return null;
        }
        int last = bare.length() - 1;
        boolean pattern = isConsonant(bare.charAt(last))
                && bare.charAt(last - 1) == 'а'
                && isConsonant(bare.charAt(last - 2));
        return pattern ? bare.substring(0, last - 1) + bare.charAt(last) : null;
    }

    private static boolean isConsonant(char letter) {
        return Character.isLetter(letter) && "аеиоу".indexOf(letter) < 0;
    }
}
