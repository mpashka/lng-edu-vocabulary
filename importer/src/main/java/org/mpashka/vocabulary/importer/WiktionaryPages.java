package org.mpashka.vocabulary.importer;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

/**
 * Последовательное чтение страниц выгрузки викисловаря: полной ({@code .xml.bz2} с
 * dumps.wikimedia.org) или выписки ({@code .xml.gz}, её пишет {@link WiktionaryExtract}).
 * Обе устроены одинаково: {@code <page>} с {@code <title>}, {@code <ns>} и {@code <text>}.
 */
// @tag:wiktionary
final class WiktionaryPages {

    record Page(String title, String text) {
    }

    interface PageConsumer {
        void accept(Page page) throws IOException, XMLStreamException;
    }

    private WiktionaryPages() {
    }

    /** Страницы основного пространства имён — статьи; служебные пропускаются. */
    static void forEachArticle(Path dump, PageConsumer consumer) throws IOException, XMLStreamException {
        try (InputStream in = open(dump)) {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            // Выгрузка весит десятки гигабайт, и ограничения JDK на размер документа её не пропускают.
            factory.setProperty("jdk.xml.totalEntitySizeLimit", 0);
            factory.setProperty("jdk.xml.maxGeneralEntitySizeLimit", 0);
            XMLStreamReader xml = factory.createXMLStreamReader(in, "UTF-8");
            String title = null;
            String namespace = "0";
            while (xml.hasNext()) {
                if (xml.next() != XMLStreamConstants.START_ELEMENT) {
                    continue;
                }
                switch (xml.getLocalName()) {
                    case "page" -> namespace = "0";
                    case "title" -> title = xml.getElementText();
                    case "ns" -> namespace = xml.getElementText();
                    case "text" -> {
                        String text = xml.getElementText();
                        if (namespace.equals("0")) {
                            consumer.accept(new Page(title, text));
                        }
                    }
                    default -> {
                    }
                }
            }
        }
    }

    private static InputStream open(Path dump) throws IOException {
        InputStream raw = new BufferedInputStream(Files.newInputStream(dump), 1 << 20);
        String name = dump.getFileName().toString();
        if (name.endsWith(".bz2")) {
            return new BZip2CompressorInputStream(raw, true);
        }
        return name.endsWith(".gz") ? new GZIPInputStream(raw, 1 << 16) : raw;
    }
}
