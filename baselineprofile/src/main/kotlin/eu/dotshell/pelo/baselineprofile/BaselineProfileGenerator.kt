package eu.dotshell.pelo.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Captures a Baseline Profile for Pelo's cold-start critical path.
 *
 * Run on a connected device/emulator (API 28+, 33+ recommended):
 * ```
 * ./gradlew :app:generateBaselineProfile
 * ```
 * The captured `baseline-prof.txt` is written under `app/src/<variant>/generated/baselineProfiles`,
 * packaged into the release build, and applied by ProfileInstaller on first launch so these paths
 * are AOT-compiled instead of JIT-warmed — cutting cold-start time and first-frame jank.
 *
 * Keep the journey short and deterministic: the more faithfully it mirrors a real cold start, the
 * better the profile. Re-run whenever the startup path changes materially.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = "eu.dotshell.pelo") {
        // Cold launch from the launcher to the first interactive frame (the map scaffold).
        pressHome()
        startActivityAndWait()
        // Let the initial composition / map settle so its classes and methods are exercised.
        device.waitForIdle()
    }
}
