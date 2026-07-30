package eu.dotshell.pelo.generic.ui.components.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import eu.dotshell.pelo.generic.data.models.search.AddressSearchResult
import eu.dotshell.pelo.generic.data.models.search.LineSearchResult
import eu.dotshell.pelo.generic.data.models.search.StationSearchResult
import eu.dotshell.pelo.generic.data.models.search.TransportSearchContent
import eu.dotshell.pelo.generic.data.network.mapstyle.MapStyleData
import eu.dotshell.pelo.generic.data.repository.offline.search.SearchHistoryItem
import eu.dotshell.pelo.generic.data.repository.offline.search.SearchType
import eu.dotshell.pelo.generic.ui.components.search.bar.SimpleSearchBar
import eu.dotshell.pelo.generic.utils.map.MapStyleUtils
import kotlinx.coroutines.delay

@Composable
fun TransportSearchBar(
    onSearchStops: suspend (String) -> List<StationSearchResult>,
    onSearchLines: suspend (String) -> List<LineSearchResult>,
    modifier: Modifier = Modifier,
    currentMapStyle: MapStyleData? = null,
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
    // Geocoded address/POI search: opt-in — call sites that don't provide a source never
    // show the address section
    onSearchAddresses: suspend (String) -> List<AddressSearchResult> = { emptyList() },
    onAddressSelected: (AddressSearchResult) -> Unit = {},
    // Pinned "My location" row (itinerary pickers with a known GPS position)
    showMyPosition: Boolean = false,
    onMyPositionSelected: () -> Unit = {},
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

    // Only a dark basemap needs the outline; with no style supplied, assume a light one.
    val resolvedShowDarkOutline = showDarkOutline
        ?: (currentMapStyle?.key?.let { MapStyleUtils.isDarkHalf(it) } == true && !startExpanded)

    var stationSearchResults by remember { mutableStateOf<List<StationSearchResult>>(emptyList()) }
    var lineSearchResults by remember { mutableStateOf<List<LineSearchResult>>(emptyList()) }
    var addressSearchResults by remember { mutableStateOf<List<AddressSearchResult>>(emptyList()) }

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
            addressSearchResults = emptyList()
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
        addressSearchResults = if (
            content != TransportSearchContent.LINES_ONLY &&
            current.length >= ADDRESS_MIN_QUERY_LENGTH
        ) {
            onSearchAddresses(current)
        } else {
            emptyList()
        }
    }

    fun clearQuery() {
        setEffectiveQuery("")
        stationSearchResults = emptyList()
        lineSearchResults = emptyList()
        addressSearchResults = emptyList()
    }

    SimpleSearchBar(
        modifier = modifier,
        searchResults = stationSearchResults,
        lineSearchResults = lineSearchResults,
        addressResults = addressSearchResults,
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
        onAddressSearch = { address ->
            if (showHistory) {
                onAddToHistory(
                    SearchHistoryItem(
                        query = address.label,
                        type = SearchType.ADDRESS,
                        lat = address.lat,
                        lon = address.lon,
                        detail = address.detail
                    )
                )
            }
            onAddressSelected(address)
            clearQuery()
        },
        onHistoryItemClick = { historyItem ->
            when {
                historyItem.type == SearchType.LINE ->
                    onLineSelected(LineSearchResult(historyItem.query))
                historyItem.type == SearchType.ADDRESS ->
                    // Coordinates persisted with the entry: re-select without re-geocoding
                    if (historyItem.lat != null && historyItem.lon != null) {
                        onAddressSelected(
                            AddressSearchResult(
                                label = historyItem.query,
                                detail = historyItem.detail,
                                lat = historyItem.lat,
                                lon = historyItem.lon
                            )
                        )
                    } else Unit
                else -> onStopPrimary(
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
        showDirections = showDirections,
        showMyPositionRow = showMyPosition,
        onMyPositionClick = {
            onMyPositionSelected()
            clearQuery()
        }
    )
}

// Addresses need a slightly longer prefix than stops before hitting the remote geocoder
private const val ADDRESS_MIN_QUERY_LENGTH = 3
