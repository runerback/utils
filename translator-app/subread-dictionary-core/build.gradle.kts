// Vendored copy of the `:core` module of SubRead Dictionary
// (https://github.com/equwal/subread-dictionary), AGPL-3.0, see LICENSE in this folder.
// Plain Kotlin/JVM on purpose, like upstream: no Android types in here.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Streaming JSON reader for the term banks of a dictionary. A term bank can be many
    // megabytes; the stream keeps one term in memory at a time.
    implementation("com.google.code.gson:gson:2.13.1")

    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
}
