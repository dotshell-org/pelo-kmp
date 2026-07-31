package eu.dotshell.pelo.generic.data.models.geojson

import androidx.compose.runtime.Immutable
import eu.dotshell.pelo.generic.data.models.CRS
import eu.dotshell.pelo.generic.data.models.geojson.StopFeature
import kotlinx.serialization.Serializable

/**
 * Represents a GeoJSON collection of transport stops
 */
@Immutable
@Serializable
data class StopCollection(
    val type: String = "FeatureCollection",
    val features: List<StopFeature> = emptyList(),
    val totalFeatures: Int? = null,
    val numberMatched: Int? = null,
    val numberReturned: Int? = null,
    val timeStamp: String? = null,
    val crs: CRS? = null,
    val bbox: List<Double>? = null
)
