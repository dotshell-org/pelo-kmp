package eu.dotshell.pelo.specific.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Lyon-specific traffic alert model that matches the Lyon API response structure
 * Contains Lyon-specific field names like ligne_cli, ligne_com, etc.
 */
@Immutable
@Serializable
data class LyonTrafficAlert(
    @SerialName("cause")
    val cause: String = "",

    @SerialName("debut")
    val startDate: String = "",

    @SerialName("fin")
    val endDate: String = "",

    @SerialName("last_update_fme")
    val lastUpdate: String = "",

    @SerialName("ligne_cli")
    val lineCode: String = "",

    @SerialName("ligne_com")
    val lineName: String = "",

    @SerialName("listeobjet")
    val objectList: String = "",

    @SerialName("message")
    val message: String = "",

    @SerialName("mode")
    val mode: String = "",

    @SerialName("n")
    val alertNumber: Int = 0,

    @SerialName("niveauseverite")
    val severityLevel: Int = 0,

    @SerialName("titre")
    val title: String = "",

    @SerialName("type")
    val alertType: String = "",

    @SerialName("typeobjet")
    val objectType: String = "",

    @SerialName("typeseverite")
    val severityType: String = ""
)
