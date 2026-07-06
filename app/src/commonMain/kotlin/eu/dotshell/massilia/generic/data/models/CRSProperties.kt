package eu.dotshell.massilia.generic.data.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class CRSProperties(
    val name: String = ""
)
