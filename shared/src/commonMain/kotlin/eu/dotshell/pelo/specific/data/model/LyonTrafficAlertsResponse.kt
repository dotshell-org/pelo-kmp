package eu.dotshell.pelo.specific.data.model
import kotlinx.serialization.Serializable

import kotlinx.serialization.SerialName

/**
 * Lyon-specific API response for traffic alerts
 */
@Serializable
data class LyonTrafficAlertsResponse(
    @SerialName("success")
    val success: Boolean = false,

    @SerialName("data")
    val alerts: List<LyonTrafficAlert> = emptyList(),

    @SerialName("timestamp")
    val timestamp: String = "",

    @SerialName("lastUpdated")
    val lastUpdated: String = ""
)
