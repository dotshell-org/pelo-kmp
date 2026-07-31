package eu.dotshell.pelo.generic.data.models.realtime.alerts.community

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a single user stop alert with karma status
 * Used for identifying problematic stops that should be avoided in route planning
 */
@Immutable
@Serializable
data class UserStopAlert(
    @SerialName("id")
    val id: String,

    @SerialName("stopId")
    val stopId: String,

    @SerialName("type")
    val type: String, // e.g., "crowding", "incident", etc.

    @SerialName("karma")
    val karma: Int,

    @SerialName("createdAt")
    val createdAt: String
)
