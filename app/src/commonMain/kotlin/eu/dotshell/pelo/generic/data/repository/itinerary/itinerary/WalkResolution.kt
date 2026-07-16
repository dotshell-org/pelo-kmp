package eu.dotshell.pelo.generic.data.repository.itinerary.itinerary

import io.raptor.StopWalk
import io.raptor.model.Stop
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Pure helpers for upgrading Point endpoints with real street walking distances
 * (RaptorRepository + OSRM table). Split out for unit testing.
 */

/**
 * Stops within [radiusMeters] (great-circle approximation) of the point, closest first,
 * capped at [maxCandidates] — the candidate set sent to the pedestrian router.
 */
internal fun nearbyStopCandidates(
    stops: List<Stop>,
    lat: Double,
    lon: Double,
    radiusMeters: Double,
    maxCandidates: Int
): List<Stop> {
    val candidates = ArrayList<Pair<Stop, Double>>()
    for (stop in stops) {
        val distance = approxWalkDistanceMeters(lat, lon, stop.lat, stop.lon)
        if (distance <= radiusMeters) {
            candidates.add(stop to distance)
        }
    }
    candidates.sortBy { it.second }
    return candidates.take(maxCandidates).map { it.first }
}

/**
 * Converts the router's real street distances into raptor [StopWalk]s using the configured
 * walking speed (no detour factor — these are real distances). Unreachable targets (null) and
 * absurdly long detours (> [maxWalkDistanceMeters], e.g. a river between the point and a stop
 * 400 m away as the crow flies) are dropped.
 */
internal fun buildStopWalks(
    candidates: List<Stop>,
    distancesMeters: List<Double?>,
    speedMetersPerSecond: Double,
    maxWalkDistanceMeters: Double
): List<StopWalk> =
    candidates.mapIndexedNotNull { index, stop ->
        val distance = distancesMeters.getOrNull(index) ?: return@mapIndexedNotNull null
        if (distance > maxWalkDistanceMeters) return@mapIndexedNotNull null
        StopWalk(stopId = stop.id, walkSeconds = ceil(distance / speedMetersPerSecond).toInt())
    }

internal fun approxWalkDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val kmPerDegLat = 111.32
    val dLat = abs(lat1 - lat2) * kmPerDegLat
    val dLon = abs(lon1 - lon2) * kmPerDegLat * cos(lat1 * PI / 180.0)
    return sqrt(dLat * dLat + dLon * dLon) * 1000.0
}
