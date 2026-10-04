// Конвертация исходной sqlite-базы словаря в целевую Postgres.
plugins {
    application
}

dependencies {
    implementation(project(":core"))
    implementation(rootProject.libs.sqlite.jdbc)
    implementation(rootProject.libs.postgresql)
    implementation(rootProject.libs.commons.compress)
}

application {
    // Отчёт о качестве правил определения части речи: ./gradlew :importer:run
    mainClass = "org.mpashka.vocabulary.importer.PartOfSpeechReport"
}

// Отчёт о качестве правил склонения существительных.
tasks.register<JavaExec>("runNounForms") {
    group = "application"
    description = "Сверяет порождённый родительный падеж с указанным в исходной базе"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "org.mpashka.vocabulary.importer.NounFormsReport"
}

// Отчёт о качестве правил спряжения глаголов.
tasks.register<JavaExec>("runVerbForms") {
    group = "application"
    description = "Сверяет порождённое настоящее время с указанным в исходной базе"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "org.mpashka.vocabulary.importer.VerbFormsReport"
}

// Отчёт по формам прилагательных.
tasks.register<JavaExec>("runAdjectiveForms") {
    group = "application"
    description = "Показывает, сколько форм родов есть готовыми и что добирают правила"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "org.mpashka.vocabulary.importer.AdjectiveFormsReport"
}

// Перенос словаря из sqlite в Postgres (этап 5).
tasks.register<JavaExec>("migrate") {
    group = "application"
    description = "Переносит словарь из исходной sqlite-базы в Postgres"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "org.mpashka.vocabulary.importer.MigrateToPostgres"
}

// Выписка сербохорватских разделов из выгрузки викисловаря (этап 6).
tasks.register<JavaExec>("wiktionaryExtract") {
    group = "application"
    description = "Выписывает сербохорватские разделы из выгрузки викисловаря в .data/wiktionary"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "org.mpashka.vocabulary.importer.WiktionaryExtract"
    maxHeapSize = "1g"
}

// Сверка ударений викисловаря с нашей базой (этап 6, задача lev-79).
tasks.register<JavaExec>("runWiktionaryAccents") {
    group = "application"
    description = "Сверяет ударения викисловаря с выписанными в старом словаре и проверяет гипотезу о тильде"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "org.mpashka.vocabulary.importer.WiktionaryAccentReport"
    maxHeapSize = "2g"
}

// Запись ударений викисловаря в базу — после переноса (этап 6, задача lev-79).
tasks.register<JavaExec>("wiktionaryAccents") {
    group = "application"
    description = "Записывает ударения викисловаря в базу; расхождения — в discrepancy"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "org.mpashka.vocabulary.importer.WiktionaryAccents"
    maxHeapSize = "2g"
    mustRunAfter("migrate", "wiktionaryPartsOfSpeech")
}

// Часть речи из викисловаря словам, у которых правила её не определили — до записи ударений.
tasks.register<JavaExec>("wiktionaryPartsOfSpeech") {
    group = "application"
    description = "Определяет часть речи по викисловарю там, где правила не справились"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "org.mpashka.vocabulary.importer.WiktionaryPartsOfSpeech"
    maxHeapSize = "2g"
    mustRunAfter("migrate")
}
