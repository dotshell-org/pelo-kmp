@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package eu.dotshell.pelo.generic.data.models.realtime.alerts.official

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * Represents a traffic alert for a line of transport
 * Generic model that can be used across different transport networks
 */
@Immutable
@Serializable
data class TrafficAlert(
    @SerialName("cause")
    val cause: String,

    @SerialName("startDate")
    @JsonNames("debut")
    val startDate: String,

    @SerialName("endDate")
    @JsonNames("fin")
    val endDate: String,

    @SerialName("lastUpdate")
    @JsonNames("last_update_fme")
    val lastUpdate: String,

    @SerialName("lineCode")
    @JsonNames("ligne_cli")
    val lineCode: String,

    @SerialName("lineName")
    @JsonNames("ligne_com")
    val lineName: String,

    @SerialName("objectList")
    @JsonNames("listeobjet")
    val objectList: String,

    @SerialName("message")
    val message: String,

    @SerialName("mode")
    val mode: String,

    @SerialName("alertNumber")
    @JsonNames("n")
    val alertNumber: Int,

    @SerialName("severityLevel")
    @JsonNames("niveauseverite")
    val severityLevel: Int,

    @SerialName("title")
    @JsonNames("titre")
    val title: String,

    @SerialName("alertType")
    @JsonNames("type")
    val alertType: String,

    @SerialName("objectType")
    @JsonNames("typeobjet")
    val objectType: String,

    @SerialName("severityType")
    @JsonNames("typeseverite")
    val severityType: String
)
