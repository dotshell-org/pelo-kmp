package eu.dotshell.pelo.generic.data.models.realtime.alerts.community
import kotlinx.serialization.Serializable

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName

/**
 * Represents alerts grouped by karma threshold status for a stop
 */
@Immutable
@Serializable
data class StopAlertsStatus(
    @SerialName("karma_below_threshold")
    val karmaBelowThreshold: List<UserStopAlert> = emptyList(),

    @SerialName("karma_at_or_above_threshold")
    val karmaAtOrAboveThreshold: List<UserStopAlert> = emptyList()
) {
    /**
     * Returns true if this stop has alerts at or above the threshold (problematic stop)
     */
    fun hasProblematicAlerts(): Boolean = karmaAtOrAboveThreshold.isNotEmpty()

    /**
     * Returns all alerts (both below and above threshold)
     */
    fun allAlerts(): List<UserStopAlert> = karmaBelowThreshold + karmaAtOrAboveThreshold
}
