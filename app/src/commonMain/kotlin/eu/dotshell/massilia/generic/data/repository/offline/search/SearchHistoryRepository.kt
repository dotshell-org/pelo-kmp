package eu.dotshell.massilia.generic.data.repository.offline.search

import eu.dotshell.massilia.generic.data.telemetry.TelemetryEmitter
import eu.dotshell.massilia.generic.data.telemetry.TelemetryEvent
import eu.dotshell.massilia.platform.PlatformContext
import eu.dotshell.massilia.platform.Settings
import eu.dotshell.massilia.platform.randomId
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Repository for managing search history.
 * Stores recent searches for quick access.
 * Multiplatform: uses [Settings] abstraction + kotlinx.serialization instead of
 * SharedPreferences + Gson.
 */
class SearchHistoryRepository(context: PlatformContext) {
    private val settings = Settings(context, "massilia_search_history")
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    companion object {
        private const val KEY_SEARCH_HISTORY = "search_history"
        private const val MAX_HISTORY_SIZE = 10
    }

    /**
     * Get the search history ordered by most recent first.
     */
    fun getSearchHistory(): List<SearchHistoryItem> {
        val raw = settings.getString(KEY_SEARCH_HISTORY, "") .takeIf { it.isNotBlank() }
            ?: return emptyList()
        return try {
            json.decodeFromString<List<SearchHistoryItem>>(raw)
                .sortedByDescending { it.timestamp }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Add a search item to history. If the item already exists, it updates its timestamp.
     * Keeps only the most recent [MAX_HISTORY_SIZE] items.
     */
    fun addToHistory(item: SearchHistoryItem) {
        val history = getSearchHistory().toMutableList()

        // Remove existing entry with same query and type (case-insensitive)
        history.removeAll {
            it.query.equals(item.query, ignoreCase = true) && it.type == item.type
        }

        // Add new item at the beginning with current timestamp
        val nowMs = Clock.System.now().toEpochMilliseconds()
        history.add(0, item.copy(timestamp = nowMs))

        // Keep only MAX_HISTORY_SIZE items
        val trimmedHistory = history.take(MAX_HISTORY_SIZE)

        settings.putString(KEY_SEARCH_HISTORY, json.encodeToString(trimmedHistory))

        emitTelemetry(item)
    }

    /**
     * Remove a specific item from history.
     */
    fun removeFromHistory(query: String, type: SearchType) {
        val history = getSearchHistory().toMutableList()
        history.removeAll {
            it.query.equals(query, ignoreCase = true) && it.type == type
        }
        settings.putString(KEY_SEARCH_HISTORY, json.encodeToString(history))
    }

    private fun emitTelemetry(item: SearchHistoryItem) {
        // The query is the canonical stop name or line id selected by the user from a result.
        // It is *not* free text — it always matches a known GTFS resource.
        val now = Clock.System.now().toString()
        val event = when (item.type) {
            SearchType.STOP -> TelemetryEvent.SearchStop(
                eventId = randomId(),
                at = now,
                stopId = item.query
            )
            SearchType.LINE -> TelemetryEvent.SearchLine(
                eventId = randomId(),
                at = now,
                lineId = item.query
            )
        }
        TelemetryEmitter.emit(event)
    }
}
