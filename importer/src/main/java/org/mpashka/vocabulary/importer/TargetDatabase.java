package org.mpashka.vocabulary.importer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/** Подключение к целевой базе Postgres — общее у переноса и отчётов. */
final class TargetDatabase {

    private TargetDatabase() {
    }

    /**
     * Подключение к целевой базе: по строке из аргумента, иначе по настройкам из
     * {@code secrets.properties} в корне репозитория.
     */
    // @tag:secrets
    static Connection connect(String urlFromArgs) throws SQLException {
        if (urlFromArgs != null) {
            return DriverManager.getConnection(urlFromArgs);
        }
        Properties secrets = readSecrets();
        return DriverManager.getConnection(
                secrets.getProperty("vocabulary.db.url"),
                secrets.getProperty("vocabulary.db.username"),
                secrets.getProperty("vocabulary.db.password"));
    }

    /**
     * Читает {@code secrets.properties} из текущего каталога. Запуск через Gradle идёт из
     * корня репозитория ({@code workingDir} задан в корневом {@code build.gradle.kts}),
     * поэтому файл находится там же, где шаблон.
     */
    // @tag:secrets
    private static Properties readSecrets() {
        Path path = Path.of("secrets.properties").toAbsolutePath();
        if (!Files.exists(path)) {
            throw new IllegalStateException("Нет файла " + path + ". Скопируйте "
                    + "secrets.properties.template под этим именем и впишите пароль базы "
                    + "(README.md, раздел «Секреты»).");
        }
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(path)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Не читается " + path, e);
        }
        return properties;
    }
}
