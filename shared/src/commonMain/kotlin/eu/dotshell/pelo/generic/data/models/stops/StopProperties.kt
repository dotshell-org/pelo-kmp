package eu.dotshell.pelo.generic.data.models.stops

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Properties of a stop
 */
@Immutable
@Serializable
data class StopProperties(
    val id: Int = 0,
    val nom: String = "",
    val desserte: String = "", // Lines serving this stop (e.g. "C:A", "M:A:B")
    val ascenseur: Boolean = false,
    val escalator: Boolean = false,
    val gid: Int = 0,
    @SerialName("last_update")
    val lastUpdate: String? = null,
    @SerialName("last_update_fme")
    val lastUpdateFme: String? = null,
    val adresse: String? = null,
    @SerialName("localise_face_a_adresse")
    val localiseFaceAAdresse: Boolean = false,
    val commune: String? = null,
    val insee: String? = null,
    val zone: String? = null
)
