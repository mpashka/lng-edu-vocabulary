package org.mpashka.vocabulary.importer;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

/**
 * Выписывает из полной выгрузки викисловаря сербохорватские разделы статей в маленький файл
 * того же формата. Полная выгрузка распаковывается минуты, выписка — секунды, а разбирать её
 * приходится много раз.
 *
 * <p>Запуск: {@code ./gradlew :importer:wiktionaryExtract}; аргументы — путь к выгрузке и
 * к выписке, по умолчанию последняя {@code .data/wiktionary/enwiktionary-*.xml.bz2} и
 * {@code .data/wiktionary/sh-pages.xml.gz}.
 */
// @tag:wiktionary
public final class WiktionaryExtract {

    private static final Pattern SECTION = Pattern.compile(
            "^==Serbo-Croatian==\\s*$.*?(?=^==[^=]|\\z)", Pattern.MULTILINE | Pattern.DOTALL);

    private WiktionaryExtract() {
    }

    public static void main(String[] args) throws IOException, XMLStreamException {
        Path dump = args.length > 0 ? Path.of(args[0]) : latestDump();
        Path extract = Path.of(args.length > 1 ? args[1] : ".data/wiktionary/sh-pages.xml.gz");
        System.out.printf("Выгрузка: %s%nВыписка:  %s%n", dump, extract);

        long[] pages = {0, 0};
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(extract), 1 << 16)) {
            XMLStreamWriter xml = XMLOutputFactory.newFactory().createXMLStreamWriter(out, "UTF-8");
            xml.writeStartDocument("UTF-8", "1.0");
            xml.writeStartElement("pages");
            xml.writeAttribute("source", dump.getFileName().toString());
            WiktionaryPages.forEachArticle(dump, page -> {
                if (++pages[0] % 1_000_000 == 0) {
                    System.out.printf("  просмотрено %,d статей, выписано %,d%n", pages[0], pages[1]);
                }
                Matcher section = SECTION.matcher(page.text());
                if (!section.find()) {
                    return;
                }
                xml.writeStartElement("page");
                xml.writeStartElement("title");
                xml.writeCharacters(page.title());
                xml.writeEndElement();
                xml.writeStartElement("text");
                xml.writeCharacters(section.group());
                xml.writeEndElement();
                xml.writeEndElement();
                pages[1]++;
            });
            xml.writeEndElement();
            xml.writeEndDocument();
            xml.close();
        }
        System.out.printf("Статей: %,d, из них с сербохорватским разделом: %,d%n", pages[0], pages[1]);
    }

    private static Path latestDump() throws IOException {
        try (var files = Files.list(Path.of(".data/wiktionary"))) {
            return files.filter(p -> p.getFileName().toString().matches("enwiktionary-\\d+-pages-articles\\.xml\\.bz2"))
                    .sorted()
                    .reduce((first, second) -> second)
                    .orElseThrow(() -> new IllegalStateException(
                            "В .data/wiktionary нет выгрузки enwiktionary-<дата>-pages-articles.xml.bz2 — "
                                    + "откуда её взять, docs/implementation/wiktionary.md"));
        }
    }
}
