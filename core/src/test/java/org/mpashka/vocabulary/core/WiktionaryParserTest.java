package org.mpashka.vocabulary.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Разметка — сокращённые подлинные статьи en.wiktionary.org (2026-10-04). */
class WiktionaryParserTest {

    private static final String VODA = """
            ==Serbo-Croatian==
            {{wp|sh:}}

            ===Etymology===
            {{inh+|sh|sla-pro|*voda}}, from {{inh|sh|ine-bsl-pro|*wandō}}, ultimately from {{inh|sh|ine-pro|*wódr̥}}.

            ===Pronunciation===
            * {{sh-IPA|vòda}}

            ===Noun===
            {{sh-noun|vòda|f|adj=voden}}

            # [[water]]

            ====Declension====
            {{sh-decl-noun
            |vòda|vȍde
            |vòdē|vódā
            |vòdi / [[vȍdi]]|vòdama
            |vȍdu|vȍde
            |vȍdo|vȍde
            |vòdi|vòdama
            |vòdōm|vòdama
            }}

            ==Slovak==
            {{sk-noun|f}}
            """;

    private static final String BEZ = """
            ==Serbo-Croatian==

            ===Etymology 1===
            {{inh+|sh|sla-pro|*bez}}.

            ====Preposition====
            {{sh-prep|bèz}} {{+obj|sh|gen}}

            # [[without]]

            ===Etymology 2===
            {{bor+|sh|ota|بز|tr=bez}}

            ====Pronunciation====
            * {{IPA|sh|/bêz/}}

            ====Noun====
            {{sh-noun|bȅz|m-in}}

            =====Declension=====
            {{sh-decl-noun-unc
            |bez
            |beza
            |bezu
            |bez
            |bezu
            |bezu
            |bezom
            }}
            """;

    private static final String RADITI = """
            ==Serbo-Croatian==

            ===Verb===
            {{sh-verb|ráditi|impf}}

            ====Conjugation====
            {{sh-conj
            |pr.va=rádēći
            |vn=ráđēnje
            |pr.1s=radim
            |pr.3p=rade
            |f1.stem=radi
            |app.fp=radile
            }}
            """;

    private static String nfd(String value) {
        return Serbian.decomposeLatinVowels(value);
    }

    @Test
    @DisplayName("существительное: заглавное слово, предки и склонение с ударением в каждой форме")
    void nounWithAccentedDeclension() {
        List<WiktionaryEntry> entries = WiktionaryParser.serboCroatian("voda", VODA);

        assertThat(entries).hasSize(1);
        WiktionaryEntry voda = entries.getFirst();
        assertThat(voda.partOfSpeech()).isEqualTo(PartOfSpeech.NOUN);
        assertThat(voda.headword()).contains(nfd("vòda"));
        assertThat(voda.etymons()).extracting(WiktionaryEntry.Etymon::language)
                .containsExactly("sla-pro", "ine-bsl-pro", "ine-pro");
        assertThat(voda.forms()).hasSize(15);
        assertThat(voda.forms()).contains(
                new Form("acc.sg", "vodu", Optional.of(nfd("vȍdu"))),
                new Form("dat.sg", "vodi", Optional.of(nfd("vòdi"))),
                new Form("dat.sg", "vodi", Optional.of(nfd("vȍdi"))),
                new Form("ins.pl", "vodama", Optional.of(nfd("vòdama"))));
    }

    @Test
    @DisplayName("две этимологии — два слова, каждое со своими предками; склонение без тона — без ударения")
    void twoEtymologies() {
        List<WiktionaryEntry> entries = WiktionaryParser.serboCroatian("bez", BEZ);

        assertThat(entries).extracting(WiktionaryEntry::partOfSpeech)
                .containsExactly(PartOfSpeech.PREPOSITION, PartOfSpeech.NOUN);
        assertThat(entries.get(0).etymons()).extracting(WiktionaryEntry.Etymon::relation).containsExactly("inh");
        assertThat(entries.get(1).etymons()).extracting(WiktionaryEntry.Etymon::relation).containsExactly("bor");
        assertThat(entries.get(1).headword()).contains(nfd("bȅz"));
        assertThat(entries.get(1).forms()).hasSize(7)
                .allSatisfy(form -> assertThat(form.accented()).isEmpty())
                .contains(new Form("ins.sg", "bezom"));
    }

    @Test
    @DisplayName("спряжение: формы по именам параметров, основа будущего времени формой не считается")
    void conjugation() {
        WiktionaryEntry raditi = WiktionaryParser.serboCroatian("raditi", RADITI).getFirst();

        assertThat(raditi.headword()).contains(nfd("ráditi"));
        assertThat(raditi.forms()).containsExactlyInAnyOrder(
                new Form("adv.praes", "radeći", Optional.of(nfd("rádēći"))),
                new Form("praes.1sg", "radim"),
                new Form("praes.3pl", "rade"),
                new Form("part.act.f.pl", "radile"),
                new Form("noun.verbal", "rađenje", Optional.of(nfd("ráđēnje"))));
    }

    @Test
    @DisplayName("склонение, записанное именами параметров, разбирается так же, как перечисленное")
    void namedDeclension() {
        String zajam = """
                ==Serbo-Croatian==
                ===Noun===
                {{sh-noun||m-in}}
                ====Declension====
                {{sh-decl-noun
                |ns=zajam
                |np=zajmovi
                |gs=zajma
                |is=zajmom
                |ip=zajmovima}}
                """;

        WiktionaryEntry entry = WiktionaryParser.serboCroatian("zajam", zajam).getFirst();

        assertThat(entry.headword()).isEmpty();
        assertThat(entry.forms()).containsExactlyInAnyOrder(new Form("nom.sg", "zajam"), new Form("nom.pl", "zajmovi"),
                new Form("gen.sg", "zajma"), new Form("ins.sg", "zajmom"), new Form("ins.pl", "zajmovima"));
    }

    @Test
    @DisplayName("нет сербохорватского раздела — нет и слов")
    void noSection() {
        assertThat(WiktionaryParser.serboCroatian("abažur", "==Slovak==\n{{sk-noun|m}}\n")).isEmpty();
    }
}
