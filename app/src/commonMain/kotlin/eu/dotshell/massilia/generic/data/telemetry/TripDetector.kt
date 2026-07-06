package eu.dotshell.massilia.generic.data.telemetry

import eu.dotshell.massilia.platform.ioDispatcher

import eu.dotshell.massilia.platform.Log
import eu.dotshell.massilia.generic.data.local_history.LocalTripRecord
import eu.dotshell.massilia.generic.data.models.geojson.StopFeature
import eu.dotshell.massilia.generic.utils.geo.GeometryUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import eu.dotshell.massilia.platform.randomId

/**
 * Detects the sequence of stops a user passes while navigation is active.
 *
 * The detector operates under a strict **snap-and-drop** discipline:
 *  1. Each incoming GPS fix is matched against the nearest known stop in the radius
 *     [snapRadiusMeters].
 *  2. If a stop is found AND it differs from the last emitted stop AND the user has been
 *     "stationary enough" for [stationaryThresholdMs] (sanity check against drive-bys), the
 *     stop_id is appended to the current trip's stop sequence.
 *  3. The raw latitude/longitude is **immediately discarded** — only the stop_id ever leaves
 *     this class.
 *
 * Lifecycle:
 *  - [start] begins a new trip and resets internal state.
 *  - [onLocationFix] is called from the foreground service's location callback at whatever
 *    cadence the OS provides. The detector internally rate-limits work to
 *    [samplingIntervalMs] to avoid burning CPU on rapid fixes.
 *  - [stop] emits the [TelemetryEvent.TripCompleted] event (if the trip has ≥ 2 stops) and
 *    appends a [LocalTripRecord] to the local history. After [stop], the detector is reset
 *    and can be reused for a new trip.
 *
 * Thread-safety: a single [Mutex] guards all mutations. Callers are free to invoke from any
 * thread; expensive work (the actual emission + local persistence) runs on
 * [ioDispatcher] via the internal [scope].
 */
