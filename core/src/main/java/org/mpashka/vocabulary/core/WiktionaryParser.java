package org.mpashka.vocabulary.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Разбор сербохорватского раздела статьи викисловаря по шаблонам вики-разметки.
 * Шаблоны и их значение — docs/implementation/wiktionary.md.
 */
// @tag:wiktionary
public final class WiktionaryParser {

    private static final Pattern SECTION = Pattern.compile(
            "^==Serbo-Croatian==\\s*$(.*?)(?=^==[^=]|\\z)", Pattern.MULTILINE | Pattern.DOTALL);
    private static final Pattern HEADER = Pattern.compile("^(={3,})\\s*(.+?)\\s*\\1\\s*$");

    private static final Map<String, PartOfSpeech> PARTS_OF_SPEECH = Map.ofEntries(
            Map.entry("Noun", PartOfSpeech.NOUN),
            Map.entry("Proper noun", PartOfSpeech.NOUN),
            Map.entry("Verb", PartOfSpeech.VERB),
            Map.entry("Adjective", PartOfSpeech.ADJECTIVE),
            Map.entry("Participle", PartOfSpeech.ADJECTIVE),
            Map.entry("Adverb", PartOfSpeech.ADVERB),
            Map.entry("Pronoun", PartOfSpeech.PRONOUN),
            Map.entry("Determiner", PartOfSpeech.PRONOUN),
            Map.entry("Numeral", PartOfSpeech.NUMERAL),
            Map.entry("Interjection", PartOfSpeech.INTERJECTION),
            Map.entry("Conjunction", PartOfSpeech.CONJUNCTION),
            Map.entry("Preposition", PartOfSpeech.PREPOSITION),
            Map.entry("Particle", PartOfSpeech.PARTICLE),
            Map.entry("Prefix", PartOfSpeech.UNKNOWN),
            Map.entry("Suffix", PartOfSpeech.UNKNOWN),
            Map.entry("Phrase", PartOfSpeech.UNKNOWN),
            Map.entry("Proverb", PartOfSpeech.UNKNOWN));

    private static final String[] CASES = {"nom", "gen", "dat", "acc", "voc", "loc", "ins"};
    private static final Pattern NAMED_CASE = Pattern.compile("([ngdavli])([sp])");

    private static final Map<String, String> CONJUGATION_TENSES = Map.of(
            "pr", "praes", "a", "aor", "impf", "impf", "impt", "imper",
            "app", "part.act", "ppp", "part.pass");

    private WiktionaryParser() {
    }

    /** Слова сербохорватского раздела в порядке статьи; раздела нет — пустой список. */
    public static List<WiktionaryEntry> serboCroatian(String title, String wikitext) {
        Matcher section = SECTION.matcher(wikitext);
        if (!section.find()) {
            return List.of();
        }
        List<EntryDraft> drafts = new ArrayList<>();
        List<WiktionaryEntry.Etymon> etymons = new ArrayList<>();
        EntryDraft draft = null;
        for (String line : section.group(1).split("\n")) {
            Matcher header = HEADER.matcher(line);
            if (header.matches() && header.group(2).startsWith("Etymology")) {
                etymons = new ArrayList<>();
                draft = null;
            } else if (header.matches() && PARTS_OF_SPEECH.containsKey(header.group(2))) {
                draft = new EntryDraft(PARTS_OF_SPEECH.get(header.group(2)), etymons);
                drafts.add(draft);
            } else if (draft == null) {
                etymons.addAll(etymonsOf(line));
            } else {
                draft.wikitext.append(line).append('\n');
            }
        }
        return drafts.stream().map(d -> d.toEntry(title)).toList();
    }

    private static List<WiktionaryEntry.Etymon> etymonsOf(String line) {
        List<WiktionaryEntry.Etymon> etymons = new ArrayList<>();
        for (Template template : Template.all(line)) {
            String relation = template.name.replace("+", "");
            if (List.of("inh", "der", "bor").contains(relation) && template.positional.size() >= 3
                    && template.positional.get(0).equals("sh")) {
                etymons.add(new WiktionaryEntry.Etymon(relation, template.positional.get(1),
                        template.positional.get(2)));
            }
        }
        return etymons;
    }

    /** Раздел части речи, пока строки его не кончились. */
    private static final class EntryDraft {
        private final PartOfSpeech partOfSpeech;
        private final List<WiktionaryEntry.Etymon> etymons;
        private final StringBuilder wikitext = new StringBuilder();

        EntryDraft(PartOfSpeech partOfSpeech, List<WiktionaryEntry.Etymon> etymons) {
            this.partOfSpeech = partOfSpeech;
            this.etymons = List.copyOf(etymons);
        }

        WiktionaryEntry toEntry(String title) {
            Optional<String> headword = Optional.empty();
            List<Form> forms = new ArrayList<>();
            for (Template template : Template.all(wikitext.toString())) {
                if (headword.isEmpty() && template.name.startsWith("sh-")
                        && !template.name.startsWith("sh-decl") && !template.name.equals("sh-conj")
                        && !template.positional.isEmpty()) {
                    headword = accented(template.positional.getFirst());
                } else if (template.name.startsWith("sh-decl-noun")) {
                    forms.addAll(declension(template));
                } else if (template.name.equals("sh-conj")) {
                    forms.addAll(conjugation(template));
                }
            }
            return new WiktionaryEntry(title, partOfSpeech, headword, forms, etymons);
        }
    }

