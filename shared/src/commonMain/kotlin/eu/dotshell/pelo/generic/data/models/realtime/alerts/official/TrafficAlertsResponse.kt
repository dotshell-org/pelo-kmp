package eu.dotshell.pelo.generic.data.models.realtime.alerts.official
import kotlinx.serialization.Serializable

import kotlinx.serialization.SerialName

/**
 * Represents the API response for traffic alerts
 */
@Serializable
data class TrafficAlertsResponse(
    @SerialName("success")
    val success: Boolean,

    @SerialName("data")
    val alerts: List<TrafficAlert>,

    @SerialName("timestamp")
    val timestamp: String,

    @SerialName("lastUpdated")
    val lastUpdated: String
)
