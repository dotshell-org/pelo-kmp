package eu.dotshell.pelo.generic.utils.navigation

import eu.dotshell.pelo.generic.data.models.navigation.NavigationProgress
import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.JourneyResult
import eu.dotshell.pelo.generic.utils.geo.GeometryUtils
import eu.dotshell.pelo.generic.utils.location.GeoPoint
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Tracks where the traveller is along [journey], from location fixes and the clock.
 *
 * Two rules drive the whole thing:
 *
 *  - **Progress is monotonic.** A fix can confirm or accelerate it, never rewind it. Ranking the
 *    nearest stop on every update — the previous approach — let one bad fix, or a route that
 *    passes near itself, silently throw the guidance back to an earlier leg.
 *  - **The timetable is the fallback, not the master.** With a trusted fix, position decides.
 *    Without one (underground, tunnel, permission revoked) the schedule advances progress on its
 *    own, so a metro ride still moves through its stops instead of freezing at the entrance.
 */
class NavigationProgressTracker(private val journey: JourneyResult) {

    private val chain: List<LegChain> = journey.buildChain()
    private var progress: NavigationProgress = initialProgress()
    /** When the current uninterrupted off-route stretch began, in normalised journey time. */
    private var offRouteSince: Int? = null

    /** The current snapshot, without folding in any new observation. */
    fun current(): NavigationProgress = progress

    /**
     * Fold one observation into the progress.
     *
     * @param location the most recent fix, or null when none is fresh enough to trust.
     * @param nowSeconds wall clock, in seconds since midnight (see [GeometryUtils.currentTimeInSeconds]).
     */
    fun update(location: GeoPoint?, nowSeconds: Int): NavigationProgress {
        if (chain.isEmpty()) return progress

        val now = normalize(nowSeconds)
        val snap = location?.let { nearestChainPoint(it) }
        val trusted = snap != null && snap.distanceMeters <= SNAP_RADIUS_METERS

        var position = Position(progress.legIndex, progress.stopIndex)

        if (trusted) {
            position = maxOf(position, Position(snap.legIndex, snap.stopIndex))
        } else {
            // No usable fix: let the schedule carry us, otherwise the guidance would sit on the
            // boarding stop for the whole ride.
            position = maxOf(position, scheduledPosition(now))
        }

        val legChain = chain[position.legIndex]
        val distanceToNext = location?.let { distanceToNextPoint(it, position) }
        val arrived = hasArrived(position, location, now)

        // Off-route needs a fix to establish: with no position at all we are dead-reckoning, not
        // astray, and offering to replan on that basis would be guessing.
        val isOffRoute = snap != null && !trusted
        offRouteSince = if (isOffRoute) (offRouteSince ?: now) else null

        progress = NavigationProgress(
            legIndex = position.legIndex,
            stopIndex = position.stopIndex,
            stopCount = legChain.points.size,
            isArrived = arrived,
            distanceToNextMeters = distanceToNext,
            isOffRoute = isOffRoute,
            offRouteSeconds = offRouteSince?.let { (now - it).coerceAtLeast(0) } ?: 0,
            isDeadReckoning = location == null,
        )
        return progress
    }

    private fun initialProgress(): NavigationProgress {
        val first = chain.firstOrNull() ?: return NavigationProgress(isArrived = journey.legs.isEmpty())
        return NavigationProgress(legIndex = first.legIndex, stopIndex = 0, stopCount = first.points.size)
    }

    /** Furthest position the timetable says we must already have reached by [now]. */
    private fun scheduledPosition(now: Int): Position {
        var best = Position(chain.first().legIndex, 0)
        chain.forEach { leg ->
            leg.points.forEachIndexed { index, point ->
                if (now >= point.timeSeconds) {
                    best = maxOf(best, Position(leg.legIndex, index))
                }
            }
        }
        // Standing at a leg's terminus and the next leg has started: move on to it.
        val legPos = chain.indexOfFirst { it.legIndex == best.legIndex }
        if (best.stopIndex >= chain[legPos].points.lastIndex && legPos < chain.lastIndex) {
            val next = chain[legPos + 1]
            if (now >= next.points.first().timeSeconds) return Position(next.legIndex, 0)
        }
        return best
    }

