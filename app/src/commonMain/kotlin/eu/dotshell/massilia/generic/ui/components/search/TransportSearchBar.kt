package eu.dotshell.massilia.generic.ui.components.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import eu.dotshell.massilia.generic.data.models.search.LineSearchResult
import eu.dotshell.massilia.generic.data.models.search.StationSearchResult
import eu.dotshell.massilia.generic.data.models.search.TransportSearchContent
import eu.dotshell.massilia.generic.data.network.mapstyle.MapStyleData
import eu.dotshell.massilia.generic.data.repository.offline.mapstyle.MapStyleCompat
import eu.dotshell.massilia.generic.data.repository.offline.search.SearchHistoryItem
import eu.dotshell.massilia.generic.data.repository.offline.search.SearchType
import eu.dotshell.massilia.generic.ui.components.search.bar.SimpleSearchBar
import kotlinx.coroutines.delay

@Composable
fun TransportSearchBar(
    onSearchStops: suspend (String) -> List<StationSearchResult>,
    onSearchLines: suspend (String) -> List<LineSearchResult>,
    modifier: Modifier = Modifier,
    currentMapStyle: MapStyleData = MapStyleCompat.POSITRON,
    content: TransportSearchContent = TransportSearchContent.STOPS_AND_LINES,
    showHistory: Boolean = true,
    startExpanded: Boolean = false,
    showDarkOutline: Boolean? = null,
    searchPlaceholder: String? = null,
    query: String? = null,
    onQueryChange: ((String) -> Unit)? = null,
    minQueryLengthForResults: Int? = null,
    debounceMs: Long? = null,
    focusNonce: Int = 0,
    onExpandedChange: (Boolean) -> Unit = {},
    onStopPrimary: (StationSearchResult) -> Unit,
    onStopSecondary: (StationSearchResult) -> Unit = {},
    onLineSelected: (LineSearchResult) -> Unit = {},
    showDirections: Boolean = true,
    searchHistory: List<SearchHistoryItem> = emptyList(),
    onAddToHistory: (SearchHistoryItem) -> Unit = {},
    onRemoveFromHistory: (query: String, type: SearchType) -> Unit = { _, _ -> },
) {
    var uncontrolledQuery by remember { mutableStateOf("") }
    val isParentControlled = query != null && onQueryChange != null
    val effectiveQuery = if (isParentControlled) query else uncontrolledQuery
    val setEffectiveQuery: (String) -> Unit = { q ->
        if (isParentControlled) {
            onQueryChange.invoke(q)
        } else {
            uncontrolledQuery = q
        }
    }

    val resolvedStopMinLen = minQueryLengthForResults ?: 2
    val resolvedLineMinLen = 1
    val resolvedUiMinLen = if (content == TransportSearchContent.STOPS_ONLY) {
        resolvedStopMinLen
    } else {
        resolvedLineMinLen
    }
    val resolvedDebounce = debounceMs
        ?: if (content == TransportSearchContent.STOPS_ONLY && !showHistory) 250L else 300L

    val resolvedShowDarkOutline = showDarkOutline
        ?: (currentMapStyle.key == "dark_matter" && !startExpanded)

    var stationSearchResults by remember { mutableStateOf<List<StationSearchResult>>(emptyList()) }
    var lineSearchResults by remember { mutableStateOf<List<LineSearchResult>>(emptyList()) }

    LaunchedEffect(
        effectiveQuery,
        content,
        resolvedStopMinLen,
        resolvedLineMinLen,
        resolvedDebounce
    ) {
        val current = effectiveQuery.trim()
        if (current.isEmpty()) {
            stationSearchResults = emptyList()
            lineSearchResults = emptyList()
            return@LaunchedEffect
        }
        delay(resolvedDebounce)
        if (current != effectiveQuery.trim()) return@LaunchedEffect

        stationSearchResults = if (
            content != TransportSearchContent.LINES_ONLY &&
            current.length >= resolvedStopMinLen
        ) {
            onSearchStops(current)
        } else {
            emptyList()
        }
        lineSearchResults = if (
            content != TransportSearchContent.STOPS_ONLY &&
            current.length >= resolvedLineMinLen
        ) {
            onSearchLines(current)
        } else {
            emptyList()
        }
    }

    fun clearQuery() {
        setEffectiveQuery("")
        stationSearchResults = emptyList()
        lineSearchResults = emptyList()
    }

    SimpleSearchBar(
        modifier = modifier,
        searchResults = stationSearchResults,
        lineSearchResults = lineSearchResults,
        searchHistory = if (showHistory) searchHistory else emptyList(),
        onQueryChange = { q -> setEffectiveQuery(q) },
        externalQuery = effectiveQuery,
        externalOnQueryChange = setEffectiveQuery,
        onSearch = { stop ->
            if (showHistory) onAddToHistory(SearchHistoryItem(stop.stopName, SearchType.STOP, stop.lines))
            onStopPrimary(stop)
            clearQuery()
        },
        onLineSearch = { line ->
            if (showHistory) onAddToHistory(SearchHistoryItem(line.lineName, SearchType.LINE))
            onLineSelected(line)
            clearQuery()
        },
        onHistoryItemClick = { historyItem ->
            if (historyItem.type == SearchType.LINE) {
                onLineSelected(LineSearchResult(historyItem.query))
            } else {
                onStopPrimary(
                    StationSearchResult(
                        stopName = historyItem.query,
                        lines = historyItem.lines
                    )
                )
            }
            clearQuery()
        },
        onHistoryItemRemove = { historyItem ->
            onRemoveFromHistory(historyItem.query, historyItem.type)
        },
        showDarkOutline = resolvedShowDarkOutline,
        onExpandedChange = onExpandedChange,
        onStopOptionsClick = { stop ->
            if (showHistory) onAddToHistory(SearchHistoryItem(stop.stopName, SearchType.STOP, stop.lines))
            onStopSecondary(stop)
        },
        onHistoryItemOptionsClick = { historyItem ->
            if (historyItem.type == SearchType.STOP) {
                onStopSecondary(
                    StationSearchResult(
                        stopName = historyItem.query,
                        lines = historyItem.lines
                    )
                )
            }
        },
        content = content,
        showHistory = showHistory,
        startExpanded = startExpanded,
        searchPlaceholder = searchPlaceholder,
        focusNonce = focusNonce,
        minQueryLengthForResults = resolvedUiMinLen,
        showDirections = showDirections
    )
}
