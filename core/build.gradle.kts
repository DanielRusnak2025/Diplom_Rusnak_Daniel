plugins {
    `java-library`
    application
}

application {
    mainClass.set("org.example.HeadlessMain")
    applicationDefaultJvmArgs = listOf(
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8"
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

    // JSON (AI response parsing)
    implementation(libs.jackson.databind)

    // HTML parsing (metadata extraction)
    implementation(libs.jsoup)

    // Language detection (offline, no AI)
    implementation(libs.lingua)

    // Logging
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)
}
