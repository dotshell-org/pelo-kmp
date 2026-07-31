package eu.dotshell.pelo.generic.data.models.geojson

import androidx.compose.runtime.Immutable
import eu.dotshell.pelo.generic.data.models.CRS
import kotlinx.serialization.Serializable

/**
 * Represents a GeoJSON FeatureCollection
 */
@Immutable
@Serializable
data class FeatureCollection(
    val type: String = "FeatureCollection",
    val features: List<Feature> = emptyList(),
    val totalFeatures: Int? = null,
    val numberMatched: Int? = null,
    val numberReturned: Int? = null,
    val timeStamp: String? = null,
    val crs: CRS? = null,
    val bbox: List<Double>? = null
)
