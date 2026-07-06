package eu.dotshell.massilia.generic.data.models.stops

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Geometry of a stop (Point)
 */
@Immutable
@Serializable
data class StopGeometry(
    val type: String = "Point",
    val coordinates: List<Double> = emptyList()
)
