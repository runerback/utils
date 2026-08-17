import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").inputStream().use { load(it) }
}

val buildNumber = (System.currentTimeMillis() / 1000).toInt()

android {
    namespace = "com.runerback.queuehelper"
    compileSdk = 35

    signingConfigs {
        create("release") {
            storeFile = file(localProperties.getProperty("RELEASE_STORE_FILE"))
            keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")
            storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD")
            keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")
        }
    }

    defaultConfig {
        applicationId = "com.runerback.queuehelper"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    applicationVariants.configureEach {
        outputs.configureEach {
            val outputImpl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val version = defaultConfig.versionName ?: "unknown"
            outputImpl.outputFileName = "queue-helper-${buildType.name}-${version}.apk"
        }
    }
}

afterEvaluate {
    tasks.matching { it.name.startsWith("assemble") }.configureEach {
        doLast {
            println("Built version: ${android.defaultConfig.versionName}")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
}
