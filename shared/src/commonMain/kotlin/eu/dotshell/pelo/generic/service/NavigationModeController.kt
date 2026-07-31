package eu.dotshell.pelo.generic.service

import eu.dotshell.pelo.generic.data.cache.TransportCacheImpl
import eu.dotshell.pelo.generic.data.config.AppConfigLoader
import eu.dotshell.pelo.generic.data.models.navigation.NavigationProgress
import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.JourneyResult
import eu.dotshell.pelo.generic.data.telemetry.TelemetryEmitter
import eu.dotshell.pelo.generic.data.telemetry.TripDetector
import eu.dotshell.pelo.generic.utils.geo.GeometryUtils
import eu.dotshell.pelo.generic.utils.location.GeoPoint
import eu.dotshell.pelo.generic.utils.navigation.NavigationProgressTracker
import eu.dotshell.pelo.platform.Log
import eu.dotshell.pelo.platform.PlatformContext
import eu.dotshell.pelo.platform.ioDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * The live navigation session: what is being navigated, where the traveller is on it, and which
 * way the route runs from here. Everything the screen shows is derived from this by
 * `buildNavigationModeUiState` — this type deliberately carries no presentation strings.
 */
data class NavigationSession(
    val isActive: Boolean = false,
    val journey: JourneyResult? = null,
    val progress: NavigationProgress = NavigationProgress(),
    /** Route heading in degrees from north, smoothed; null until the route direction is known. */
    val bearing: Double? = null,
    /** Most recent fix, however old. Null when none has ever arrived. */
    val location: GeoPoint? = null,
    /** A fix arrived recently enough to be trusted for guidance. */
    val hasFreshFix: Boolean = false,
    /** Seconds since midnight, refreshed once a second so countdowns actually count down. */
    val nowSeconds: Int = 0,
)

class NavigationModeController(
    private val context: PlatformContext,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val _session = MutableStateFlow(NavigationSession())

    val session: StateFlow<NavigationSession> = _session

    private var tripDetector: TripDetector? = null
    private var tripDetectorInitJob: Job? = null
    private var tickerJob: Job? = null

    private var tracker: NavigationProgressTracker? = null
    private var tracePoints: List<GeoPoint> = emptyList()
    private var lastLocation: GeoPoint? = null
    private var lastFixAt: TimeMark? = null
    private var smoothedBearing: Double? = null

    fun start(journey: JourneyResult, tracePoints: List<GeoPoint> = emptyList()) {
        this.tracePoints = tracePoints.ifEmpty { journey.fallbackTrace() }
        tracker = NavigationProgressTracker(journey)
        lastLocation = null
        lastFixAt = null
        smoothedBearing = null

        NavigationModeStateStore.setNavigationActive(context, true)
        _session.value = NavigationSession(
            isActive = true,
            journey = journey,
            progress = tracker?.current() ?: NavigationProgress(),
            nowSeconds = GeometryUtils.currentTimeInSeconds(),
        )
        startTicker()
        initializeCommonTripDetector()
    }

    fun stop() {
        tickerJob?.cancel()
        tickerJob = null
        NavigationModeStateStore.setNavigationActive(context, false)
        finalizeTripDetector()
        tracker = null
        tracePoints = emptyList()
        lastLocation = null
        lastFixAt = null
        smoothedBearing = null
        _session.value = NavigationSession()
    }

    fun dispose() {
        tickerJob?.cancel()
        tickerJob = null
        // Leaving composition is not "the user finished their trip", but the in-memory session is
        // gone either way: leaving the flag set would strand the foreground service with nothing
        // able to switch it off.
        NavigationModeStateStore.setNavigationActive(context, false)
        finalizeTripDetector()
        scope.cancel()
    }

    /**
     * Record a fix. It is not folded into the session here: the ticker is the single writer, so
     * fixes arriving on the UI thread cannot interleave with it inside the progress tracker.
     * At one refresh a second the extra latency is invisible next to the camera animation.
     */
    fun onLocationFix(location: GeoPoint) {
        tripDetector?.onLocationFix(location.latitude, location.longitude)
        if (!_session.value.isActive) return
        lastLocation = location
        lastFixAt = timeSource.markNow()
    }

    /**
     * Recompute the session from the latest fix and the clock. Driven by the ticker alone, once a
     * second, so the countdown counts and a lost signal degrades to timetable guidance.
     */
    private fun refresh() {
        val current = _session.value
        val journey = current.journey ?: return
        val tracker = tracker ?: return

        val now = GeometryUtils.currentTimeInSeconds()
        val fixAge = lastFixAt?.elapsedNow()
        val isFresh = fixAge != null && fixAge.inWholeMilliseconds <= FIX_FRESHNESS_MS
        val usableLocation = lastLocation?.takeIf { isFresh }

        val progress = tracker.update(usableLocation, now)

        _session.value = current.copy(
            journey = journey,
            progress = progress,
            bearing = updateBearing(usableLocation),
            location = lastLocation,
            hasFreshFix = isFresh,
            nowSeconds = now,
        )
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(TICK_INTERVAL_MS)
                if (!_session.value.isActive) break
                refresh()
            }
        }
    }

    /**
     * Follow the route's heading, damped. Two things would otherwise make the map lurch: crossing
     * a segment boundary changes the raw heading in one step, and a heading that crosses the
     * 360°/0° seam interpolates the long way round unless it is walked through the short arc.
     */
    private fun updateBearing(location: GeoPoint?): Double? {
        if (location == null || tracePoints.size < 2) return smoothedBearing
        val segment = GeometryUtils.findNavigationAxisSegment(location, tracePoints)
            ?: return smoothedBearing
        val target = GeometryUtils.computeBearingDegrees(segment.first, segment.second)

        val previous = smoothedBearing
        smoothedBearing = if (previous == null) {
            target
        } else {
            val delta = GeometryUtils.shortestAngleDelta(previous, target)
            (previous + delta * BEARING_SMOOTHING).mod(360.0)
        }
        return smoothedBearing
    }

    /** Straight lines between the journey's stops — a usable heading source when no shape loaded. */
    private fun JourneyResult.fallbackTrace(): List<GeoPoint> = legs.flatMap { leg ->
        buildList {
            add(GeoPoint(leg.fromLat, leg.fromLon))
            leg.intermediateStops.forEach { add(GeoPoint(it.lat, it.lon)) }
            add(GeoPoint(leg.toLat, leg.toLon))
        }
    }.filterNot { it.latitude == 0.0 && it.longitude == 0.0 }

    private fun initializeCommonTripDetector() {
        if (NavigationModePlatform.handlesTripTelemetry) return
        if (tripDetector != null || tripDetectorInitJob != null) return
        if (TelemetryEmitter.optInManager()?.isOptedIn != true) return

        tripDetectorInitJob = scope.launch {
            val stops = runCatching { TransportCacheImpl.getInstance(context).getStops() }.getOrNull().orEmpty()
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

    private companion object {
        const val TAG = "NavigationMode"
        const val TICK_INTERVAL_MS = 1_000L
        /** Past this age a fix stops driving guidance and the timetable takes over. */
        const val FIX_FRESHNESS_MS = 30_000L
        const val BEARING_SMOOTHING = 0.35
    }
}
