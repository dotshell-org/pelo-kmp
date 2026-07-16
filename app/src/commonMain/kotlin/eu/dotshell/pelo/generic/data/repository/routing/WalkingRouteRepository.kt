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
 * Street-following walking paths for DISPLAY of walk legs (map polylines). Routing decisions and
 * walk durations stay on raptor's offline haversine model — this only shapes the drawn line.
 *
 * Failures (offline, timeouts) return null and the caller falls back to a straight segment.
 * Successful paths are memoized (walk legs are redrawn on every journey selection change).
 */
class WalkingRouteRepository private constructor() {

    private val client = OsrmWalkingClient()
    private val cacheMutex = Mutex()
    private val cache = LinkedHashMap<String, List<DoubleArray>>()

    /**
     * Cache-only lookup: the street path if a previous fetch already resolved it, else null
     * immediately (no network). Lets the map paint instantly and refine once fetches complete.
     */
    suspend fun peekWalkingPath(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double
    ): List<DoubleArray>? =
        cacheMutex.withLock { cache[cacheKey(fromLat, fromLon, toLat, toLon)] }

    /**
     * @return the street path as [lon, lat] points including the exact endpoints,
     *         or null when unavailable (caller draws a straight line).
     */
    suspend fun getWalkingPath(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double
    ): List<DoubleArray>? {
        // Trivially short walks render fine as a straight segment; skip the network round-trip
        if (approxDistanceMeters(fromLat, fromLon, toLat, toLon) < MIN_FETCH_DISTANCE_METERS) return null

        val key = cacheKey(fromLat, fromLon, toLat, toLon)
        cacheMutex.withLock { cache[key] }?.let { return it }

        return withContext(ioDispatcher) {
            try {
                withTimeout(REQUEST_TIMEOUT_MS) {
                    val response = client.route(fromLat, fromLon, toLat, toLon)
                    val path = buildWalkingPath(response, fromLat, fromLon, toLat, toLon)
                    if (path != null) {
                        cacheMutex.withLock {
                            if (cache.size >= MAX_CACHE_ENTRIES) {
                                cache.remove(cache.keys.first())
                            }
                            cache[key] = path
                        }
                    }
                    path
                }
            } catch (e: Exception) {
                Log.w(TAG, "Walking route fetch failed: ${e.message}")
                null
            }
        }
    }

    companion object {
        private const val TAG = "WalkingRouteRepository"
        private const val REQUEST_TIMEOUT_MS = 5_000L
        private const val MIN_FETCH_DISTANCE_METERS = 40.0
        private const val MAX_CACHE_ENTRIES = 64

        @Volatile
        private var INSTANCE: WalkingRouteRepository? = null

        fun getInstance(): WalkingRouteRepository {
            return INSTANCE ?: WalkingRouteRepository().also { INSTANCE = it }
        }

        private fun cacheKey(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): String {
            fun r(v: Double): Long = (v * 100_000).roundToLong() // ~1 m resolution
            return "${r(fromLat)},${r(fromLon)}->${r(toLat)},${r(toLon)}"
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
