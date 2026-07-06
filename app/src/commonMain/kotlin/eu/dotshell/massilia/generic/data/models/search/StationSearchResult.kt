package eu.dotshell.massilia.generic.data.models.search

import androidx.compose.runtime.Immutable

@Immutable
data class StationSearchResult(
    val stopName: String,
    val lines: List<String>,
    val stopId: Int? = null
)
