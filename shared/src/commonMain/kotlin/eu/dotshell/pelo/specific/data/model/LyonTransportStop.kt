@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package eu.dotshell.pelo.specific.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * Lyon-specific transport stop properties
 */
@Immutable
@Serializable
data class LyonStopProperties(
    @SerialName("gid")
    val gid: Int = 0,
    
    @SerialName("id_arret")
    val stopId: String = "",
    
    @SerialName("nom_arret")
    @JsonNames("nom", "nom_arret")
    val stopName: String = "",
    
    @SerialName("code_arret")
    val stopCode: String = "",
    
    @SerialName("type_arret")
    val stopType: String = "",
    
    @SerialName("x")
    val x: Double = 0.0,
    
    @SerialName("y")
    val y: Double = 0.0,
    
    @SerialName("longitude")
    val longitude: Double = 0.0,
    
    @SerialName("latitude")
    val latitude: Double = 0.0,
    
    @SerialName("commune")
    val city: String = "",
    
    @SerialName("code_insee")
    @JsonNames("insee", "code_insee")
    val inseeCode: String = "",

    // Field returned by Lyon's WFS stops layer (used to know which line(s) serve each stop).
    // Format example: "C:A", "M:A:B", etc.
    @SerialName("desserte")
    val desserte: String? = null,

    // Some WFS layers expose the same data under a slightly different key.
    @SerialName("desserte_arret")
    val desserteArret: String? = null
)
