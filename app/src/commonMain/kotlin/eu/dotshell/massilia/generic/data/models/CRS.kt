package eu.dotshell.massilia.generic.data.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Coordinates System
 */
@Immutable
@Serializable
data class CRS(
    val type: String = "",
    val properties: CRSProperties = CRSProperties()
)
