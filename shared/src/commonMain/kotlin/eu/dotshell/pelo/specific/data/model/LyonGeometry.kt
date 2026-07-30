package eu.dotshell.pelo.specific.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Lyon-specific Geometry model
 */
@Immutable
@Serializable
data class LyonGeometry(
    val type: String = "MultiLineString",
    val coordinates: List<List<List<Double>>> = emptyList()
)
