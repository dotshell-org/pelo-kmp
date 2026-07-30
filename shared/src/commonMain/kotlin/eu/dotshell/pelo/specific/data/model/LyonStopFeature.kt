package eu.dotshell.pelo.specific.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Lyon-specific stop feature
 */
@Immutable
@Serializable
data class LyonStopFeature(
    val type: String = "Feature",
    val id: String = "",
    val geometry: LyonStopGeometry = LyonStopGeometry(),
    @SerialName("geometry_name")
    val geometryName: String? = null,
    val properties: LyonStopProperties = LyonStopProperties(),
    val bbox: List<Double>? = null
)