class TripDetector(
    private val stops: List<StopFeature>,
    private val snapRadiusMeters: Int,
    private val samplingIntervalMs: Long,
    private val stationaryThresholdMs: Long = 10_000L
) {

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val mutex = Mutex()

    private var active: Boolean = false
    private var tripStartedAtMs: Long = 0
    private var stopsPassed: MutableList<String> = mutableListOf()
    private var lastFixAtMs: Long = 0
    private var lastStopSnapAtMs: Long = 0
    private var lastEmittedStop: String? = null
    private var pendingCandidate: String? = null
    private var pendingCandidateFirstSeenMs: Long = 0

    fun start() {
        scope.launch {
            mutex.withLock {
                active = true
                tripStartedAtMs = Clock.System.now().toEpochMilliseconds()
                stopsPassed = mutableListOf()
                lastFixAtMs = 0
                lastStopSnapAtMs = 0
                lastEmittedStop = null
                pendingCandidate = null
                pendingCandidateFirstSeenMs = 0
            }
        }
    }

    /**
     * Feed a GPS fix into the detector. The fix coordinates are used only to find the nearest
     * stop and are discarded immediately after.
     *
     * Rate-limited to one logical work item per [samplingIntervalMs] regardless of how
     * frequently the OS delivers fixes.
     */
    fun onLocationFix(lat: Double, lng: Double) {
        val now = Clock.System.now().toEpochMilliseconds()
        scope.launch {
            mutex.withLock {
                if (!active) return@withLock
                if (now - lastFixAtMs < samplingIntervalMs) return@withLock
                lastFixAtMs = now

                val nearest = findNearestStopWithinRadius(lat, lng) ?: run {
                    // No stop nearby — drop the pending candidate as the user is moving away
                    pendingCandidate = null
                    pendingCandidateFirstSeenMs = 0
                    return@withLock
                }

                if (nearest == lastEmittedStop) {
                    // Same stop as last emission, nothing to do
                    return@withLock
                }

                if (pendingCandidate != nearest) {
                    // New candidate — start its dwell timer
                    pendingCandidate = nearest
                    pendingCandidateFirstSeenMs = now
                    return@withLock
                }

                // Same candidate as previous tick — has the user dwelled long enough?
                if (now - pendingCandidateFirstSeenMs >= stationaryThresholdMs) {
                    stopsPassed.add(nearest)
                    lastEmittedStop = nearest
                    lastStopSnapAtMs = now
                    pendingCandidate = null
                    pendingCandidateFirstSeenMs = 0
                }
            }
        }
    }

    /**
     * Finalize the current trip. Emits [TelemetryEvent.TripCompleted] (if non-trivial) and a
     * [LocalTripRecord]. After this call the detector is idle until [start] is called again.
     *
     * Returns the [Job] running the emission so callers can `join()` it before [dispose] —
     * otherwise the supervisor cancellation in `dispose` would race against the persistence.
     */
    fun stop(): Job {
        return scope.launch {
            val maybeSnapshot: TripSnapshot? = mutex.withLock {
                if (!active) return@withLock null
                active = false
                val endedAt = Clock.System.now().toEpochMilliseconds()
                val passed = stopsPassed.toList()
                stopsPassed = mutableListOf()
                lastEmittedStop = null
                pendingCandidate = null
                pendingCandidateFirstSeenMs = 0
                if (passed.size < 2) {
                    null
                } else {
                    TripSnapshot(
                        startedAtMs = tripStartedAtMs,
                        endedAtMs = endedAt,
                        stopsPassed = passed
                    )
                }
            }
            val snapshot = maybeSnapshot ?: return@launch

            emitTripCompleted(snapshot)
            persistLocally(snapshot)
        }
    }

    private fun findNearestStopWithinRadius(lat: Double, lng: Double): String? {
        // Coarse pre-filter using squared distance in degrees to avoid the trigonometric
        // distance computation for far-away stops. ~1 degree ≈ 111 km, our radius is at most
        // a few hundred meters, so we use a generous 0.01 degree bound (~1.1 km).
        val degreeBound = 0.01
        val degreeBoundSq = degreeBound * degreeBound

        var bestId: String? = null
        var bestDistance = snapRadiusMeters.toDouble()

        for (stop in stops) {
            val coords = stop.geometry.coordinates
            if (coords.size < 2) continue
            val sLng = coords[0]
            val sLat = coords[1]
            val sq = GeometryUtils.squaredDistance(lat, lng, sLat, sLng)
            if (sq > degreeBoundSq) continue

            val d = GeometryUtils.distanceMeters(lat, lng, sLat, sLng)
            if (d <= bestDistance) {
                bestDistance = d
                bestId = stop.properties.id.toString()
            }
        }
        return bestId
    }

    private fun emitTripCompleted(trip: TripSnapshot) {
        TelemetryEmitter.emit(
            TelemetryEvent.TripCompleted(
                eventId = randomId(),
                at = Clock.System.now().toString(),
                startedAt = Instant.fromEpochMilliseconds(trip.startedAtMs).toString(),
                endedAt = Instant.fromEpochMilliseconds(trip.endedAtMs).toString(),
                stopsPassed = trip.stopsPassed
            )
        )
    }

    private suspend fun persistLocally(trip: TripSnapshot) {
        val storage = TelemetryEmitter.localHistory() ?: return
        // linesUsed is left empty for now — correlating stop sequences to GTFS routes will be
        // computed lazily by the LocalProfileComputer in a future iteration. Storing the
        // sequence is enough for the user-facing "Mes trajets" view.
        runCatching {
            storage.appendTrip(
                LocalTripRecord(
                    startedAtEpochMs = trip.startedAtMs,
                    endedAtEpochMs = trip.endedAtMs,
                    stopsPassed = trip.stopsPassed,
                    linesUsed = emptyList()
                )
            )
        }.onFailure { Log.w(TAG, "Failed to persist local trip", it) }
    }

    /**
     * Cancel the detector entirely. Call from the service's onDestroy. After this point the
     * detector cannot be reused.
     */
    fun dispose() {
        scope.cancel()
    }

    private data class TripSnapshot(
        val startedAtMs: Long,
        val endedAtMs: Long,
        val stopsPassed: List<String>
    )

    companion object {
        private const val TAG = "TripDetector"
    }
}
