package eu.dotshell.pelo.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Captures a Baseline Profile for Pelo's cold start and its first few destinations.
 *
 * Run on a connected device/emulator (API 28+, 33+ recommended):
 * ```
 * ./gradlew :app:generateBaselineProfile
 * ```
 * The captured `baseline-prof.txt` is written under `app/src/<variant>/generated/baselineProfiles`,
 * packaged into the release build, and applied by ProfileInstaller on first launch so these paths
 * are AOT-compiled instead of JIT-warmed — cutting cold-start time and first-frame jank.
 *
 * ## Before running
 *
 * **Open the app by hand once on the target device and accept the terms.** The consent gate is
 * shown until accepted, its button label comes from `config.json`, and it only enables after a
 * checkbox is ticked — driving that from here would be brittle. With consent already granted the
 * gate composes nothing and the journey below reaches the real screens.
 *
 * ## Why more than a cold start
 *
 * The previous journey stopped at the first frame. The profile it produced had three thousand
 * MapLibre entries and not one for the lines sheet, a line's details, the itinerary sheet or
 * settings — so every one of those was JIT-warmed on first use, which is exactly when the user is
 * watching. The steps below walk the destinations reachable by a stable, localisation-independent
 * selector.
 *
 * Keep it short and deterministic: this runs several times per capture, and anything that can hang
 * lengthens every iteration. Each step is best-effort — a selector that finds nothing is skipped
 * rather than failing the capture, because a partial profile is worth more than none.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE_NAME) {
        // Cold launch from the launcher to the first interactive frame (the map scaffold).
        pressHome()
        startActivityAndWait()
        // Let the initial composition / map settle so its classes and methods are exercised.
        device.waitForIdle()

        // The lines sheet: category building, the chip rows, and the icon decoding behind them.
        if (tapTab(LINES_TAB)) {
            device.waitForIdle()
            tapTab(PLAN_TAB)
            device.waitForIdle()
        }

        // Settings: the screen itself plus the dataset-info read behind its timetable row.
        if (tapTab(SETTINGS_TAB)) {
            device.waitForIdle()
            tapTab(PLAN_TAB)
            device.waitForIdle()
        }
    }

    /**
     * Taps a bottom-navigation tab by content description, trying each locale's wording in turn.
     *
     * The descriptions are translated (`tab_lines_cd` is "Onglet Lignes" or "Lines tab"), and the
     * device language is not ours to choose, so both are tried. Returns false when neither is on
     * screen, leaving the caller to skip that leg.
     */
    private fun MacrobenchmarkScope.tapTab(descriptions: List<String>): Boolean {
        for (description in descriptions) {
            val target = device.wait(Until.findObject(By.desc(description)), FIND_TIMEOUT_MS)
            if (target != null) {
                target.click()
                return true
            }
        }
        return false
    }

    private companion object {
        const val PACKAGE_NAME = "eu.dotshell.pelo"

        /** Short: these are looked up on screens where the tab bar is already drawn. */
        const val FIND_TIMEOUT_MS = 2_000L

        // Keep in sync with tab_plan_cd / tab_lines_cd / tab_settings_cd in the two strings.xml.
        val PLAN_TAB = listOf("Onglet Plan", "Map tab")
        val LINES_TAB = listOf("Onglet Lignes", "Lines tab")
        val SETTINGS_TAB = listOf("Onglet Paramètres", "Settings tab")
    }
}
