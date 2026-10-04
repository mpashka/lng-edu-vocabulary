package org.mpashka.vocabulary.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Словоформы, выписанные в самой статье исходного словаря.
 *
 * <p>Это <b>единственный достоверный источник ударения в словоформах</b>: правила дают
 * только буквы, а ударение заглавного слова форме не принадлежит — совпадает оно лишь
 * в 16,4 % случаев ([docs/implementation/word-forms.md]). Статья выписывает по одной опорной форме на
 * часть речи:
 *
 * <pre>
 *   аба‛жу_р, абажу’ра        существительное — родительный падеж
 *   ра’дити, ра^ди_м          глагол — первое лицо настоящего времени
 *   абеце’д||ан, ~ни_, ~на    прилагательное — формы родов
 * </pre>
 *
 * <p>🚨 <b>Скрытая часть формы даёт буквы, видимая — ударение.</b> Сокращённая запись
 * прячет начало формы: тильда стоит вместо основы ({@code ~ости}), дефис — вместо
 * начала слова ({@code -зу’јем}). Буквы спрятанного берутся из заглавного слова, а его
 * тон — нет: тон словоформы совпадает с тоном заглавного слова лишь в 16,4 % случаев,
 * и перенести его на форму значило бы выдумать ударение. Поэтому ударение получает
 * только форма, у которой тон <b>напечатан</b>: целиком выписанная ({@code абажу’ра})
 * либо сокращённая, но с тоном в видимой части ({@code пи“п||авац, ~а’вца} →
 * {@code пипа’вца}).
 *
 * <p>Проверка на выходе одна: в сербском слове тон ровно один. Вышло два или ни одного —
 * ударение неизвестно, и форма остаётся с одними буквами. Выдуманное ударение хуже
 * честно отсутствующего: проверить его потом нечем.
 */
// @tag:word-forms @tag:accent
public final class SourceForms {

    /**
     * Докуда искать опорную форму: она стоит сразу за заглавным словом, через запятую.
     * Дальше в шапке идут пометы и варианты написания, формой они не являются.
     *
     * <p>У существительного предел жёстче на один фрагмент: падежная форма стоит прямо
     * за заглавным словом, и измерено, что четвёртым фрагментом уже начинается не она —
     * с пределом в пять расхождений с правилами становится на 57 больше
     * ([docs/testing/rules-reports.md]). У глагола между инфинитивом и формой успевают
     * встать дефис и возвратная частица, поэтому предел на один шире.
     */
    private static final int NOUN_HEADER_LIMIT = 4;

    /** Докуда искать форму настоящего времени. */
    private static final int VERB_HEADER_LIMIT = 5;

    private SourceForms() {
    }

    /**
     * Формы этой статьи, выписанные словарём. Заглавное слово в их число не входит:
     * оно переносится само по себе.
     *
     * @param entry  разобранная статья
     * @param gender род существительного; {@code null} — у прочих частей речи
     * @param chunks фрагменты разметки статьи
     */
    public static List<Form> inArticle(Entry entry, Gender gender, List<Chunk> chunks) {
        if (chunks.isEmpty()) {
            return List.of();
        }
        return switch (entry.partOfSpeech()) {
            case NOUN -> gender == null ? List.of() : one(genitive(chunks, entry.grammar()));
            case VERB -> one(present(chunks));
            case ADJECTIVE -> genderForms(chunks);
            default -> List.of();
        };
    }

    /**
     * Родительный падеж — второй сербский фрагмент шапки.
     *
     * <p>Многословные названия исключены: вторым фрагментом у них идёт не падежная
     * форма, а второе слово названия ({@code Аралско Је“зеро, а‛ралско_}). У слов с
     * пометой {@code мн} выписан родительный множественного — помета у формы своя.
     */
    private static Optional<Form> genitive(List<Chunk> chunks, List<String> marks) {
        String headword = chunks.getFirst().text();
        if (Serbian.bare(headword).contains(" ")) {
            return Optional.empty();
        }
        String grammar = marks.contains("мн") ? "gen.pl" : "gen.sg";
        return headerForm(chunks).map(written -> form(grammar, written, headword));
    }