    private fun hasArrived(position: Position, location: GeoPoint?, now: Int): Boolean {
        val last = chain.last()
        if (position.legIndex != last.legIndex) return false
        if (position.stopIndex < last.points.lastIndex) return false

        val destination = last.points.last()
        if (location != null && destination.hasCoordinates) {
            val metres = GeometryUtils.distanceMeters(
                lat1 = location.latitude,
                lon1 = location.longitude,
                lat2 = destination.lat,
                lon2 = destination.lon,
            )
            return metres <= ARRIVAL_RADIUS_METERS
        }
        // No fix to confirm with: fall back on the timetable, plus a grace period so a slightly
        // early clock does not declare arrival while the traveller is still aboard.
        return now >= destination.timeSeconds + ARRIVAL_GRACE_SECONDS
    }

    private fun distanceToNextPoint(location: GeoPoint, position: Position): Int? {
        val legPos = chain.indexOfFirst { it.legIndex == position.legIndex }
        if (legPos == -1) return null
        val leg = chain[legPos]
        val next = leg.points.getOrNull(position.stopIndex + 1)
            ?: chain.getOrNull(legPos + 1)?.points?.firstOrNull()
            ?: leg.points.last()
        if (!next.hasCoordinates) return null
        return GeometryUtils.distanceMeters(
            lat1 = location.latitude,
            lon1 = location.longitude,
            lat2 = next.lat,
            lon2 = next.lon,
        ).roundToInt()
    }

    private fun nearestChainPoint(location: GeoPoint): Snap? {
        var best: Snap? = null
        chain.forEach { leg ->
            leg.points.forEachIndexed { index, point ->
                if (!point.hasCoordinates) return@forEachIndexed
                val squared = GeometryUtils.squaredMeters(
                    lat1 = location.latitude,
                    lon1 = location.longitude,
                    lat2 = point.lat,
                    lon2 = point.lon,
                )
                if (best == null || squared < best!!.squaredMeters) {
                    best = Snap(leg.legIndex, index, squared)
                }
            }
        }
        return best
    }

    /**
     * GTFS service days run past midnight ("25:30" is 01:30), and the wall clock wraps at
     * midnight. Normalising every instant into the journey's own day makes the two comparable.
     */
    private fun normalize(timeSeconds: Int): Int {
        var normalized = timeSeconds
        val reference = journey.departureTime
        while (normalized < reference - DAY_SECONDS / 2) normalized += DAY_SECONDS
        while (normalized > reference + DAY_SECONDS / 2) normalized -= DAY_SECONDS
        return normalized
    }

    private fun JourneyResult.buildChain(): List<LegChain> = legs.mapIndexed { index, leg ->
        val points = buildList {
            add(ChainPoint(leg.fromStopName, leg.fromLat, leg.fromLon, normalize(leg.departureTime)))
            leg.intermediateStops.forEach { stop ->
                add(ChainPoint(stop.stopName, stop.lat, stop.lon, normalize(stop.arrivalTime)))
            }
            add(ChainPoint(leg.toStopName, leg.toLat, leg.toLon, normalize(leg.arrivalTime)))
        }
        LegChain(legIndex = index, points = points)
    }

    private data class LegChain(val legIndex: Int, val points: List<ChainPoint>)

    private data class ChainPoint(
        val stopName: String,
        val lat: Double,
        val lon: Double,
        val timeSeconds: Int,
    ) {
        val hasCoordinates: Boolean
            get() = lat in -90.0..90.0 && lon in -180.0..180.0 && (lat != 0.0 || lon != 0.0)
    }

    private data class Snap(val legIndex: Int, val stopIndex: Int, val squaredMeters: Double) {
        val distanceMeters: Double get() = sqrt(squaredMeters)
    }

    private data class Position(val legIndex: Int, val stopIndex: Int) : Comparable<Position> {
        override fun compareTo(other: Position): Int =
            compareValuesBy(this, other, Position::legIndex, Position::stopIndex)
    }

    private companion object {
        /** Beyond this, a fix is treated as off-route rather than snapped to the nearest stop. */
        const val SNAP_RADIUS_METERS = 250.0
        const val ARRIVAL_RADIUS_METERS = 80.0
        const val ARRIVAL_GRACE_SECONDS = 60
        const val DAY_SECONDS = 24 * 3600
    }
}
