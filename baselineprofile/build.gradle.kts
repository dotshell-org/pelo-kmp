import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "eu.dotshell.pelo.baselineprofile"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    defaultConfig {
        // Baseline-profile capture needs API 28+ on the device (33+ gives the most reliable
        // results). This only constrains the test module, not the app (app minSdk stays 24).
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The application whose startup we profile / benchmark.
    targetProjectPath = ":app"
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

// Generate the profile on a connected device/emulator — the one you already run Pelo on:
//   ./gradlew :app:generateBaselineProfile
// For a reproducible headless run instead, declare a Gradle Managed Device (e.g. a Pixel 6 API 34
// "aosp" image) under android.testOptions.managedDevices, add its name here, and flip
// useConnectedDevices to false.
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
