package eu.dotshell.pelo.specific.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Lyon-specific FeatureCollection model
 */
@Immutable
@Serializable
data class LyonFeatureCollection(
    val type: String = "FeatureCollection",
    val features: List<LyonFeature> = emptyList(),
    val totalFeatures: Int? = null,
    val numberMatched: Int? = null,
    val numberReturned: Int? = null,
    val timeStamp: String? = null,
    val crs: LyonCRS? = null,
    val bbox: List<Double>? = null
)
