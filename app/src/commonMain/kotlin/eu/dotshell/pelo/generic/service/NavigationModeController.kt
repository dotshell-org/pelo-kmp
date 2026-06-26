package eu.dotshell.pelo.generic.service

import eu.dotshell.pelo.generic.data.cache.TransportCacheImpl
import eu.dotshell.pelo.generic.data.config.AppConfigLoader
import eu.dotshell.pelo.generic.data.models.navigation.NavigationKeyStopType
import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.JourneyLeg
import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.JourneyResult
import eu.dotshell.pelo.generic.data.telemetry.TelemetryEmitter
import eu.dotshell.pelo.generic.data.telemetry.TripDetector
import eu.dotshell.pelo.generic.utils.geo.GeometryUtils
import eu.dotshell.pelo.generic.utils.location.GeoPoint
import eu.dotshell.pelo.platform.Log
import eu.dotshell.pelo.platform.PlatformContext
import eu.dotshell.pelo.platform.ioDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class NavigationModeUiState(
    val isActive: Boolean = false,
    val journey: JourneyResult? = null,
    val currentLegIndex: Int = 0,
    val nextStopName: String? = null,
    val nextRouteName: String? = null,
    val nextStopType: NavigationKeyStopType? = null,
    val distanceToNextMeters: Int? = null,
    val remainingMinutes: Int = 0,
    val instruction: String = "",
    val isComplete: Boolean = false,
    val bearing: Double? = null
)

