package eu.dotshell.massilia.generic.data.repository.offline.search

import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

/**
 * Model for a search history item that can be either a stop or a line
 */
@Serializable
data class SearchHistoryItem(
    val query: String,
    val type: SearchType,
    val lines: List<String> = emptyList(), // For stops: the lines serving the stop; For lines: empty
    val timestamp: Long = Clock.System.now().toEpochMilliseconds()
)
