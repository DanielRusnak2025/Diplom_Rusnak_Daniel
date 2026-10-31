plugins {
    `java-library`
    application
}

application {
    mainClass.set("org.example.HeadlessMain")
    applicationDefaultJvmArgs = listOf(
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8",
        "--enable-native-access=ALL-UNNAMED"
    )
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

dependencies {
    // ORM
    implementation(libs.hibernate.core)

    // JDBC driver — runtime only
    runtimeOnly(libs.mysql.connector.j)

    // Email (Gmail IMAP)
    implementation(libs.jakarta.mail)

    // HTTP client (Ollama calls)
    implementation(libs.okhttp)

    // Jackson BOM — aligns all jackson-* modules to one version
    implementation(platform(libs.jackson.bom))

    // JSON (AI response parsing)
    implementation(libs.jackson.databind)

    // YAML parsing (Flyway Java migrations that read taxonomy.yaml)
    implementation(libs.jackson.dataformat.yaml)

    // HTML parsing (metadata extraction)
    implementation(libs.jsoup)

    // Language detection (offline, no AI)
    implementation(libs.lingua)

    // Logging
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)

    // Flyway schema migrations
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.mysql)

    // Password hashing (Argon2id, RFC 9106)
    implementation(libs.argon2.jvm)

    // Tests
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
