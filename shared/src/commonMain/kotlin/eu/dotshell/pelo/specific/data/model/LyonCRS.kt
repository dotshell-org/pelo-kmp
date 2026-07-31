package eu.dotshell.pelo.specific.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Lyon-specific CRS model
 */
@Immutable
@Serializable
data class LyonCRS(
    val type: String = "",
    val properties: LyonCRSProperties = LyonCRSProperties()
)
