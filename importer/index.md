---
tags: "@tag:import @tag:source-db @tag:wiktionary @tag:hjp"
---

# Перенос данных и отчёты

Родительский индекс: [../docs/index.md](../docs/index.md) ·
Прогоны и их числа: [../docs/testing/rules-reports.md](../docs/testing/rules-reports.md)

Конвертация исходной sqlite-базы в Postgres и отчёты о качестве правил. Исходная база
открывается **только на чтение** — это чужой файл из Android-приложения.

Код: `src/main/java/org/mpashka/vocabulary/importer/`.

## Перенос

- `SourceReader.java` — последовательное чтение всех статей исходной базы
- `MigrateToPostgres.java` — перенос словаря в Postgres, `./gradlew :importer:migrate`
  (схема — [db-schema.md](../docs/implementation/db-schema.md))
- `Homonyms.java` — разделение омонимов, упакованных исходной базой в одну строку;
  ударение каждого восстанавливается из разметки
- `WordForms.java` — сборка словоформ для указателя поиска: порождаются только буквы,
  без ударения

## Викисловарь

Устройство и выгрузка — [wiktionary.md](../docs/implementation/wiktionary.md).

- `WiktionaryPages.java` — последовательное чтение страниц выгрузки (`.bz2`) и выписки (`.gz`)
- `WiktionaryExtract.java` — выписка сербохорватских разделов,
  `./gradlew :importer:wiktionaryExtract`
- `WiktionaryAccentReport.java` — сверка ударений викисловаря с базой, ничего не меняя,
  `./gradlew :importer:runWiktionaryAccents`
- `WiktionaryAccents.java` — запись ударений викисловаря в базу после переноса, расхождения —
  в `discrepancy`, `./gradlew :importer:wiktionaryAccents`
- `WiktionaryPartsOfSpeech.java` — часть речи из викисловаря словам с `UNKNOWN`, с замером
  точности тем же прогоном, `./gradlew :importer:wiktionaryPartsOfSpeech`
- `WiktionaryMatch.java` — сопоставление наших слов и форм со словами викисловаря, общее у
  сверки и записи
- `HjpAccents.java` — ударение заглавного слова из hjp.znanje.hr по выверенному списку
  `src/main/resources/hjp-accents.tsv`, `./gradlew :importer:hjpAccents`
  ([sources.md](../docs/implementation/sources.md), «hjp.znanje.hr»)
- `TargetDatabase.java` — подключение к Postgres из `secrets.properties`, общее у переноса и отчётов

## Отчёты о качестве правил

Печатают сводку в консоль, ничего не меняя. Что и с чем сверяется и какая точность
достигнута — [rules-reports.md](../docs/testing/rules-reports.md).

- `PartOfSpeechReport.java` — часть речи вслепую, `./gradlew :importer:run`
- `NounFormsReport.java` — родительный падеж, `./gradlew :importer:runNounForms`
- `VerbFormsReport.java` — настоящее время, `./gradlew :importer:runVerbForms`
- `AdjectiveFormsReport.java` — формы родов, `./gradlew :importer:runAdjectiveForms`
