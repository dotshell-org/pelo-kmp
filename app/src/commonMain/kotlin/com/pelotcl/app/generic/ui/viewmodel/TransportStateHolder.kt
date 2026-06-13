package com.pelotcl.app.generic.ui.viewmodel

import com.pelotcl.app.generic.data.models.stops.Favorite
import com.pelotcl.app.generic.data.models.realtime.vehiclepositions.SimpleVehiclePosition
import com.pelotcl.app.generic.data.models.geojson.StopFeature
import com.pelotcl.app.generic.data.network.transport.TransportApi
import com.pelotcl.app.generic.data.network.transport.TransportLineRules
import com.pelotcl.app.generic.data.repository.api.FavoritesRepository
import com.pelotcl.app.generic.data.repository.api.HolidayDetector
import com.pelotcl.app.generic.data.repository.api.OfflineDataManager
import com.pelotcl.app.generic.data.repository.api.OfflineRepository
import com.pelotcl.app.generic.data.repository.api.SchedulesRepository
import com.pelotcl.app.generic.data.repository.api.TrafficAlertsRepository
import com.pelotcl.app.generic.data.repository.api.TransportRepository
import com.pelotcl.app.generic.data.repository.api.HolidayDetector as ApiHolidayDetector
import com.pelotcl.app.generic.data.cache.TransportCache
import com.pelotcl.app.generic.service.TransportServiceProvider
import com.pelotcl.app.generic.data.repository.online.VehiclePositionsRepository
import com.pelotcl.app.generic.data.models.gtfs.LineStopInfo
import com.pelotcl.app.generic.data.models.realtime.alerts.official.TrafficAlert
import com.pelotcl.app.generic.data.models.realtime.alerts.official.AlertSeverity
import com.pelotcl.app.generic.data.models.geojson.Feature
import com.pelotcl.app.generic.data.models.search.LineSearchResult
import com.pelotcl.app.generic.data.models.search.StationSearchResult
import com.pelotcl.app.generic.data.offline.OfflineDataInfo
import com.pelotcl.app.generic.data.models.lines.MultiLineStringGeometry
import com.pelotcl.app.generic.data.repository.itinerary.itinerary.JourneyLeg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class TransportStateHolder(
    private val transportRepository: TransportRepository,
    private val schedulesRepository: SchedulesRepository,
    private val favoritesRepository: FavoritesRepository,
    private val trafficAlertsRepository: TrafficAlertsRepository,
    private val offlineDataManager: OfflineDataManager,
    private val offlineRepository: OfflineRepository,
    private val holidayDetector: HolidayDetector,
    private val lineRules: TransportLineRules,
    private val transportApi: TransportApi,
    private val transportCache: TransportCache,
    private val vehiclePositionsRepository: VehiclePositionsRepository,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
) {
    companion object {
        private const val TAG = "TransportStateHolder"
        private const val DEBUG_LOGGING = false
    }

    // ===== State =====
    private val _linesState = MutableStateFlow<TransportLinesState>(TransportLinesState.Loading)
    private val _uiState = MutableStateFlow<TransportLinesUiState>(TransportLinesUiState.Loading)
    private val _alertsState = MutableStateFlow<TrafficAlertsState>(TrafficAlertsState.Loading)
    private val _stopsUiState = MutableStateFlow<TransportStopsUiState>(TransportStopsUiState.Loading)
    private val _trafficAlerts = MutableStateFlow<List<TrafficAlert>>(emptyList())
    private val _alertsTimestampMillis = MutableStateFlow<Long?>(null)
    private val _vehiclePositions = MutableStateFlow<List<SimpleVehiclePosition>>(emptyList())
    private val _globalVehiclePositions = MutableStateFlow<List<SimpleVehiclePosition>>(emptyList())
    private val _isLiveTrackingEnabled = MutableStateFlow(false)
    private val _isGlobalLiveEnabled = MutableStateFlow(false)
    private val _isOffline = MutableStateFlow(false)
    private val _headsigns = MutableStateFlow<Map<Int, String>>(emptyMap())
    private val _allSchedules = MutableStateFlow<List<String>>(emptyList())
    private val _nextSchedules = MutableStateFlow<List<String>>(emptyList())
    private val _availableDirections = MutableStateFlow<List<Int>>(emptyList())
    private val _favoriteStops = MutableStateFlow<Set<String>>(emptySet())
    private val _userFavorites = MutableStateFlow<List<Favorite>>(emptyList())
    private val _selectedLineName = MutableStateFlow<String?>(null)
    private val _offlineDataInfo = MutableStateFlow(OfflineDataInfo())

    val uiState: StateFlow<TransportLinesUiState> = _uiState.asStateFlow()
    val stopsUiState: StateFlow<TransportStopsUiState> = _stopsUiState.asStateFlow()
    val trafficAlerts: StateFlow<List<TrafficAlert>> = _trafficAlerts.asStateFlow()
    val alertsTimestampMillis: StateFlow<Long?> = _alertsTimestampMillis.asStateFlow()
    val vehiclePositions: StateFlow<List<SimpleVehiclePosition>> = _vehiclePositions.asStateFlow()
    val globalVehiclePositions: StateFlow<List<SimpleVehiclePosition>> = _globalVehiclePositions.asStateFlow()
    val isLiveTrackingEnabled: StateFlow<Boolean> = _isLiveTrackingEnabled.asStateFlow()
    val isGlobalLiveEnabled: StateFlow<Boolean> = _isGlobalLiveEnabled.asStateFlow()
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()
    val headsigns: StateFlow<Map<Int, String>> = _headsigns.asStateFlow()
    val allSchedules: StateFlow<List<String>> = _allSchedules.asStateFlow()
    val nextSchedules: StateFlow<List<String>> = _nextSchedules.asStateFlow()
    val availableDirections: StateFlow<List<Int>> = _availableDirections.asStateFlow()
    val favoriteStops: StateFlow<Set<String>> = _favoriteStops.asStateFlow()
    val userFavorites: StateFlow<List<Favorite>> = _userFavorites.asStateFlow()
    val selectedLineName: StateFlow<String?> = _selectedLineName.asStateFlow()
    val offlineDataInfo: StateFlow<OfflineDataInfo> = _offlineDataInfo.asStateFlow()

    // ===== Caches =====
    private var cachedAvailableLines: List<String> = emptyList()
    private var cachedAvailableLinesUiState: TransportLinesUiState? = null
    private var cachedAvailableLinesStopsState: TransportStopsUiState? = null
    private var cachedAlertIndexSource: List<TrafficAlert>? = null
    private var cachedAlertSeverityByLine: Map<String, AlertSeverity> = emptyMap()
    private var cachedAlertsByLineSource: List<TrafficAlert>? = null
    private var cachedAlertsByLine: Map<String, List<TrafficAlert>> = emptyMap()

    // ===== Jobs =====
    private var vehiclePositionsJob: Job? = null
    private var globalLiveJob: Job? = null

    init {
        loadFavorites()
        loadTransportLines()
        loadStops()
        loadTrafficAlerts()
    }

    fun loadTransportLines() {
        scope.launch {
            _linesState.value = TransportLinesState.Loading
            _uiState.value = TransportLinesUiState.Loading

            val retryDelays = listOf(0L, 3_000L, 8_000L)
            for ((attempt, delayMs) in retryDelays.withIndex()) {
                if (delayMs > 0) {
                    delay(delayMs)
                }
                try {
                    val result = transportRepository.getAllLines()
                    result.onSuccess { lines ->
                        val strongFeatures = lines.features.orEmpty()
                        _linesState.value = TransportLinesState.Success(lines)
                        // Show the strong lines (metro/tram/RX) immediately.
                        _uiState.value = TransportLinesUiState.Success(strongFeatures)

                        val cache = transportCache
                        val metroLines = strongFeatures.filter {
                            it.properties.transportType == "METRO" ||
                                it.properties.transportType == "FUNICULAR"
                        }
                        val tramLines = strongFeatures.filter {
                            it.properties.transportType == "TRAM"
                        }

                        if (metroLines.isNotEmpty()) {
                            cache.saveMetroLines(metroLines)
                        }
                        if (tramLines.isNotEmpty()) {
                            cache.saveTramLines(tramLines)
                        }

                        // Then load bus (incl. trambus, which lives in the bus typename) and
                        // navigone, and merge them in so every line trace shows on the map.
                        // Best-effort: a failure here keeps the strong lines displayed.
                        val lineService = TransportServiceProvider.getTransportLineService()
                        val busFeatures = runCatching {
                            lineService.getBusLines().features.orEmpty()
                        }.getOrElse { emptyList() }
                        val navigoneFeatures = runCatching {
                            lineService.getNavigoneLines().features.orEmpty()
                        }.getOrElse { emptyList() }
                        if (busFeatures.isNotEmpty() || navigoneFeatures.isNotEmpty()) {
                            val allFeatures = strongFeatures + busFeatures + navigoneFeatures
                            _linesState.value = TransportLinesState.Success(lines.copy(features = allFeatures))
                            _uiState.value = TransportLinesUiState.Success(allFeatures)
                        }
                        return@launch
                    }.onFailure { error ->
                        if (attempt == retryDelays.lastIndex) {
                            _linesState.value = TransportLinesState.Error(error.message ?: "Unknown error")
                            _uiState.value = TransportLinesUiState.Error(error.message ?: "Unknown error")
                        }
                    }
                } catch (e: Exception) {
                    if (attempt == retryDelays.lastIndex) {
                        _linesState.value = TransportLinesState.Error(e.message ?: "Unknown error")
                        _uiState.value = TransportLinesUiState.Error(e.message ?: "Unknown error")
                    }
                }
            }
        }
    }

    fun loadStops() {
        scope.launch {
            _stopsUiState.value = TransportStopsUiState.Loading
            val cache = transportCache

            val offlineStops = runCatching {
                withContext(Dispatchers.Default) { offlineRepository.loadStops() }
            }.getOrNull().orEmpty()

            if (offlineStops.isNotEmpty()) {
                val sampleSize = minOf(10, offlineStops.size)
                val emptyDesserteCount = offlineStops.take(sampleSize).count { it.properties.desserte.isBlank() }
                val unknownCount = offlineStops.take(sampleSize).count { it.properties.desserte.equals("UNKNOWN", ignoreCase = true) }

                if (emptyDesserteCount > sampleSize * 0.8) {
                    withContext(Dispatchers.Default) {
                        offlineRepository.clearStopsCache()
                    }
                    return@launch loadStops()
                }
            }

            val cachedStops = runCatching {
                withContext(Dispatchers.Default) { cache.getStops() }
            }.getOrNull().orEmpty()

            val baseStops: List<StopFeature> = when {
                offlineStops.isNotEmpty() -> offlineStops
                cachedStops.isNotEmpty() -> cachedStops
                else -> {
                    val wfsResult = runCatching {
                        withContext(Dispatchers.Default) { transportApi.getTransportStops() }
                    }
                    wfsResult.onFailure { error ->
                        _stopsUiState.value = TransportStopsUiState.Error(
                            error.message ?: "Unable to load transport stops"
                        )
                        return@launch
                    }
                    val features = wfsResult.getOrNull()?.features
                    if (features.isNullOrEmpty()) {
                        _stopsUiState.value = TransportStopsUiState.Error(
                            "No transport stops available from server"
                        )
                        return@launch
                    }
                    features
                }
            }

            if (baseStops.isEmpty()) {
                _stopsUiState.value = TransportStopsUiState.Error("No transport stops available")
                return@launch
            }

            val (enrichedStops, didEnrich) = withContext(Dispatchers.Default) {
                fun hasStrongToken(desserte: String): Boolean {
                    if (desserte.isBlank()) return false
                    val strongTokens = setOf("A", "B", "C", "D", "F1", "F2", "RX")
                    val entries = desserte.split(",")
                    for (entry in entries) {
                        val token = entry.trim().substringBefore(":").trim()
                        if (token.isEmpty()) continue
                        val up = token.uppercase()
                        if (up in strongTokens) return true
                        if (up.startsWith("T")) return true
                        if (up.startsWith("NAV")) return true
                    }
                    return false
                }

                val sampleSize = minOf(50, baseStops.size)
                val sampleStops = baseStops.take(sampleSize)
                val nonBlankCount = sampleStops.count { it.properties.desserte.isNotBlank() }
                val ratioNonBlank = nonBlankCount.toDouble() / sampleSize.toDouble()
                val strongStopCount = sampleStops.count { it.properties.desserte.isNotBlank() && hasStrongToken(it.properties.desserte) }
                val unknownCount = sampleStops.count { it.properties.desserte.equals("UNKNOWN", ignoreCase = true) }
                val emptyCount = sampleStops.count { it.properties.desserte.isBlank() }

                val shouldEnrich = strongStopCount == 0 && ratioNonBlank < 0.1 ||
                    unknownCount > sampleSize * 0.3 ||
                    (strongStopCount < sampleSize * 0.2 && unknownCount > sampleSize * 0.2)

                if (!shouldEnrich) {
                    baseStops to false
                } else {
                    val desserteCache = HashMap<String, String>(512)
                    val stopsNeedingEnrichment = baseStops.filter { it.properties.desserte.isBlank() }

                    val enriched = stopsNeedingEnrichment.mapNotNull { stop ->
                        val name = stop.properties.nom
                        if (name.isBlank()) {
                            stop
                        } else {
                            val desserte = desserteCache.getOrPut(name) {
                                schedulesRepository.getDesserteForStop(name).orEmpty()
                            }
                            if (desserte.isBlank()) {
                                null
                            } else {
                                stop.copy(properties = stop.properties.copy(desserte = desserte))
                            }
                        }
                    }

                    val stopMap = baseStops.associateBy { it.properties.nom }.toMutableMap()
                    enriched.forEach { enrichedStop ->
                        stopMap[enrichedStop.properties.nom] = enrichedStop
                    }

                    stopMap.values.toList() to (enriched.isNotEmpty())
                }
            }

            _stopsUiState.value = TransportStopsUiState.Success(enrichedStops)

            if (enrichedStops.isNotEmpty()) {
                withContext(Dispatchers.Default) {
                    offlineRepository.saveStops(enrichedStops)
                    cache.saveStops(enrichedStops)
                }
            }
        }
    }

    fun loadFavorites() {
        _favoriteStops.value = favoritesRepository.getFavoriteStops()
        _userFavorites.value = favoritesRepository.getUserFavorites()
    }

    fun addUserFavorite(name: String, iconName: String, stopName: String) {
        val newFavorite = Favorite(
            id = favoritesRepository.generateFavoriteId(),
            name = name,
            iconName = iconName,
            stopName = stopName
        )
        if (favoritesRepository.addFavorite(newFavorite)) {
            loadFavorites()
        }
    }

    fun removeUserFavorite(favoriteId: String) {
        if (favoritesRepository.removeFavorite(favoriteId)) {
            loadFavorites()
        }
    }

    fun toggleFavoriteStop(stopName: String) {
        favoritesRepository.toggleFavoriteStop(stopName)
        loadFavorites()
    }

    fun getConnectionsForStop(stopName: String, lineName: String): Flow<List<LineSearchResult>> {
        return flow {
            val rawDesserte = schedulesRepository.getDesserteForStop(stopName)
            val lines = rawDesserte.orEmpty()
                .split(",")
                .mapNotNull { token ->
                    val line = token.trim().substringBefore(":").trim()
                    line.takeIf { it.isNotEmpty() && !it.equals(lineName, ignoreCase = true) }
                }
                .distinctBy { it.uppercase() }
            emit(lines.map { LineSearchResult(it) })
        }
    }

    suspend fun getNextDeparturesForStop(
        stopName: String,
        lines: List<String>
    ): List<StopDeparturePreview> = withContext(Dispatchers.Default) {
        if (stopName.isBlank() || lines.isEmpty()) {
            return@withContext emptyList()
        }

        val now = clock.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val today = now.date
        val nowMinutes = now.hour * 60 + now.minute
        val isSchoolHoliday = holidayDetector.isSchoolHoliday(today)
        val isPublicHoliday = holidayDetector.isPublicHoliday(today)

        lines.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.uppercase() }
            .flatMap { line ->
                val headsigns = schedulesRepository.getHeadsigns(line)
                val directions = headsigns.keys.ifEmpty { setOf(0, 1) }
                directions.asSequence().mapNotNull { directionId ->
                    val schedules = schedulesRepository.getSchedules(
                        lineName = line,
                        stopName = stopName,
                        directionId = directionId,
                        isSchoolHoliday = isSchoolHoliday,
                        isPublicHoliday = isPublicHoliday
                    )
                    val nextDeparture = pickNextDeparture(schedules, nowMinutes) ?: return@mapNotNull null
                    StopDeparturePreview(
                        lineName = line,
                        directionId = directionId,
                        directionName = headsigns[directionId] ?: "Direction ${directionId + 1}",
                        nextDeparture = nextDeparture
                    )
                }
            }
            .sortedWith(
                compareBy(
                    { parseTimeToMinutes(it.nextDeparture) ?: Int.MAX_VALUE },
                    { it.lineName },
                    { it.directionId }
                )
            )
            .toList()
    }

    suspend fun searchStops(query: String): List<StationSearchResult> =
        schedulesRepository.searchStopsByName(query)

    fun searchLines(query: String): List<LineSearchResult> {
        return schedulesRepository.searchLinesByName(query)
    }

    fun getAlertsForLine(lineName: String): List<TrafficAlert> {
        val alerts = _trafficAlerts.value
        if (alerts !== cachedAlertsByLineSource) {
            val index = mutableMapOf<String, MutableList<TrafficAlert>>()
            alerts.distinctBy { it.alertNumber }.forEach { alert ->
                extractAlertLineTokens(alert).forEach { token ->
                    index.getOrPut(token) { mutableListOf() }.add(alert)
                }
            }
            index.values.forEach { list -> list.sortBy { it.severityLevel } }
            cachedAlertsByLine = index
            cachedAlertsByLineSource = alerts
        }
        val target = normalizeLineToken(lineName)
        return cachedAlertsByLine[target] ?: emptyList()
    }

    fun getAllAvailableLines(): List<String> {
        val currentUiState = _uiState.value
        val currentStopsState = _stopsUiState.value

        if (currentUiState === cachedAvailableLinesUiState &&
            currentStopsState === cachedAvailableLinesStopsState
        ) {
            return cachedAvailableLines
        }

        val linesFromLoadedFeatures = when (currentUiState) {
            is TransportLinesUiState.Success -> currentUiState.lines.map { it.properties.lineName }
            is TransportLinesUiState.PartialSuccess -> currentUiState.lines.map { it.properties.lineName }
            else -> emptyList()
        }

        val linesFromStops = when (currentStopsState) {
            is TransportStopsUiState.Success -> currentStopsState.stops
                .asSequence()
                .flatMap { stop -> parseLineCodesFromDesserte(stop.properties.desserte).asSequence() }
                .toList()
            else -> emptyList()
        }

        val aggregated = (linesFromLoadedFeatures + linesFromStops)
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.uppercase() }
            .toList()

        cachedAvailableLinesUiState = currentUiState
        cachedAvailableLinesStopsState = currentStopsState
        cachedAvailableLines = aggregated
        return aggregated
    }

    fun getStopsForLine(lineName: String, currentStopName: String? = null, directionId: Int? = null): List<LineStopInfo> {
        val state = _stopsUiState.value
        if (state !is TransportStopsUiState.Success) return emptyList()

        val effectiveDirection = directionId ?: 0
        val effectiveLineName = resolveScheduleRouteName(lineName)
        val stopSequences = schedulesRepository.getStopSequences(effectiveLineName, effectiveDirection)
        val stopSequenceByName = stopSequences
            .associate { (stopNameFromGtfs, sequence) -> stopNameFromGtfs.uppercase() to sequence }

        val filteredStops = state.stops.filter { stop ->
            parseLineCodesFromDesserte(stop.properties.desserte)
                .any { areEquivalentRouteNames(it, lineName) }
        }

        if (filteredStops.isEmpty()) return emptyList()

        val normalizedNameCache = HashMap<String, String>(filteredStops.size)
        val stopsByNormalizedName = filteredStops
            .groupBy { stop ->
                normalizedNameCache.getOrPut(stop.properties.nom) { normalizeStopName(stop.properties.nom) }
            }

        val usedStopNames = mutableSetOf<String>()
        val orderedStops = stopSequences.mapNotNull { (stopNameFromGtfs, sequence) ->
            val normalizedName = normalizedNameCache.getOrPut(stopNameFromGtfs) { normalizeStopName(stopNameFromGtfs) }
            if (!usedStopNames.add(normalizedName)) return@mapNotNull null

            val stopFeature = stopsByNormalizedName[normalizedName]?.firstOrNull()
            val displayStopName = stopFeature?.properties?.nom ?: stopNameFromGtfs

            LineStopInfo(
                stopId = stopFeature?.id ?: "${lineName.uppercase()}_${effectiveDirection}_$sequence",
                stopName = displayStopName,
                stopSequence = sequence,
                isCurrentStop = displayStopName.equals(currentStopName, ignoreCase = true)
            )
        }

        if (orderedStops.isNotEmpty()) {
            val missingStops = filteredStops
                .asSequence()
                .filter { normalizedNameCache.getOrPut(it.properties.nom) { normalizeStopName(it.properties.nom) } !in usedStopNames }
                .sortedBy { it.properties.nom.uppercase() }
                .mapIndexed { index, stop ->
                    val normalizedName = normalizedNameCache[stop.properties.nom]!!
                    usedStopNames.add(normalizedName)

                    LineStopInfo(
                        stopId = stop.id,
                        stopName = stop.properties.nom,
                        stopSequence = stopSequenceByName[stop.properties.nom.uppercase()]
                            ?: (orderedStops.size + index + 1),
                        isCurrentStop = stop.properties.nom.equals(currentStopName, ignoreCase = true)
                    )
                }
                .toList()

            return orderedStops + missingStops
        }

        return filteredStops
            .asSequence()
            .sortedBy { it.properties.nom.uppercase() }
            .distinctBy { normalizedNameCache.getOrPut(it.properties.nom) { normalizeStopName(it.properties.nom) } }
            .mapIndexed { index, stop ->
                LineStopInfo(
                    stopId = stop.id,
                    stopName = stop.properties.nom,
                    stopSequence = index + 1,
                    isCurrentStop = stop.properties.nom.equals(currentStopName, ignoreCase = true)
                )
            }
            .toList()
    }

    fun loadHeadsign(lineName: String) {
        scope.launch(Dispatchers.Default) {
            _headsigns.value = schedulesRepository.getHeadsigns(resolveScheduleRouteName(lineName))
        }
    }

    fun computeAvailableDirections(lineName: String, stopName: String) {
        scope.launch(Dispatchers.Default) {
            if (lineName.isBlank() || stopName.isBlank()) {
                _availableDirections.value = emptyList()
                return@launch
            }

            val today = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val isSchoolHoliday = holidayDetector.isSchoolHoliday(today)
            val isPublicHoliday = holidayDetector.isPublicHoliday(today)
            val candidateDirections = _headsigns.value.keys.ifEmpty { setOf(0, 1) }.toList().sorted()

            val available = candidateDirections.filter { directionId ->
                runCatching {
                    schedulesRepository.getSchedules(
                        lineName = resolveScheduleRouteName(lineName),
                        stopName = stopName,
                        directionId = directionId,
                        isSchoolHoliday = isSchoolHoliday,
                        isPublicHoliday = isPublicHoliday
                    )
                }.getOrDefault(emptyList()).isNotEmpty()
            }
            _availableDirections.value = available
        }
    }

    fun loadSchedulesForDirection(lineName: String, stopName: String, directionId: Int) {
        scope.launch(Dispatchers.Default) {
            _allSchedules.value = emptyList()
            _nextSchedules.value = emptyList()

            if (lineName.isBlank() || stopName.isBlank()) return@launch

            val today = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val isSchoolHoliday = holidayDetector.isSchoolHoliday(today)
            val isPublicHoliday = holidayDetector.isPublicHoliday(today)

            val allSchedulesForDay = schedulesRepository.getSchedules(
                lineName = resolveScheduleRouteName(lineName),
                stopName = stopName,
                directionId = directionId,
                isSchoolHoliday = isSchoolHoliday,
                isPublicHoliday = isPublicHoliday
            )
            _allSchedules.value = allSchedulesForDay
            if (allSchedulesForDay.isEmpty()) return@launch

            val nowMinutes = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).let {
                it.hour * 60 + it.minute
            }
            val ordered = allSchedulesForDay.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

            val nextThree = (
                ordered.filter { schedule ->
                    val minutes = parseTimeToMinutes(schedule) ?: return@filter false
                    minutes >= nowMinutes
                } + ordered
            ).take(3)

            _nextSchedules.value = nextThree
        }
    }

    fun getAlertSeverityMapForLines(lineNames: List<String>): Map<String, AlertSeverity> {
        if (lineNames.isEmpty()) return emptyMap()
        val severityByLine = getOrBuildAlertSeverityIndex()
        if (severityByLine.isEmpty()) return emptyMap()

        return lineNames
            .asSequence()
            .mapNotNull { lineName ->
                val token = normalizeLineToken(lineName)
                if (token.isEmpty() || !isLikelyLineToken(token)) return@mapNotNull null
                severityByLine[token]?.let { severity ->
                    lineName.uppercase() to severity
                }
            }
            .toMap()
    }

    fun selectLine(lineName: String) {
        _selectedLineName.value = lineName
    }

    fun clearSelectedLine() {
        _selectedLineName.value = null
    }

    fun addLineToLoaded(lineName: String) {
        val requestedLine = lineName.trim()
        if (requestedLine.isEmpty()) return

        scope.launch {
            val currentLines = when (val state = _uiState.value) {
                is TransportLinesUiState.Success -> state.lines
                is TransportLinesUiState.PartialSuccess -> state.lines
                else -> emptyList()
            }

            if (currentLines.any { areEquivalentRouteNames(it.properties.lineName, requestedLine) }) {
                return@launch
            }

            transportRepository.getLineByName(requestedLine)
                .onSuccess { loadedFeatures ->
                    if (loadedFeatures.isEmpty()) return@onSuccess

                    val merged = (currentLines + loadedFeatures)
                        .groupBy { it.properties.traceCode }
                        .map { (_, features) -> features.first() }

                    _uiState.value = TransportLinesUiState.Success(merged)
                    invalidateAvailableLinesCache()
                }
                .onFailure { }
        }
    }

    fun removeLineFromLoaded(lineName: String) {
        val requestedLine = lineName.trim()
        if (requestedLine.isEmpty()) return

        val currentLines = when (val state = _uiState.value) {
            is TransportLinesUiState.Success -> state.lines
            is TransportLinesUiState.PartialSuccess -> state.lines
            else -> return
        }

        val filtered = currentLines.filterNot {
            areEquivalentRouteNames(it.properties.lineName, requestedLine)
        }

        if (filtered.size != currentLines.size) {
            _uiState.value = TransportLinesUiState.Success(filtered)
            invalidateAvailableLinesCache()
        }
    }

    fun loadAllLines() {
        loadTransportLines()
    }

    fun preloadStops() {
        loadStops()
    }

    fun reloadStrongLines() {
        loadTransportLines()
    }

    fun startLiveTracking(lineName: String) {
        if (_isGlobalLiveEnabled.value) {
            stopGlobalLive()
        }
        vehiclePositionsJob?.cancel()
        _isLiveTrackingEnabled.value = true
        _vehiclePositions.value = emptyList()

        vehiclePositionsJob = scope.launch {
            vehiclePositionsRepository.streamAllVehiclePositions().collect { result ->
                result.onSuccess { allPositions ->
                    val requested = lineName.trim()
                    val requestedNormalized = lineRules.normalizeForComparison(requested)

                    _vehiclePositions.value = allPositions.filter {
                        lineRules.normalizeForComparison(it.lineName) == requestedNormalized
                    }
                }.onFailure { }
            }
        }
    }

    fun stopLiveTracking() {
        vehiclePositionsJob?.cancel()
        vehiclePositionsJob = null
        _isLiveTrackingEnabled.value = false
        _vehiclePositions.value = emptyList()
    }

    fun stopGlobalLive() {
        globalLiveJob?.cancel()
        globalLiveJob = null
        _isGlobalLiveEnabled.value = false
        _globalVehiclePositions.value = emptyList()
    }

    fun toggleGlobalLive() {
        if (_isGlobalLiveEnabled.value) {
            stopGlobalLive()
            return
        }

        if (_isLiveTrackingEnabled.value) {
            stopLiveTracking()
        }
        _isGlobalLiveEnabled.value = true
        _globalVehiclePositions.value = emptyList()

        globalLiveJob = scope.launch {
            vehiclePositionsRepository.streamAllVehiclePositions().collect { result ->
                result.onSuccess { allPositions ->
                    _globalVehiclePositions.value = allPositions
                }.onFailure { }
            }
        }
    }

    fun hasAllIcons(iconNames: Collection<String>): Boolean = false

    fun getIconBitmap(iconName: String): Nothing? = null

    fun cacheIconBitmap(iconName: String, bitmap: Nothing?) {
    }

    fun clearScheduleState() {
        _allSchedules.value = emptyList()
        _nextSchedules.value = emptyList()
    }

    fun getStopsFeaturesForLine(lineName: String): List<StopFeature> {
        val state = _stopsUiState.value
        if (state is TransportStopsUiState.Success) {
            return state.stops.filter { stop ->
                parseLineCodesFromDesserte(stop.properties.desserte)
                    .any { areEquivalentRouteNames(it, lineName) }
            }
        }
        return emptyList()
    }

    fun isStopsByLineIndexReady(): Boolean {
        return _stopsUiState.value is TransportStopsUiState.Success
    }

    fun startOfflineDownload() {
        scope.launch {
            offlineDataManager.downloadAllOfflineData()
        }
    }

    fun cancelOfflineDownload() {
        offlineDataManager.cancelDownload()
    }

    fun reloadStopsCache() {
        loadStops()
    }

    fun resetLineDetailState() {
        _headsigns.value = emptyMap()
        _allSchedules.value = emptyList()
        _nextSchedules.value = emptyList()
        _availableDirections.value = emptyList()
    }

    fun loadTrafficAlerts() {
        scope.launch {
            _alertsState.value = TrafficAlertsState.Loading
            try {
                val result = trafficAlertsRepository.getTrafficAlerts()
                result.onSuccess { alerts ->
                    _alertsState.value = TrafficAlertsState.Success(alerts)
                    _trafficAlerts.value = alerts
                    _alertsTimestampMillis.value = clock.now().toEpochMilliseconds()
                }.onFailure { error ->
                    _alertsState.value = TrafficAlertsState.Error(error.message ?: "Unknown error")
                }
            } catch (e: Exception) {
                _alertsState.value = TrafficAlertsState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun parseLineCodesFromDesserte(desserte: String): List<String> {
        return com.pelotcl.app.generic.ui.viewmodel.parseLineCodesFromDesserte(desserte)
    }

    fun generateBezierCurve(start: List<Double>, end: List<Double>): List<List<Double>> {
        return com.pelotcl.app.generic.ui.viewmodel.generateBezierCurve(start, end)
    }

    fun onCleared() {
        vehiclePositionsJob?.cancel()
        globalLiveJob?.cancel()
    }

    // ===== Private methods =====

    private fun analyzeLoadedStops(stops: List<StopFeature>) {
    }

    private fun getOrBuildAlertSeverityIndex(): Map<String, AlertSeverity> {
        val alerts = _trafficAlerts.value
        if (alerts === cachedAlertIndexSource) return cachedAlertSeverityByLine

        val index = mutableMapOf<String, AlertSeverity>()
        alerts
            .asSequence()
            .distinctBy { it.alertNumber }
            .forEach { alert ->
                val severity = AlertSeverity.fromSeverityType(alert.severityType, alert.severityLevel)
                extractAlertLineTokens(alert).forEach { token ->
                    val existing = index[token]
                    if (existing == null || severity.level < existing.level) {
                        index[token] = severity
                    }
                }
            }

        cachedAlertIndexSource = alerts
        cachedAlertSeverityByLine = index
        return index
    }

    private fun extractAlertLineTokens(alert: TrafficAlert): Set<String> {
        val lineCodeTokens = parseAlertTokens(alert.lineCode)
        val lineNameTokens = parseAlertTokens(alert.lineName)
        val shouldUseObjectList = alert.objectType.contains("ligne", ignoreCase = true) ||
            alert.objectType.contains("line", ignoreCase = true) ||
            alert.objectType.contains("route", ignoreCase = true) ||
            (lineCodeTokens.isEmpty() && lineNameTokens.isEmpty())

        return buildSet {
            addAll(lineCodeTokens)
            addAll(lineNameTokens)
            if (shouldUseObjectList) {
                addAll(parseAlertTokens(alert.objectList))
            }
            addAll(parseLineMentionsFromText(alert.title))
            addAll(parseLineMentionsFromText(alert.message))
        }
    }

    private fun parseAlertTokens(raw: String): Set<String> {
        return com.pelotcl.app.generic.ui.viewmodel.parseAlertTokens(raw, lineRules)
    }

    private fun parseLineMentionsFromText(raw: String): Set<String> {
        return com.pelotcl.app.generic.ui.viewmodel.parseLineMentionsFromText(raw, lineRules)
    }

    private fun normalizeLineToken(raw: String): String {
        return lineRules.normalizeAlertToken(raw)
    }

    private fun canonicalRouteName(raw: String): String {
        return lineRules.canonicalRouteName(raw)
    }

    private fun invalidateAvailableLinesCache() {
        cachedAvailableLines = emptyList()
        cachedAvailableLinesUiState = null
        cachedAvailableLinesStopsState = null
    }

    private fun resolveScheduleRouteName(raw: String): String {
        val candidates = lineRules.equivalentRouteNames(raw)
        val routeNames = schedulesRepository.getAllRouteNames()
        val routeNamesUpper = routeNames.map { it.uppercase() }.toSet()
        val matched = candidates.firstOrNull { it in routeNamesUpper }
        return matched ?: canonicalRouteName(raw)
    }

    private fun areEquivalentRouteNames(first: String, second: String): Boolean {
        return canonicalRouteName(first) == canonicalRouteName(second)
    }

    private fun isLikelyLineToken(token: String): Boolean {
        return lineRules.isLikelyLineToken(token)
    }

    private fun alertAffectsLine(alert: TrafficAlert, lineName: String): Boolean {
        val target = normalizeLineToken(lineName)
        if (target.isEmpty() || !isLikelyLineToken(target)) return false

        val lineCodeTokens = parseAlertTokens(alert.lineCode)
        val lineNameTokens = parseAlertTokens(alert.lineName)

        val shouldUseObjectList = alert.objectType.contains("ligne", ignoreCase = true) ||
            alert.objectType.contains("line", ignoreCase = true) ||
            alert.objectType.contains("route", ignoreCase = true)

        val primaryTokens = buildSet {
            addAll(lineCodeTokens)
            addAll(lineNameTokens)
            if (shouldUseObjectList) {
                addAll(parseAlertTokens(alert.objectList))
            }
        }

        if (target in primaryTokens) return true

        if (lineCodeTokens.isEmpty() && lineNameTokens.isEmpty() && !shouldUseObjectList) {
            val textTokens = buildSet {
                addAll(parseLineMentionsFromText(alert.title))
                addAll(parseLineMentionsFromText(alert.message))
            }
            return target in textTokens
        }

        return false
    }

    private fun parseTimeToMinutes(rawTime: String): Int? {
        return com.pelotcl.app.generic.ui.viewmodel.parseTimeToMinutes(rawTime)
    }

    private fun pickNextDeparture(schedules: List<String>, currentMinutes: Int): String? {
        return com.pelotcl.app.generic.ui.viewmodel.pickNextDeparture(schedules, currentMinutes)
    }

    private fun normalizeStopName(stopName: String): String {
        return com.pelotcl.app.generic.ui.viewmodel.normalizeStopName(stopName)
    }

    internal fun sectionLinesBetweenStops(
        lines: List<Feature>,
        startStopId: String,
        endStopId: String,
        leg: JourneyLeg
    ): List<Feature> {
        val sectionedLines = mutableListOf<Feature>()

        val stopsState = _stopsUiState.value
        if (stopsState !is TransportStopsUiState.Success) {
            return sectionedLines
        }

        val stops = stopsState.stops

        val startStop = stops.find { it.properties.id.toString() == startStopId }
        val endStop = stops.find { it.properties.id.toString() == endStopId }

        val finalStartStop = startStop ?: findStopByCoordinates(stops, leg.fromLat, leg.fromLon)
        val finalEndStop = endStop ?: findStopByCoordinates(stops, leg.toLat, leg.toLon)

        if (finalStartStop == null || finalEndStop == null) {
            return sectionedLines
        }

        for (line in lines) {
            val lineGeometry = line.multiLineStringGeometry
            if (lineGeometry is MultiLineStringGeometry) {
                val coordinates = lineGeometry.coordinates
                val firstLine = coordinates.firstOrNull() ?: continue

                val startCoord = listOf(finalStartStop.geometry.coordinates[0], finalStartStop.geometry.coordinates[1])
                val endCoord = listOf(finalEndStop.geometry.coordinates[0], finalEndStop.geometry.coordinates[1])

                fun findClosestPointIndex(targetCoord: List<Double>): Int {
                    var minDistance = Double.MAX_VALUE
                    var bestIndex = -1

                    for (i in firstLine.indices) {
                        val coord = firstLine[i]
                        val distance = kotlin.math.sqrt(
                            (coord[0] - targetCoord[0]) * (coord[0] - targetCoord[0]) +
                                (coord[1] - targetCoord[1]) * (coord[1] - targetCoord[1])
                        )

                        if (distance < minDistance) {
                            minDistance = distance
                            bestIndex = i
                        }
                    }

                    return bestIndex
                }

                var startIndex = findClosestPointIndex(startCoord)
                var endIndex = findClosestPointIndex(endCoord)

                if (startIndex != -1 && endIndex != -1) {
                    val initialLength = kotlin.math.abs(endIndex - startIndex)

                    if (initialLength < 10) {
                        val extendBy = 5

                        startIndex = if (startIndex > extendBy) {
                            startIndex - extendBy
                        } else {
                            0
                        }

                        endIndex = if (endIndex < firstLine.size - extendBy - 1) {
                            endIndex + extendBy
                        } else {
                            firstLine.size - 1
                        }
                    }
                }

                if (startIndex != -1 && endIndex != -1) {
                    var sectionCoordinates: List<List<Double>> = if (startIndex < endIndex) {
                        firstLine.subList(startIndex, endIndex + 1)
                    } else {
                        firstLine.subList(endIndex, startIndex + 1).reversed()
                    }

                    if (sectionCoordinates.isNotEmpty()) {
                        if (sectionCoordinates.size == 1) {
                            sectionCoordinates = listOf(startCoord, endCoord)
                        } else {
                            sectionCoordinates = sectionCoordinates.toMutableList().apply {
                                if (this.isNotEmpty()) {
                                    this[0] = startCoord
                                }
                                if (this.size > 1) {
                                    this[this.size - 1] = endCoord
                                }
                            }
                        }
                    }

                    if (sectionCoordinates.size > 2) {
                        val sectionedLine = line.copy(
                            multiLineStringGeometry = MultiLineStringGeometry(
                                type = line.multiLineStringGeometry.type,
                                coordinates = listOf(sectionCoordinates)
                            )
                        )
                        sectionedLines.add(sectionedLine)
                    }
                }
            }
        }
        return sectionedLines
    }

    private fun findStopByCoordinates(
        stops: List<StopFeature>,
        targetLat: Double,
        targetLon: Double
    ): StopFeature? {
        return com.pelotcl.app.generic.ui.viewmodel.findStopByCoordinates(stops, targetLat, targetLon)
    }
}
