package eu.dotshell.pelo.specific.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Lyon-specific stop geometry
 */
@Immutable
@Serializable
data class LyonStopGeometry(
    val type: String = "Point",
    val coordinates: List<Double> = emptyList()
)
