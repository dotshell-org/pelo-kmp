package eu.dotshell.pelo.generic.data.repository.offline.search

import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

/**
 * Model for a search history item: a stop, a line, or a geocoded address/POI.
 * Address entries carry their coordinates so they can be re-selected without re-geocoding.
 */
@Serializable
data class SearchHistoryItem(
    val query: String,
    val type: SearchType,
    val lines: List<String> = emptyList(), // For stops: the lines serving the stop; For lines: empty
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val lat: Double? = null,               // ADDRESS only
    val lon: Double? = null,               // ADDRESS only
    val detail: String? = null             // ADDRESS only: secondary address line
)
