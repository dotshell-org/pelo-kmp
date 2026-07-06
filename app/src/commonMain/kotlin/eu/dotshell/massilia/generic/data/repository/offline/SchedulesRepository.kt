package eu.dotshell.massilia.generic.data.repository.offline

import eu.dotshell.massilia.generic.data.models.search.LineSearchResult
import eu.dotshell.massilia.generic.data.models.search.StationSearchResult
import eu.dotshell.massilia.generic.data.repository.api.SchedulesRepository as ApiSchedulesRepository
import eu.dotshell.massilia.generic.data.repository.itinerary.itinerary.RaptorRepository
import eu.dotshell.massilia.platform.Log
import eu.dotshell.massilia.platform.PlatformContext
import eu.dotshell.massilia.platform.ioDispatcher
import kotlin.concurrent.Volatile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SchedulesRepository private constructor(context: PlatformContext) : ApiSchedulesRepository {

    private val raptorRepository = RaptorRepository.getInstance(context)

    companion object {
        @Volatile
        private var INSTANCE: SchedulesRepository? = null

        private const val SEARCH_CACHE_SIZE = 30

        // Manual LRU over an insertion-ordered LinkedHashMap (no android.util.LruCache in
        // commonMain). Guarded by [searchCacheMutex] for the suspend read/write paths.
        private val searchCacheMutex = Mutex()
        private val searchCache = LinkedHashMap<String, List<StationSearchResult>>()

        /**
         * Singleton. No `synchronized` in commonMain — a @Volatile double-check suffices for a
         * startup singleton (a rare race only creates a discarded extra instance). Callers pass an
         * application-scoped context.
         */
        fun getInstance(context: PlatformContext): SchedulesRepository {
            return INSTANCE ?: SchedulesRepository(context).also { INSTANCE = it }
        }

        fun trimCaches(level: Int) {
            // 20 = TRIM_MEMORY_UI_HIDDEN, 40 = TRIM_MEMORY_BACKGROUND (android ComponentCallbacks2).
            // Best-effort and lock-free (a memory hint); the Raptor data is the source of truth.
            if (level >= 20) {
                runCatching { searchCache.clear() }
            }
        }

        private suspend fun getCachedSearch(key: String): List<StationSearchResult>? =
            searchCacheMutex.withLock {
                val value = searchCache[key]
                if (value != null) {
                    searchCache.remove(key) // re-insert to mark as most-recently-used
                    searchCache[key] = value
                }
                value
            }

        private suspend fun putCachedSearch(key: String, value: List<StationSearchResult>) =
            searchCacheMutex.withLock {
                searchCache.remove(key)
                searchCache[key] = value
                while (searchCache.size > SEARCH_CACHE_SIZE) {
                    val eldest = searchCache.keys.firstOrNull() ?: break
                    searchCache.remove(eldest)
                }
            }
    }

    fun warmupDatabase() {
        // Binary-only mode: warm up by initializing raptor assets.
    }

    override suspend fun searchStopsByName(query: String): List<StationSearchResult> {
        val cacheKey = query.trim().lowercase()
        getCachedSearch(cacheKey)?.let { return it }

        val (assetsAvailable, rawResults) = withContext(ioDispatcher) {
            val assetsAvail = raptorRepository.checkAssetsAvailable()
            val stops = raptorRepository.searchStopsByName(query)
            val results = stops.map { stop ->
                val desserte = raptorRepository.getDesserteForStop(stop.name).orEmpty()
                val lines = if (desserte.isEmpty() || desserte.equals("UNKNOWN", ignoreCase = true)) {
                    if (!assetsAvail) {
                        Log.w("SchedulesRepository", "Stop ${stop.name} has no desserte data - Raptor assets may be missing")
                        emptyList()
                    } else {
                        emptyList()
                    }
                } else {
                    desserte.split(',')
                        .mapNotNull { part ->
                            val token = part.trim()
                            if (token.isEmpty()) null else token.substringBefore(':').trim()
                        }
                        .filter { it.isNotEmpty() }
                        .distinct()
                }
                StationSearchResult(
                    stopName = stop.name,
                    lines = lines,
                    stopId = stop.id
                )
            }
            assetsAvail to results
        }

        // Group homonymous platforms/quays under one visual stop entry in search results.
        val results = rawResults
            .groupBy { it.stopName.trim().lowercase() }
            .values
            .map { group ->
                val representative = group.first()
                StationSearchResult(
                    stopName = representative.stopName,
                    lines = group.flatMap { it.lines }.distinct(),
                    stopId = representative.stopId
                )
            }
            .take(50)

        if (cacheKey.length >= 2 && results.isNotEmpty()) {
            putCachedSearch(cacheKey, results)
        }
        return results
    }

    override fun searchLinesByName(query: String): List<LineSearchResult> {
        return raptorRepository.searchLinesByName(query)
    }

    override fun getAllRouteNames(): List<String> {
        return raptorRepository.searchLinesByName("").map { it.lineName }.distinct().sorted()
    }

    fun getAllBusLikeRouteNames(): List<String> {
        return getAllRouteNames()
    }

    override fun getHeadsigns(routeName: String): Map<Int, String> {
        return raptorRepository.getHeadsigns(routeName)
    }

    override fun getDesserteForStop(stopName: String): String? {
        return raptorRepository.getDesserteForStop(stopName)
    }

    override fun getStopSequences(routeName: String, directionId: Int): List<Pair<String, Int>> {
        return raptorRepository.getStopSequences(routeName, directionId)
    }

    override fun getSchedules(
        lineName: String,
        stopName: String,
        directionId: Int,
        isSchoolHoliday: Boolean,
        isPublicHoliday: Boolean
    ): List<String> {
        return raptorRepository.getSchedules(
            lineName = lineName,
            stopName = stopName,
            directionId = directionId,
            isSchoolHoliday = isSchoolHoliday,
            isPublicHoliday = isPublicHoliday
        )
    }
}
