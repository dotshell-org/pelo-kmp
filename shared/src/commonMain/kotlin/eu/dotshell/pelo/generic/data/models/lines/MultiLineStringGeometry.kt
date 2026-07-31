package eu.dotshell.pelo.generic.data.models.lines

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Represents a MultiLineString geometry
 */
@Immutable
@Serializable
data class MultiLineStringGeometry(
    val type: String = "MultiLineString",
    val coordinates: List<List<List<Double>>> = emptyList()
)