    /**
     * {@code sh-decl-noun} перечисляет пары «единственное | множественное» по семи падежам,
     * {@code -unc} — только единственное, {@code -pl} — только множественное. Бывает и
     * запись именами: {@code ns=zajam|gs=zajma|gp=zajmova}.
     */
    private static List<Form> declension(Template template) {
        List<String> values = template.positional;
        List<Form> forms = new ArrayList<>();
        for (Map.Entry<String, String> parameter : template.named.entrySet()) {
            Matcher name = NAMED_CASE.matcher(parameter.getKey());
            if (name.matches()) {
                String caseName = Arrays.stream(CASES).filter(c -> c.startsWith(name.group(1))).findFirst().orElseThrow();
                addVariants(forms, caseName + "." + (name.group(2).equals("s") ? "sg" : "pl"), parameter.getValue());
            }
        }
        boolean paired = template.name.equals("sh-decl-noun");
        String singleNumber = template.name.endsWith("-pl") ? "pl" : "sg";
        for (int i = 0; i < values.size(); i++) {
            int caseIndex = paired ? i / 2 : i;
            if (caseIndex >= CASES.length) {
                break;
            }
            String number = paired ? (i % 2 == 0 ? "sg" : "pl") : singleNumber;
            addVariants(forms, CASES[caseIndex] + "." + number, values.get(i));
        }
        return forms;
    }

    /** Имена параметров {@code sh-conj}: {@code pr.1s}, {@code app.fp}, {@code pr.va}. */
    private static List<Form> conjugation(Template template) {
        List<Form> forms = new ArrayList<>();
        for (Map.Entry<String, String> parameter : template.named.entrySet()) {
            String[] parts = parameter.getKey().split("\\.");
            if (parts.length != 2) {
                continue;
            }
            String grammar = switch (parameter.getKey()) {
                case "pr.va" -> "adv.praes";
                case "pa.va" -> "adv.past";
                default -> CONJUGATION_TENSES.containsKey(parts[0]) && parts[1].length() == 2
                        ? CONJUGATION_TENSES.get(parts[0]) + "." + personOrGender(parts[1])
                        : null;
            };
            if (grammar != null) {
                addVariants(forms, grammar, parameter.getValue());
            }
        }
        String verbalNoun = template.named.get("vn");
        if (verbalNoun != null) {
            addVariants(forms, "noun.verbal", verbalNoun);
        }
        return forms;
    }

    /** {@code 1s} → {@code 1sg}, {@code fp} → {@code f.pl}. */
    private static String personOrGender(String code) {
        String number = code.charAt(1) == 's' ? "sg" : "pl";
        return Character.isDigit(code.charAt(0)) ? code.charAt(0) + number : code.charAt(0) + "." + number;
    }

    /** Ячейка таблицы может держать несколько допустимых форм: {@code vòdi / vȍdi}. */
    private static void addVariants(List<Form> forms, String grammar, String cell) {
        for (String variant : cell.split("\\s*(/|,|<br\\s*/?>)\\s*")) {
            String value = variant.strip();
            if (value.isEmpty() || value.equals("-") || value.equals("—")) {
                continue;
            }
            String decomposed = Serbian.decomposeLatinVowels(value);
            forms.add(new Form(grammar, Serbian.stripCombiningAccents(decomposed), accented(decomposed)));
        }
    }

    /** Запись с ударением; без знака тона — пусто: долгота одна ударения не даёт. */
    private static Optional<String> accented(String value) {
        String decomposed = Serbian.decomposeLatinVowels(value);
        return Accent.toneCount(decomposed) == 0 ? Optional.empty() : Optional.of(decomposed);
    }

    /** Вызов шаблона {@code {{имя|позиционные|имя=значение}}} с раскрытыми ссылками. */
    private record Template(String name, List<String> positional, Map<String, String> named) {

        static List<Template> all(String text) {
            List<Template> templates = new ArrayList<>();
            int start = text.indexOf("{{");
            while (start >= 0) {
                int end = closing(text, start);
                if (end < 0) {
                    break;
                }
                templates.add(of(text.substring(start + 2, end)));
                start = text.indexOf("{{", end + 2);
            }
            return templates;
        }

        private static int closing(String text, int start) {
            int depth = 0;
            for (int i = start; i < text.length() - 1; i++) {
                if (text.startsWith("{{", i)) {
                    depth++;
                    i++;
                } else if (text.startsWith("}}", i)) {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                    i++;
                }
            }
            return -1;
        }

        private static Template of(String body) {
            List<String> parts = splitTopLevel(body);
            List<String> positional = new ArrayList<>();
            Map<String, String> named = new LinkedHashMap<>();
            for (String part : parts.subList(1, parts.size())) {
                int equals = part.indexOf('=');
                if (equals > 0 && !part.substring(0, equals).contains("[")) {
                    named.put(part.substring(0, equals).strip(), unlink(part.substring(equals + 1)).strip());
                } else {
                    positional.add(unlink(part).strip());
                }
            }
            return new Template(parts.getFirst().strip(), positional, named);
        }

        /** Делит по {@code |}, не заходя внутрь вложенных шаблонов и ссылок. */
        private static List<String> splitTopLevel(String body) {
            List<String> parts = new ArrayList<>();
            int depth = 0;
            int from = 0;
            for (int i = 0; i < body.length(); i++) {
                if (body.startsWith("{{", i) || body.startsWith("[[", i)) {
                    depth++;
                    i++;
                } else if (body.startsWith("}}", i) || body.startsWith("]]", i)) {
                    depth--;
                    i++;
                } else if (body.charAt(i) == '|' && depth == 0) {
                    parts.add(body.substring(from, i));
                    from = i + 1;
                }
            }
            parts.add(body.substring(from));
            return parts;
        }

        /** {@code [[vȍdi]]} → {@code vȍdi}, {@code [[voda|vòdi]]} → {@code vòdi}. */
        private static String unlink(String value) {
            return value.replaceAll("\\[\\[(?:[^|\\]]*\\|)?([^\\]]*)]]", "$1");
        }
    }
}