    /**
     * Первое лицо настоящего времени. Сокращённая запись ({@code аванзова‛ти, -зу’јем})
     * восстанавливается по инфинитиву.
     */
    private static Optional<Form> present(List<Chunk> chunks) {
        String infinitive = chunks.getFirst().text();
        boolean abbreviated = false;
        for (int i = 1; i < chunks.size() && i < VERB_HEADER_LIMIT; i++) {
            Chunk chunk = chunks.get(i);
            if (chunk.isTranslation() || Chunk.SENSE_NUMBER.equals(chunk.tag())) {
                return Optional.empty();
            }
            if (!chunk.hasTag() && chunk.text().startsWith("-")) {
                abbreviated = true;
                continue;
            }
            if (!Chunk.SERBIAN.equals(chunk.tag()) && !Chunk.SERBIAN_LINK.equals(chunk.tag())) {
                continue;
            }
            String written = chunk.text();
            String bare = Serbian.bare(written);
            // Возвратная частица идёт отдельным фрагментом — это не форма, смотрим дальше.
            if (bare.equals("се")) {
                continue;
            }
            // Тильда означает повтор основы — это не форма настоящего времени, а статья
            // другого устройства (причастие, устойчивое сочетание).
            if (bare.isEmpty() || bare.startsWith("~") || bare.equals(Serbian.bare(infinitive))) {
                return Optional.empty();
            }
            // Дефис бывает отдельным фрагментом, внутри текста формы, а иногда отсутствует
            // вовсе. Тогда сокращение видно по самой форме: она короче инфинитива и
            // начинается с других букв («биберити, рим»).
            boolean shortened = abbreviated || bare.startsWith("-")
                    || (bare.length() < Serbian.bare(infinitive).length()
                        && bare.length() >= 2
                        && !Serbian.bare(infinitive).startsWith(bare.substring(0, 2)));
            return Optional.of(shortened
                    ? expanded(infinitive, written)
                    : form("praes.1sg", written, infinitive));
        }
        return Optional.empty();
    }

    /** Формы родов, выписанные в шапке статьи ({@code абеце’д||ан, ~ни_, ~на, ~но}). */
    private static List<Form> genderForms(List<Chunk> chunks) {
        String headword = chunks.getFirst().text();
        Map<String, Form> forms = new LinkedHashMap<>();
        for (int i = 1; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
            if (chunk.isTranslation() || Chunk.SENSE_NUMBER.equals(chunk.tag())) {
                break;
            }
            if (!Chunk.SERBIAN.equals(chunk.tag())) {
                continue;
            }
            Form form = form("adj", chunk.text(), headword);
            if (!form.value().isEmpty() && !form.value().equalsIgnoreCase(Serbian.bare(headword))) {
                forms.putIfAbsent(form.value(), form);
            }
        }
        return List.copyOf(forms.values());
    }

    /**
     * Первый сербский фрагмент шапки после заглавного слова либо пусто, если формы там
     * нет вовсе.
     *
     * <p>Повтор заглавного слова формой не считается: это вариант ударения
     * ({@code го‛ра ж., го“ра ж.}). Сравнение без учёта регистра — у имён собственных
     * вторым фрагментом идёт тот же заголовок со строчной буквы ({@code Африка, африка}).
     */
    private static Optional<String> headerForm(List<Chunk> chunks) {
        String headword = chunks.getFirst().text();
        for (int i = 1; i < chunks.size() && i < NOUN_HEADER_LIMIT; i++) {
            Chunk chunk = chunks.get(i);
            if (chunk.isTranslation() || Chunk.SENSE_NUMBER.equals(chunk.tag())) {
                return Optional.empty();
            }
            if (!Chunk.SERBIAN.equals(chunk.tag())) {
                continue;
            }
            return Serbian.bare(chunk.text()).equalsIgnoreCase(Serbian.bare(headword))
                    ? Optional.empty()
                    : Optional.of(chunk.text());
        }
        return Optional.empty();
    }

    /**
     * Форма, записанная сокращённо через дефис: буквы начала берутся из инфинитива,
     * ударение — только написанное у самой формы.
     *
     * <p>Начало формы словарь не напечатал, а значит, и ударения его не сообщил. Но тон
     * в сербском слове один: написан он в хвосте — в начале его нет, и приписанный
     * хвосту тон принадлежит самой форме, а не инфинитиву.
     */
    private static Form expanded(String infinitive, String written) {
        String value = VerbConjugation.expandAbbreviated(infinitive, written);
        String tail = written;
        while (tail.startsWith("-")) {
            tail = tail.substring(1);
        }
        String renderedTail = Serbian.renderAccents(Serbian.stripStemMarker(tail)).trim();
        String plainTail = Serbian.stripCombiningAccents(renderedTail);
        if (!value.endsWith(plainTail)) {
            // Совместить хвост с инфинитивом не удалось: буквы берём как вышло,
            // ударение в таком случае неизвестно.
            return new Form("praes.1sg", value);
        }
        return form("praes.1sg", value.substring(0, value.length() - plainTail.length()) + renderedTail);
    }

    /**
     * Форма по её записи в статье. Тильда раскрывается в основу заглавного слова, но
     * <b>без её ударения</b>: напечатанного ударения у этой части формы нет.
     */
    private static Form form(String grammar, String written, String headword) {
        String stem = Serbian.bare(Serbian.stem(headword));
        return form(grammar, Serbian.renderAccents(
                Serbian.stripStemMarker(written).replace("~", stem)));
    }

    /** Форма по отрисованной записи: ударение сохраняется, только если тон вышел один. */
    private static Form form(String grammar, String rendered) {
        String value = Serbian.stripCombiningAccents(rendered).trim();
        return new Form(grammar, value, Accent.toneCount(rendered) == 1
                ? Optional.of(rendered.trim())
                : Optional.empty());
    }

    private static List<Form> one(Optional<Form> form) {
        return form.map(List::of).orElseGet(List::of);
    }
}
