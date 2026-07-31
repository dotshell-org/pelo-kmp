package eu.dotshell.pelo.specific.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Lyon-specific Feature model that matches the Lyon API response structure
 */
@Immutable
@Serializable
data class LyonFeature(
    val type: String = "Feature",
    val id: String = "",
    val geometry: LyonGeometry = LyonGeometry(),
    @SerialName("geometry_name")
    val geometryName: String? = null,
    val properties: LyonTransportLineProperties = LyonTransportLineProperties(),
    val bbox: List<Double>? = null
)
