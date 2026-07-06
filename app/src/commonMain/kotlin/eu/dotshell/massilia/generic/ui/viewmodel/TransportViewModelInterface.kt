package eu.dotshell.massilia.generic.ui.viewmodel

import eu.dotshell.massilia.generic.data.models.geojson.StopFeature
import eu.dotshell.massilia.generic.data.models.gtfs.LineStopInfo
import eu.dotshell.massilia.generic.data.models.realtime.alerts.official.AlertSeverity
import eu.dotshell.massilia.generic.data.models.realtime.alerts.official.TrafficAlert
import eu.dotshell.massilia.generic.data.models.realtime.vehiclepositions.SimpleVehiclePosition
import eu.dotshell.massilia.generic.data.models.search.LineSearchResult
import eu.dotshell.massilia.generic.data.models.search.StationSearchResult
import eu.dotshell.massilia.generic.data.models.stops.Favorite
import eu.dotshell.massilia.generic.data.offline.OfflineDataInfo
import eu.dotshell.massilia.generic.data.offline.OfflineDownloadState
import eu.dotshell.massilia.generic.data.repository.itinerary.itinerary.JourneyResult
import eu.dotshell.massilia.generic.data.repository.itinerary.itinerary.RaptorStop
import eu.dotshell.massilia.generic.ui.viewmodel.StopDeparturePreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface TransportViewModelInterface {
    val uiState: StateFlow<TransportLinesUiState>
    val stopsUiState: StateFlow<TransportStopsUiState>
    val favoriteStops: StateFlow<Set<String>>
    val vehiclePositions: StateFlow<List<SimpleVehiclePosition>>
    val isLiveTrackingEnabled: StateFlow<Boolean>
    val isOffline: StateFlow<Boolean>
    val isGlobalLiveEnabled: StateFlow<Boolean>
    val globalVehiclePositions: StateFlow<List<SimpleVehiclePosition>>
    val headsigns: StateFlow<Map<Int, String>>
    val availableDirections: StateFlow<List<Int>>
    val allSchedules: StateFlow<List<String>>
    val nextSchedules: StateFlow<List<String>>
    val offlineDataInfo: StateFlow<OfflineDataInfo>
    val selectedLineName: StateFlow<String?>
    val trafficAlerts: StateFlow<List<TrafficAlert>>
    val alertsTimestampMillis: StateFlow<Long?>
    val userFavorites: StateFlow<List<Favorite>>
    val offlineDownloadState: StateFlow<OfflineDownloadState>

    suspend fun searchStops(query: String): List<StationSearchResult>
    fun searchLines(query: String): List<LineSearchResult>

    fun loadAllLines()
    fun preloadStops()
    fun reloadStrongLines()
    fun selectLine(lineName: String)
    fun clearSelectedLine()
    fun addLineToLoaded(lineName: String)
    fun removeLineFromLoaded(lineName: String)
    fun resetLineDetailState()
    fun clearScheduleState()

    fun startLiveTracking(lineName: String)
    fun stopLiveTracking()
    fun stopGlobalLive()
    fun toggleGlobalLive()

    fun toggleFavoriteStop(stopName: String)
    fun addUserFavorite(name: String, iconName: String, stopName: String)
    fun removeUserFavorite(favoriteId: String)
    fun loadFavorites()

    fun loadSchedulesForDirection(lineName: String, stopName: String, directionId: Int)
    fun loadHeadsign(lineName: String)
    fun computeAvailableDirections(lineName: String, stopName: String)
    fun reloadStopsCache()

    fun getAlertsForLine(lineName: String): List<TrafficAlert>
    fun getAlertSeverityMapForLines(lineNames: List<String>): Map<String, AlertSeverity>
    fun getConnectionsForStop(stopName: String, lineName: String): Flow<List<LineSearchResult>>
    suspend fun getNextDeparturesForStop(stopName: String, lines: List<String>): List<StopDeparturePreview>
    fun getAllAvailableLines(): List<String>
    fun getStopsForLine(lineName: String, currentStopName: String?, directionId: Int?): List<LineStopInfo>
    fun parseLineCodesFromDesserte(desserte: String): List<String>
    fun getStopsFeaturesForLine(lineName: String): List<StopFeature>
    fun isStopsByLineIndexReady(): Boolean

    suspend fun resolveStopIdsByName(stopName: String, maxIds: Int = 10): List<Int>
    suspend fun findNearestStops(latitude: Double, longitude: Double, limit: Int): List<RaptorStop>
    suspend fun getOptimizedPaths(
        originStopIds: List<Int>,
        destinationStopIds: List<Int>,
        departureTimeSeconds: Int
    ): List<JourneyResult>
    suspend fun getOptimizedPathsArriveBy(
        originStopIds: List<Int>,
        destinationStopIds: List<Int>,
        arrivalTimeSeconds: Int
    ): List<JourneyResult>

    fun startOfflineDownload()
    fun cancelOfflineDownload()
}
