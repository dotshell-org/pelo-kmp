package eu.dotshell.massilia.generic.data.repository.itinerary.itinerary

import eu.dotshell.massilia.platform.PlatformContext
import eu.dotshell.massilia.platform.Settings

/**
 * Repository for managing itinerary routing preferences.
 * Stores user preferences for route filtering (school S lines, night N lines).
 * Multiplatform: uses [Settings] abstraction instead of SharedPreferences.
 */
class ItineraryPreferencesRepository(context: PlatformContext) {
    private val settings = Settings(context, "massilia_itinerary_prefs")

    private val keyEnableSchoolLines = "enable_school_lines"
    private val keyEnableNightLines = "enable_night_lines"

    /**
     * Check if school (S) lines should be included in routing.
     * Default: true (enabled)
     */
    fun isSchoolLinesEnabled(): Boolean = isOptionEnabled(keyEnableSchoolLines, true)

    /**
     * Enable or disable school (S) lines in routing.
     */
    fun setSchoolLinesEnabled(enabled: Boolean) = setOptionEnabled(keyEnableSchoolLines, enabled)

    /**
     * Check if night (N) lines should be included in routing.
     * Default: true (enabled)
     */
    fun isNightLinesEnabled(): Boolean = isOptionEnabled(keyEnableNightLines, true)

    /**
     * Enable or disable night (N) lines in routing.
     */
    fun setNightLinesEnabled(enabled: Boolean) = setOptionEnabled(keyEnableNightLines, enabled)

    /**
     * Generic getter for itinerary options.
     */
    fun isOptionEnabled(key: String, defaultValue: Boolean): Boolean {
        if (!settings.contains(key)) {
            settings.putBoolean(key, defaultValue)
        }
        return settings.getBoolean(key, defaultValue)
    }

    /**
     * Generic setter for itinerary options.
     */
    fun setOptionEnabled(key: String, enabled: Boolean) {
        settings.putBoolean(key, enabled)
    }
}