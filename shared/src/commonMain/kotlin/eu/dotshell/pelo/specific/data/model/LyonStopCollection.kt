package eu.dotshell.pelo.specific.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Lyon-specific stop collection
 */
@Immutable
@Serializable
data class LyonStopCollection(
    val type: String = "FeatureCollection",
    val features: List<LyonStopFeature> = emptyList(),
    val totalFeatures: Int? = null,
    val numberMatched: Int? = null,
    val numberReturned: Int? = null,
    val timeStamp: String? = null,
    val crs: LyonCRS? = null,
    val bbox: List<Double>? = null
)
