// Spring Boot: поиск по словоформам, REST API для фронтенда, пополнение через LLM.
plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dep.mgmt)
}

dependencies {
    implementation(project(":core"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")

    // /actuator/health — по нему живость приложения проверяют контейнер, роль выкладки
    // и внешняя проверка. Наружу открыт только health (умолчание стартера).
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Схема ведётся миграциями: db/migration. Замысел — docs/implementation/db-schema.md.
    // Нужен именно стартер: в Spring Boot 4 автонастройки разнесены по модулям,
    // и одного flyway-core не хватает — миграции молча не применяются.
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly(rootProject.libs.postgresql)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

// Выкладка идёт одним артефактом: оболочка (Vue) собирается и уезжает в jar статикой.
// В разработке она не нужна — там страницу отдаёт `npm run dev`, поэтому сборка привязана
// к bootJar, а не к processResources: `bootRun` остаётся быстрым и не требует node.
val frontendDir = rootProject.layout.projectDirectory.dir("frontend")

val installFrontend by tasks.registering(Exec::class) {
    workingDir = frontendDir.asFile
    commandLine("npm", "ci", "--no-audit", "--no-fund")
    inputs.file(frontendDir.file("package-lock.json"))
    outputs.dir(frontendDir.dir("node_modules"))
}

val buildFrontend by tasks.registering(Exec::class) {
    dependsOn(installFrontend)
    workingDir = frontendDir.asFile
    commandLine("npm", "run", "build")
    inputs.dir(frontendDir.dir("src"))
    inputs.file(frontendDir.file("index.html"))
    inputs.file(frontendDir.file("vite.config.js"))
    inputs.file(frontendDir.file("package.json"))
    outputs.dir(frontendDir.dir("dist"))
}

tasks.bootJar {
    dependsOn(buildFrontend)
    from(frontendDir.dir("dist")) { into("BOOT-INF/classes/static") }
}
