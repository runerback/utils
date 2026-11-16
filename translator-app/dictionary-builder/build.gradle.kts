// Build-time tool: turns the Yomitan zips in the repo-root `dictionaries/` folder (git-ignored,
// supplied by whoever builds the app) into a prebuilt SQLite database that the app ships as an
// asset. Runs on the desktop during the build, so the app needs no import step.
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
    implementation(project(":subread-dictionary-core"))
    implementation("org.xerial:sqlite-jdbc:3.47.0.0")
}
