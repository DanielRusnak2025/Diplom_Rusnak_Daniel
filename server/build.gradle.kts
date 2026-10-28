plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

application {
    mainClass.set("org.example.ServerMainKt")
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":shared"))
    implementation(libs.kotlinx.serialization.json)
}
