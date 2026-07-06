package eu.dotshell.massilia.generic.data.models.realtime.alerts.official

/**
 * Enumeration of alert severity types
 */
enum class AlertSeverity(val level: Int, val color: Long) {
    SIGNIFICANT_DELAYS(20, 0xFFFF5722), // Orange
    OTHER_EFFECT(30, 0xFF2196F3), // Blue
    INFORMATION(40, 0xFF4CAF50), // Green
    UNKNOWN(0, 0xFF9E9E9E); // Gray

    companion object {
        fun fromSeverityType(severityType: String, severityLevel: Int): AlertSeverity {
            return when (severityType) {
                "SIGNIFICANT_DELAYS" -> SIGNIFICANT_DELAYS
                "OTHER_EFFECT" -> OTHER_EFFECT
                "INFORMATION" -> INFORMATION
                else -> entries.firstOrNull { it.level == severityLevel } ?: UNKNOWN
            }
        }
    }
}