class NavigationModeController(
    private val context: PlatformContext
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val _uiState = MutableStateFlow(NavigationModeUiState())

    val uiState: StateFlow<NavigationModeUiState> = _uiState

    private var tripDetector: TripDetector? = null
    private var tripDetectorInitJob: Job? = null
    private var waypoints: List<NavigationWaypoint> = emptyList()
    private var tracePoints: List<GeoPoint> = emptyList()
    private var targetWaypointIndex: Int = 0

    fun start(journey: JourneyResult, tracePoints: List<GeoPoint> = emptyList()) {
        waypoints = journey.toNavigationWaypoints()
        this.tracePoints = if (tracePoints.isNotEmpty()) {
            tracePoints
        } else {
            waypoints.map { GeoPoint(it.lat, it.lon) }
        }
        targetWaypointIndex = 0
        NavigationModeStateStore.setNavigationActive(context, true)
        _uiState.value = buildState(
            journey = journey,
            location = null,
            isComplete = waypoints.isEmpty()
        )
        initializeCommonTripDetector()
    }

    fun stop() {
        NavigationModeStateStore.setNavigationActive(context, false)
        finalizeTripDetector()
        waypoints = emptyList()
        tracePoints = emptyList()
        targetWaypointIndex = 0
        _uiState.value = NavigationModeUiState()
    }

    fun dispose() {
        finalizeTripDetector()
        scope.cancel()
    }

    fun onLocationFix(location: GeoPoint) {
        val activeJourney = _uiState.value.journey ?: return
        tripDetector?.onLocationFix(location.latitude, location.longitude)
        updateTargetWaypoint(location)
        _uiState.value = buildState(
            journey = activeJourney,
            location = location,
            isComplete = targetWaypointIndex >= waypoints.size
        )
    }

    private fun updateTargetWaypoint(location: GeoPoint) {
        while (targetWaypointIndex < waypoints.size) {
            val target = waypoints[targetWaypointIndex]
            val distance = target.distanceMetersTo(location) ?: break
            if (distance > ARRIVAL_RADIUS_METERS) break
            targetWaypointIndex += 1
        }
    }

    private fun buildState(
        journey: JourneyResult,
        location: GeoPoint?,
        isComplete: Boolean
    ): NavigationModeUiState {
        val target = waypoints.getOrNull(targetWaypointIndex)
        val distance = if (location != null) target?.distanceMetersTo(location)?.roundToInt() else null
        val currentLegIndex = target?.legIndex
            ?: waypoints.lastOrNull()?.legIndex
            ?: 0

        // Calculate bearing based on the closest segment of the trace points
        val bearing = if (location != null && tracePoints.isNotEmpty()) {
            val segment = GeometryUtils.findNavigationAxisSegment(location, tracePoints)
            if (segment != null) {
                GeometryUtils.computeBearingDegrees(segment.first, segment.second)
            } else {
                null
            }
        } else {
            null
        }

        return NavigationModeUiState(
            isActive = true,
            journey = journey,
            currentLegIndex = currentLegIndex,
            nextStopName = target?.stopName,
            nextRouteName = target?.routeName,
            nextStopType = target?.type,
            distanceToNextMeters = distance,
            remainingMinutes = remainingMinutesUntil(journey.arrivalTime),
            instruction = instructionFor(target, isComplete),
            isComplete = isComplete,
            bearing = bearing
        )
    }

    private fun initializeCommonTripDetector() {
        if (NavigationModePlatform.handlesTripTelemetry) return
        if (tripDetector != null || tripDetectorInitJob != null) return
        if (TelemetryEmitter.optInManager()?.isOptedIn != true) return

        tripDetectorInitJob = scope.launch {
            val stops = runCatching { TransportCacheImpl(context).getStops() }.getOrNull().orEmpty()
            if (stops.isEmpty()) return@launch

            val telemetryConfig = runCatching { AppConfigLoader.getConfig().telemetry }.getOrNull()
            val detector = TripDetector(
                stops = stops,
                snapRadiusMeters = telemetryConfig?.tripSnapRadiusMeters ?: 100,
                samplingIntervalMs = (telemetryConfig?.tripSamplingSeconds ?: 30L) * 1000L
            )
            detector.start()
            tripDetector = detector
            tripDetectorInitJob = null
        }
    }

    private fun finalizeTripDetector() {
        tripDetectorInitJob?.cancel()
        tripDetectorInitJob = null
        val detector = tripDetector ?: return
        tripDetector = null
        scope.launch {
            runCatching { detector.stop().join() }
                .onFailure { Log.w(TAG, "Failed to finalize navigation trip", it) }
            detector.dispose()
        }
    }

    private fun JourneyResult.toNavigationWaypoints(): List<NavigationWaypoint> {
        val result = mutableListOf<NavigationWaypoint>()
        legs.forEachIndexed { index, leg ->
            if (result.isEmpty()) {
                result.add(leg.fromWaypoint(index, NavigationKeyStopType.START))
            }
            leg.intermediateStops.forEach { stop ->
                result.add(
                    NavigationWaypoint(
                        stopId = stop.stopName,
                        stopName = stop.stopName,
                        lat = stop.lat,
                        lon = stop.lon,
                        deadlineSeconds = stop.arrivalTime,
                        type = NavigationKeyStopType.TRANSFER,
                        legIndex = index,
                        routeName = leg.routeName.takeUnless { leg.isWalking }
                    )
                )
            }
            val type = if (index == legs.lastIndex) {
                NavigationKeyStopType.TERMINUS
            } else {
                NavigationKeyStopType.TRANSFER
            }
            result.add(leg.toWaypoint(index, type))
        }
        return result.filterNot { it.lat == 0.0 && it.lon == 0.0 }
            .dedupeConsecutive()
    }

    private fun JourneyLeg.fromWaypoint(index: Int, type: NavigationKeyStopType) =
        NavigationWaypoint(
            stopId = fromStopId,
            stopName = fromStopName,
            lat = fromLat,
            lon = fromLon,
            deadlineSeconds = departureTime,
            type = type,
            legIndex = index,
            routeName = routeName.takeUnless { isWalking }
        )

    private fun JourneyLeg.toWaypoint(index: Int, type: NavigationKeyStopType) =
        NavigationWaypoint(
            stopId = toStopId,
            stopName = toStopName,
            lat = toLat,
            lon = toLon,
            deadlineSeconds = arrivalTime,
            type = type,
            legIndex = index,
            routeName = routeName.takeUnless { isWalking }
        )

    private fun List<NavigationWaypoint>.dedupeConsecutive(): List<NavigationWaypoint> {
        val deduped = mutableListOf<NavigationWaypoint>()
        forEach { waypoint ->
            val previous = deduped.lastOrNull()
            if (previous?.stopName != waypoint.stopName || previous.deadlineSeconds != waypoint.deadlineSeconds) {
                deduped.add(waypoint)
            }
        }
        return deduped
    }

    private fun instructionFor(target: NavigationWaypoint?, isComplete: Boolean): String {
        if (isComplete) return "Vous êtes arrivé"
        return when (target?.type) {
            NavigationKeyStopType.START -> "Rejoignez ${target.stopName}"
            NavigationKeyStopType.TRANSFER -> "Prochain arrêt : ${target.stopName}"
            NavigationKeyStopType.TERMINUS -> "Destination : ${target.stopName}"
            null -> "Navigation en cours"
        }
    }

    private fun remainingMinutesUntil(arrivalSeconds: Int): Int {
        val now = GeometryUtils.currentTimeInSeconds()
        val normalizedArrival = if (arrivalSeconds < now) arrivalSeconds + SECONDS_PER_DAY else arrivalSeconds
        return ((normalizedArrival - now).coerceAtLeast(0) + 59) / 60
    }

    private data class NavigationWaypoint(
        val stopId: String,
        val stopName: String,
        val lat: Double,
        val lon: Double,
        val deadlineSeconds: Int,
        val type: NavigationKeyStopType,
        val legIndex: Int,
        val routeName: String?
    ) {
        fun distanceMetersTo(location: GeoPoint): Double? {
            if (lat == 0.0 && lon == 0.0) return null
            return GeometryUtils.distanceMeters(
                lat1 = location.latitude,
                lon1 = location.longitude,
                lat2 = lat,
                lon2 = lon
            )
        }
    }

    private companion object {
        const val TAG = "NavigationMode"
        const val ARRIVAL_RADIUS_METERS = 70
        const val SECONDS_PER_DAY = 86_400
    }
}
