---
tags: "@tag:word-forms @tag:word-roots @tag:accent"
---

# Бэкенд

Родительский индекс: [../docs/index.md](../docs/index.md) ·
Договор с фронтендом: [../docs/specification/api.md](../docs/specification/api.md)

Spring Boot 4.1 на Java 25. Отдаёт REST API словаря на `http://localhost:8180`, данные
берёт из Postgres — словарь перенесён туда конвертером
([importer](../importer/index.md)). Схема применяется Flyway при запуске.

Код: `src/main/java/org/mpashka/vocabulary/backend/`.

## Файлы

- `VocabularyApplication.java` — точка входа
- `WordController.java` — `/api/words` (поиск) и `/api/words/{name}` (карточка слова);
  здесь же записи ответа
- `ShellController.java` — возврат оболочки по адресам `/word/**`: их знает только
  страница, и без этого прямая ссылка на статью давала бы 404
  ([deploy.md](../docs/implementation/deploy.md))
- `PostgresDictionary.java` — чтение словаря из Postgres: поиск по словоформам, по
  латинице, кириллице и русскому переводу
- `src/main/resources/application.yaml` — порт 8180, подключение к базе `vocabulary`,
  Flyway
- `src/main/resources/db/migration/` — миграции схемы; замысел и обоснования —
  [db-schema.md](../docs/implementation/db-schema.md)

## Запуск

```bash
./gradlew :backend:bootRun
```

Нужна локальная база Postgres — [development.md](../docs/implementation/development.md).

Сборка для сервера — `./gradlew :backend:bootJar`: в jar уезжает и веб-оболочка. Как это
выложено и как обновляется — [deploy.md](../docs/implementation/deploy.md).
