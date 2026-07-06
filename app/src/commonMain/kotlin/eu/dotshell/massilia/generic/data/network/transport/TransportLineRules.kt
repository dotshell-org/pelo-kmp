package eu.dotshell.massilia.generic.data.network.transport

import eu.dotshell.massilia.generic.data.models.ui.VehicleMarkerType

/**
 * Rules for normalizing and matching transport line names.
 *
 * This can be network/city-specific (e.g. Rhônexpress -> RX, NAVI1 -> NAV1, regex patterns, etc.).
 */
interface TransportLineRules {
    /**
     * Normalizes a raw token coming from traffic alerts text/fields so it can be compared
     * against line names used in schedules / routing.
     */
    fun normalizeAlertToken(raw: String): String

    /**
     * Returns whether a token is likely to represent a line name.
     */
    fun isLikelyLineToken(token: String): Boolean

    /**
     * Canonical route name used to match schedules route names.
     */
    fun canonicalRouteName(raw: String): String

    /**
     * Returns equivalent route names to match against schedule data.
     * Example: "NAVI1" might match both "NAVI1" and "NAV1".
     */
    fun equivalentRouteNames(raw: String): List<String>

    /**
     * Normalizes a line name for comparison (e.g. live vehicle stream filtering).
     */
    fun normalizeForComparison(raw: String): String

    /**
     * Returns whether the line is considered a "strong" line (metro, tram, funicular, etc.).
     */
    fun isStrongLine(lineName: String): Boolean

    /**
     * Returns the transport type category (e.g., "Métro", "Bus").
     */
    fun getTransportType(lineName: String): String

    /**
     * Returns the mode icon name for the line, or null if it's a strong line.
     */
    fun getModeIcon(lineName: String): String?

    /**
     * Returns whether the line is a navigone (ferry) line.
     */
    fun isNavigoneLine(lineName: String): Boolean

    /**
     * Returns whether live vehicle tracking is available for this line.
     */
    fun isLiveTrackableLine(lineName: String): Boolean

    /**
     * Returns the vehicle marker type for rendering on the map.
     */
    fun getVehicleMarkerType(lineName: String): VehicleMarkerType

    /**
     * Normalizes a line name for UI display (e.g., "NAV1" -> "NAVI1").
     */
    fun normalizeLineNameForUi(lineName: String): String

    /**
     * Sorts a list of line names in a city-specific display order.
     */
    fun sortLines(lines: List<String>): List<String>
}

