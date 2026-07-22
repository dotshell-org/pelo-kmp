package eu.dotshell.pelo.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures Pelo's cold-start time so the Baseline Profile win can be quantified.
 *
 * Run on a connected device/emulator:
 * ```
 * ./gradlew :app:benchmarkReleaseAndroidTest
 * ```
 * Compare [startupNoCompilation] (no AOT) against [startupBaselineProfile] (profile applied): the
 * drop in `timeToInitialDisplay` is the profile's contribution to startup.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    /** Baseline: nothing pre-compiled — the JIT warms every path on the fly. */
    @Test
    fun startupNoCompilation() = startup(CompilationMode.None())

    /** With the packaged Baseline Profile applied (partial AOT of the profiled paths). */
    @Test
    fun startupBaselineProfile() = startup(CompilationMode.Partial())

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = "eu.dotshell.pelo",
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 10,
    ) {
        pressHome()
        startActivityAndWait()
    }
}
