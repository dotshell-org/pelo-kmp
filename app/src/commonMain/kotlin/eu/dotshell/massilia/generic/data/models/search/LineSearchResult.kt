package eu.dotshell.massilia.generic.data.models.search

import androidx.compose.runtime.Immutable

@Immutable
data class LineSearchResult(
    val lineName: String,
    val category: String = ""
)
