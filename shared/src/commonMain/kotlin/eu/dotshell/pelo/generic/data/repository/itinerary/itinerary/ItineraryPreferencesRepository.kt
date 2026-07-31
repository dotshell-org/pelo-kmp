package eu.dotshell.pelo.generic.data.repository.itinerary.itinerary

import eu.dotshell.pelo.platform.PlatformContext
import eu.dotshell.pelo.platform.Settings

/**
 * Repository for managing itinerary routing preferences.
 * Stores user preferences for route filtering (JD lines, RX line).
 * Multiplatform: uses [Settings] abstraction instead of SharedPreferences.
 */
class ItineraryPreferencesRepository(context: PlatformContext) {
    private val settings = Settings(context, "pelo_itinerary_prefs")

    private val keyEnableJDLines = "enable_jd_lines"
    private val keyEnableRXLine = "enable_rx_line"

    /**
     * Check if Junior Direct (JD) lines should be included in routing.
     * Default: true (enabled)
     */
    fun isJdLinesEnabled(): Boolean = isOptionEnabled(keyEnableJDLines, true)

    /**
     * Enable or disable Junior Direct (JD) lines in routing.
     */
    fun setJdLinesEnabled(enabled: Boolean) = setOptionEnabled(keyEnableJDLines, enabled)

    /**
     * Check if RhôneExpress (RX) line should be included in routing.
     * Default: true (enabled)
     */
    fun isRxLineEnabled(): Boolean = isOptionEnabled(keyEnableRXLine, true)

    /**
     * Enable or disable RhôneExpress (RX) line in routing.
     */
    fun setRxLineEnabled(enabled: Boolean) = setOptionEnabled(keyEnableRXLine, enabled)

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