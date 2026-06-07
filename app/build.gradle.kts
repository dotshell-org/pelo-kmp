import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.materialIconsExtended)

            // Ktor (replaces Retrofit/OkHttp/Gson)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)

            // Kotlinx
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)

            // Raptor-KT
            implementation(libs.raptor.kt)
        }

        androidMain.dependencies {
            implementation(compose.preview)

            // Ktor engine for Android
            implementation(libs.ktor.client.okhttp)
            implementation(libs.retrofit)
            implementation(libs.retrofit.converter.gson)
            implementation(libs.gson)
            implementation(libs.okhttp)
            implementation(libs.okhttp.sse)

            // Android-specific
            implementation(libs.material)
            implementation(libs.androidx.compose.foundation.layout)
            implementation(libs.transport.runtime)
            implementation(libs.androidx.ui.graphics)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.lifecycle.runtime.ktx)
            implementation(libs.androidx.lifecycle.process)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.androidx.compose.material.icons.extended)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.compose.ui.geometry)
            implementation(libs.androidx.work.runtime.ktx)
            implementation(libs.google.play.services.location)
            implementation(libs.androidx.profileinstaller)
            implementation(libs.androidx.security.crypto)
            implementation(libs.kotlinx.coroutines.android)

            // MapLibre (Android-only)
            implementation(libs.maplibre.android)

            // YAML parsing for config.yml (Android-only, will use expect/actual later)
            implementation(libs.snakeyaml)
        }

        androidMain {
            kotlin.exclude("com/pelotcl/app/generic/data/models/**")
            kotlin.exclude("com/pelotcl/app/specific/data/model/**")
        }

        iosMain.dependencies {
            // Ktor engine for iOS
            implementation(libs.ktor.client.darwin)
        }
    }
}

android {
    signingConfigs {
        create("release") {
            storeFile = rootProject.file("pelotcl.jks")
            storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD") ?: ""
            keyAlias = "pelotcl-key"
            keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD") ?: ""
        }
    }
    namespace = "com.pelotcl.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pelotcl.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    lint {
        abortOnError = false
        warningsAsErrors = false
        checkAllWarnings = false
        disable += listOf(
            "LogNotTimber",
            "UnusedAttribute",
            "GradleDependency",
            "AndroidGradlePluginVersion"
        )
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

val cleanCompressedAssets = tasks.register<Delete>("cleanCompressedAssets") {
    delete(layout.buildDirectory.dir("intermediates/compressed_assets"))
}

tasks.matching { it.name.startsWith("compress") && it.name.endsWith("Assets") }
    .configureEach {
        dependsOn(cleanCompressedAssets)
    }
