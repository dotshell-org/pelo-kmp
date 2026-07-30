package eu.dotshell.pelo.specific.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Lyon-specific transport line properties that match the Lyon API response structure
 * Contains Lyon-specific field names and serialized names
 */
@Immutable
@Serializable
data class LyonTransportLineProperties(
    @SerialName("ligne")
    val ligne: String = "",
    
    @SerialName("code_trace")
    val codeTrace: String = "",

    @SerialName("code_ligne")
    val codeLigne: String = "",

    @SerialName("type_trace")
    val typeTrace: String? = null,

    @SerialName("nom_trace")
    val nomTrace: String? = null,

    val sens: String? = null,

    val origine: String? = null,

    val destination: String? = null,

    @SerialName("nom_origine")
    val nomOrigine: String? = null,

    @SerialName("nom_destination")
    val nomDestination: String? = null,

    @SerialName("famille_transport")
    val familleTransport: String = "",

    @SerialName("date_debut")
    val dateDebut: String? = null,

    @SerialName("date_fin")
    val dateFin: String? = null,

    @SerialName("code_type_ligne")
    val codeTypeLigne: String? = null,

    @SerialName("nom_type_ligne")
    val nomTypeLigne: String? = null,

    @SerialName("code_tri_ligne")
    val codeTriLigne: String? = null,

    @SerialName("nom_version")
    val nomVersion: String? = null,

    @SerialName("last_update")
    val lastUpdate: String? = null,

    @SerialName("last_update_fme")
    val lastUpdateFme: String? = null,

    val gid: Int = 0,

    val couleur: String? = null
)
