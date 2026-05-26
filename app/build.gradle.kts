import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
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
    compileSdk {
        version = release(36)
    }

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
        isCoreLibraryDesugaringEnabled = true
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    buildFeatures {
        compose = true
    }

    lint {
        // Disable strict mode for now to allow compilation
        abortOnError = false
        warningsAsErrors = false
        checkAllWarnings = false
        
        // Ignore specific warnings that we consider acceptable
        disable += listOf(
            "LogNotTimber",
            "UnusedAttribute",
            "GradleDependency",
            "AndroidGradlePluginVersion"
        )
    }
}

val cleanCompressedAssets = tasks.register<Delete>("cleanCompressedAssets") {
    // Avoid stale or corrupted compressed_assets outputs between builds.
    delete(layout.buildDirectory.dir("intermediates/compressed_assets"))
}

tasks.matching { it.name.startsWith("compress") && it.name.endsWith("Assets") }
    .configureEach {
        dependsOn(cleanCompressedAssets)
    }

dependencies {
    implementation(libs.material)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.transport.runtime)
    implementation(libs.androidx.ui.graphics)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // Foundation for TextFieldState (text2) APIs
    implementation(libs.androidx.compose.foundation)

    // Navigation and Material Icons
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)

    // MapLibre
    implementation(libs.maplibre.android)

    // Location Services
    implementation(libs.google.play.services.location)

    // Retrofit for network calls
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.gson)
    
    // OkHttp for caching and network optimization
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)

    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.ui.geometry)

    // Raptor-KT
    implementation(libs.raptor.kt)

    // Kotlinx Serialization for fast JSON caching
    implementation(libs.kotlinx.serialization.json)

    // YAML parsing for config.yml
    implementation(libs.snakeyaml)

    // Encrypted SharedPreferences for telemetry daily_id
    implementation(libs.androidx.security.crypto)

    // WorkManager for background tasks
    implementation(libs.androidx.work.runtime.ktx)

    // Jetpack Glance for home screen widgets
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // ProfileInstaller for Baseline Profiles (improves cold start by ~15-30%)
    implementation(libs.androidx.profileinstaller)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
