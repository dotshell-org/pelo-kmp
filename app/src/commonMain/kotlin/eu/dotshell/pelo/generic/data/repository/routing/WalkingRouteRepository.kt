package eu.dotshell.pelo.generic.data.repository.routing

import eu.dotshell.pelo.generic.data.network.routing.OsrmRouteResponse
import eu.dotshell.pelo.generic.data.network.routing.OsrmWalkingClient
import eu.dotshell.pelo.platform.Log
import eu.dotshell.pelo.platform.ioDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.Volatile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Street-network walking data from the public OSRM foot router, with two uses:
 * - [getWalkingPath]/[getWalkingDistanceMeters]: one pair, real polyline + distance (map display
 *   and the direct-walk duration).
 * - [getWalkingDistances]: one-to-many distances in a single table request (access/egress walk
 *   times fed into the raptor optimization).
 *
 * Failures (offline, timeouts) return null and callers fall back to the offline great-circle
 * model. Successful results are memoized (itinerary recalcs reuse the same endpoints).
 */
class WalkingRouteRepository private constructor() {

    private val client = OsrmWalkingClient()
    private val cacheMutex = Mutex()
    private val routeCache = LinkedHashMap<String, WalkRoute>()
    private val tableCache = LinkedHashMap<String, List<Double?>>()

    private class WalkRoute(val path: List<DoubleArray>, val distanceMeters: Double)

    /**
     * @return the street path as [lon, lat] points including the exact endpoints,
     *         or null when unavailable (caller draws a straight line).
     */
    suspend fun getWalkingPath(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double
    ): List<DoubleArray>? = getWalkingRoute(fromLat, fromLon, toLat, toLon)?.path

    /**
     * @return the real street distance in meters, or null when unavailable.
     */
    suspend fun getWalkingDistanceMeters(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double
    ): Double? = getWalkingRoute(fromLat, fromLon, toLat, toLon)?.distanceMeters

    private suspend fun getWalkingRoute(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double
    ): WalkRoute? {
        // Trivially short walks render fine as a straight segment; skip the network round-trip
        if (approxDistanceMeters(fromLat, fromLon, toLat, toLon) < MIN_FETCH_DISTANCE_METERS) return null

        val key = routeKey(fromLat, fromLon, toLat, toLon)
        cacheMutex.withLock { routeCache[key] }?.let { return it }

        return withContext(ioDispatcher) {
            try {
                withTimeout(REQUEST_TIMEOUT_MS) {
                    val response = client.route(fromLat, fromLon, toLat, toLon)
                    val path = buildWalkingPath(response, fromLat, fromLon, toLat, toLon)
                    val distance = response.routes.firstOrNull()?.distance
                    if (path != null && distance != null) {
                        val route = WalkRoute(path, distance)
                        cacheMutex.withLock {
                            if (routeCache.size >= MAX_CACHE_ENTRIES) {
                                routeCache.remove(routeCache.keys.first())
                            }
                            routeCache[key] = route
                        }
                        route
                    } else null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Walking route fetch failed: ${e.message}")
                null
            }
        }
    }

    /**
     * Real street distances (meters) from a point to each of [targets] (lat/lon pairs) in one
     * table request. The result is parallel to [targets]; a null entry means that target is
     * unreachable on foot. Returns null when the service is unavailable.
     */
    suspend fun getWalkingDistances(
        fromLat: Double,
        fromLon: Double,
        targets: List<Pair<Double, Double>>
    ): List<Double?>? {
        if (targets.isEmpty()) return emptyList()

        val key = tableKey(fromLat, fromLon, targets)
        cacheMutex.withLock { tableCache[key] }?.let { return it }

        return withContext(ioDispatcher) {
            try {
                withTimeout(REQUEST_TIMEOUT_MS) {
                    val response = client.table(fromLat, fromLon, targets)
                    val row = response.distances.firstOrNull()
                    if (response.code == "Ok" && row != null && row.size == targets.size + 1) {
                        // row[0] is source->source; targets start at index 1
                        val distances = row.drop(1)
                        cacheMutex.withLock {
                            if (tableCache.size >= MAX_TABLE_CACHE_ENTRIES) {
                                tableCache.remove(tableCache.keys.first())
                            }
                            tableCache[key] = distances
                        }
                        distances
                    } else null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Walking table fetch failed: ${e.message}")
                null
            }
        }
    }

    companion object {
        private const val TAG = "WalkingRouteRepository"
        private const val REQUEST_TIMEOUT_MS = 5_000L
        private const val MIN_FETCH_DISTANCE_METERS = 40.0
        private const val MAX_CACHE_ENTRIES = 64
        private const val MAX_TABLE_CACHE_ENTRIES = 16

        @Volatile
        private var INSTANCE: WalkingRouteRepository? = null

        fun getInstance(): WalkingRouteRepository {
            return INSTANCE ?: WalkingRouteRepository().also { INSTANCE = it }
        }

        private fun r(v: Double): Long = (v * 100_000).roundToLong() // ~1 m resolution

        private fun routeKey(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): String =
            "${r(fromLat)},${r(fromLon)}->${r(toLat)},${r(toLon)}"

        private fun tableKey(fromLat: Double, fromLon: Double, targets: List<Pair<Double, Double>>): String =
            buildString {
                append(r(fromLat)).append(',').append(r(fromLon)).append(">>")
                for ((lat, lon) in targets) {
                    append(r(lat)).append(',').append(r(lon)).append(';')
                }
            }

        private fun approxDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val kmPerDegLat = 111.32
            val dLat = abs(lat1 - lat2) * kmPerDegLat
            val dLon = abs(lon1 - lon2) * kmPerDegLat * cos(lat1 * PI / 180.0)
            return sqrt(dLat * dLat + dLon * dLon) * 1000.0
        }
    }
}

/**
 * Extracts the drawable path from an OSRM response: the street geometry stitched to the exact
 * endpoints (OSRM snaps to the nearest road, so the raw route may not touch the stop/address
 * markers). Null when the response is unusable.
 */
internal fun buildWalkingPath(
    response: OsrmRouteResponse,
    fromLat: Double,
    fromLon: Double,
    toLat: Double,
    toLon: Double
): List<DoubleArray>? {
    if (response.code != "Ok") return null
    val coordinates = response.routes.firstOrNull()?.geometry?.coordinates ?: return null
    val street = coordinates.filter { it.size >= 2 }.map { doubleArrayOf(it[0], it[1]) }
    if (street.size < 2) return null

    return buildList(street.size + 2) {
        add(doubleArrayOf(fromLon, fromLat))
        addAll(street)
        add(doubleArrayOf(toLon, toLat))
    }
}
