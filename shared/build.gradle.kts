import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Everything the app *is*, minus its Android entry points.
 *
 * The Compose UI lives here, in commonMain, because it is shared with iOS: `App()` is hosted by
 * MainActivity on one side and MainViewController on the other. Only the pieces Android needs to
 * boot — Application, Activity, the foreground service, the manifest and res/ — stay in :app.
 *
 * The split exists because the Kotlin Multiplatform plugin stopped being compatible with
 * 'com.android.application' in AGP 9 (a warning today, an error in AGP 10). A library module is
 * the supported host for the KMP plugin.
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    // AGP 9 also deprecates this pairing and points at 'com.android.kotlin.multiplatform.library'
    // instead — but that plugin is unusable here: AGP 9.0.0 and the Kotlin 2.3.10 multiplatform
    // plugin are binary-incompatible on it (KotlinMultiplatformAndroidComponentsExtension.onVariant
    // is missing at runtime). Revisit when the AGP/KGP pair catches up; the module split below is
    // what actually removes the AGP 10 blocker.
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    compilerOptions {
        // expect/actual classes are still flagged "Beta"; this project relies on them
        // intentionally (Settings, FileSystem, LocationProvider, …). Opt in to silence the warning.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
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

            // JetBrains lifecycle (Compose Multiplatform) — provides androidx.lifecycle.ViewModel
            // + viewModelScope in commonMain (same package as the Android artifact).
            implementation(libs.jetbrains.lifecycle.viewmodel)

            // Ktor (replaces Retrofit/OkHttp/Gson)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)

            // Kotlinx
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)

            // okio — cross-platform file IO + gzip (replaces java.io + java.util.zip)
            implementation(libs.okio)

            // maplibre-compose — Compose Multiplatform map (probe: verifying toolchain compatibility)
            implementation(libs.maplibre.compose)

            // Raptor-KT
            implementation(libs.raptor.kt)
        }

        androidMain.dependencies {
            implementation(compose.preview)

            // Ktor engine for Android (uses OkHttp under the hood)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.okhttp)

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
        }

        androidMain {
            kotlin.exclude("eu/dotshell/pelo/generic/data/models/**")
            kotlin.exclude("eu/dotshell/pelo/specific/data/model/**")
        }

        iosMain.dependencies {
            // Ktor engine for iOS
            implementation(libs.ktor.client.darwin)
        }

        androidUnitTest.dependencies {
            implementation(libs.junit)
        }
    }
}

android {
    // Deliberately not eu.dotshell.pelo: that belongs to :app, and two modules cannot share a
    // namespace. Nothing here reads resources, so the R class this generates goes unused.
    namespace = "eu.dotshell.pelo.shared"
    compileSdk = 36

    testOptions {
        unitTests {
            // Let android.util.Log (and other android.* stubs) return defaults instead
            // of throwing "not mocked" in plain JVM unit tests, so common code that logs
            // can be exercised without an instrumented device.
            isReturnDefaultValues = true
        }
    }

    defaultConfig {
        minSdk = 24
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

compose.resources {
    publicResClass = true
    packageOfResClass = "eu.dotshell.pelo.resources"
    generateResClass = always
}

// Compose compiler stability/skippability reports — off by default (they add build overhead).
// Enable to hunt recomposition hotspots; output lands in shared/build/compose_compiler/:
//   ./gradlew :shared:assembleDebug -PcomposeCompilerReports=true
composeCompiler {
    if (project.findProperty("composeCompilerReports") == "true") {
        val dir = layout.buildDirectory.dir("compose_compiler")
        reportsDestination.set(dir)
        metricsDestination.set(dir)
    }
}

