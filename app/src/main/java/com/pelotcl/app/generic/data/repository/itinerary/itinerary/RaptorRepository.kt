package com.pelotcl.app.generic.data.repository.itinerary.itinerary

import android.content.Context
import android.util.Log
import android.util.LruCache
import com.pelotcl.app.generic.data.cache.journey.JourneyCache
import com.pelotcl.app.generic.data.repository.itinerary.holiday.HolidayPeriod
import com.pelotcl.app.generic.data.repository.itinerary.holiday.HolidaysData
import com.pelotcl.app.generic.data.models.search.LineSearchResult
import com.pelotcl.app.generic.service.TransportServiceProvider
import com.pelotcl.app.generic.utils.date.HolidayDetector
import com.pelotcl.app.generic.utils.date.FrenchPublicHolidayStrategy
import com.pelotcl.app.generic.utils.search.SearchUtils
import io.raptor.PeriodData
import io.raptor.RaptorLibrary
import io.raptor.data.NetworkLoader
import io.raptor.model.Route
import io.raptor.model.Stop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Repository to handle raptor-kt route calculations.
 * Uses lazy initialization - Raptor library is only loaded when first needed,
 * not at app startup, to improve initial loading time.
 *
 * Supports multiple schedule periods:
 * - saturday: Saturday schedules
 * - sunday: Sunday and public holiday schedules
 * - school_on_weekdays: Weekday schedules during school periods
 * - school_off_weekdays: Weekday schedules during school holidays
 *
 * Performance optimizations:
 * - Multi-level cache: Memory LRU (30min) -> Disk cache (daily) -> Raptor calculation
 * - HashMap indexes for O(1) stop lookups by ID and index
 * - Pre-computed normalized name index for fast search
 * - Buffered I/O for asset loading
 * - Singleton pattern to avoid multiple initializations
 * - Dispatchers.Default for CPU-bound Raptor calculations (optimized thread pool)
 * - Reusable StringBuilder for cache key building (reduces GC pressure)
 * - Pre-allocated ArrayLists for result mapping (avoids resizing)
 */
class RaptorRepository private constructor(private val context: Context) {

    private var raptorLibrary: RaptorLibrary? = null
    private var stopsCache: List<Stop> = emptyList()
    private val mutex = Mutex()

    // Generic holiday detector
    private var holidayDetector: HolidayDetector? = null

    // Multi-level disk cache for journey persistence
    private val journeyDiskCache: JourneyCache by lazy { JourneyCache.getInstance(context) }

    // Performance: HashMap index for O(1) stop lookup by index position
    private var stopsByIndex: Map<Int, Stop> = emptyMap()

    // Performance: Cache of normalized stop names to avoid repeated normalization during search
    private var normalizedStopNames: Map<Stop, String> = emptyMap()
    private var stopIdsByNormalizedName: Map<String, List<Int>> = emptyMap()
    // Lazy-loaded per period to avoid reading all 8 binary files at startup
    private val routesByPeriod = ConcurrentHashMap<String, List<Route>>()
    private val stopsByPeriod = ConcurrentHashMap<String, List<Stop>>()

    // Performance: Reusable StringBuilder for cache key building (ThreadLocal for thread safety)
    private val cacheKeyBuilder = ThreadLocal.withInitial { StringBuilder(64) }

    // Performance: Cached result of checkAssetsAvailable() to avoid repeated file I/O
    @Volatile
    private var cachedAssetsAvailable: Boolean? = null

    // Performance: Pre-computed list of stops with coords to avoid repeated .map{} allocations
    private var cachedStopsWithCoords: List<RaptorStopWithCoords> = emptyList()

    // Period IDs matching asset file naming
    companion object {
        private const val TAG = "RaptorRepository"

        // Set to false for production builds to reduce log overhead
        private const val DEBUG_LOGGING = false

        // Period constants
        private const val PERIOD_SATURDAY = "saturday"
        private const val PERIOD_SUNDAY = "sunday"
        private const val PERIOD_SCHOOL_ON_WEEKDAYS = "school_on_weekdays"
        private const val PERIOD_SCHOOL_OFF_WEEKDAYS = "school_off_weekdays"

        // LRU Cache for journey results: key = "origin|dest|time"
        // Level 1 cache: 50 entries in memory with 30-minute validity
        private val journeyCache = LruCache<String, List<JourneyResult>>(50)

        // Cache validity: 30 minutes (increased from 5min for better hit rate)
        private const val JOURNEY_CACHE_VALIDITY_MS = 30 * 60 * 1000L
        private val journeyCacheTimestamps = mutableMapOf<String, Long>()

        // Singleton instance - uses applicationContext so no memory leak
        // StaticFieldLeak is safe here because we only store applicationContext (not Activity context)
        // The instance lifecycle matches the application lifecycle, and applicationContext doesn't
        // hold references to any Activity, preventing memory leaks
        @Suppress("StaticFieldLeak")
        @Volatile
        private var INSTANCE: RaptorRepository? = null

        /**
         * Get singleton instance of RaptorRepository.
         * Creates instance on first call, returns existing instance on subsequent calls.
         */
        fun getInstance(context: Context): RaptorRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RaptorRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }


    }

    @Volatile
    private var isInitialized = false

    @Volatile
    private var isInitializing = false

    /**
     * Initialize the Raptor library with all period data from assets.
     * This is called lazily on first use, not at startup.
     * Uses buffered I/O and builds performance indexes.
     * Loads all schedule periods: saturday, sunday, school_on_weekdays, school_off_weekdays
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        // Fast path: already initialized
        if (isInitialized && raptorLibrary != null) {
            return@withContext Result.success(Unit)
        }

        mutex.withLock {
            // Double-check after acquiring lock
            if (isInitialized && raptorLibrary != null) {
                return@withContext Result.success(Unit)
            }

            if (isInitializing) {
                // Another coroutine is initializing, wait and return
                return@withContext Result.success(Unit)
            }

            isInitializing = true

            try {
                val startTime = System.currentTimeMillis()

                // Verify all required assets exist before attempting to load them
                val requiredAssets = listOf(
                    "holidays.json",
                    "raptor/stops_saturday.bin", "raptor/routes_saturday.bin",
                    "raptor/stops_sunday.bin", "raptor/routes_sunday.bin",
                    "raptor/stops_school_on_weekdays.bin", "raptor/routes_school_on_weekdays.bin",
                    "raptor/stops_school_off_weekdays.bin", "raptor/routes_school_off_weekdays.bin"
                )

                val missingAssets = requiredAssets.filter { assetName ->
                    runCatching { context.assets.open(assetName).close() }.isFailure
                }

                if (missingAssets.isNotEmpty()) {
                    val errorMsg = "CRITICAL: Missing required assets: ${missingAssets.joinToString(", ")}"
                    Log.e("RaptorRepository", errorMsg)
                    Log.e("RaptorRepository", "This will cause bus stops to disappear from search and map!")
                    Log.e("RaptorRepository", "Please clean build and check asset packaging in Android Studio")
                    return@withContext Result.failure(IllegalStateException(errorMsg))
                }

                Log.i("RaptorRepository", "All required assets verified successfully")

                // Load school holidays from assets
                loadSchoolHolidays()

                // Load all period data
                val periods = listOf(
                    PERIOD_SATURDAY,
                    PERIOD_SUNDAY,
                    PERIOD_SCHOOL_ON_WEEKDAYS,
                    PERIOD_SCHOOL_OFF_WEEKDAYS
                ).map { periodId ->
                    PeriodData(
                        periodId = periodId,
                        stopsInputStream = BufferedInputStream(
                            context.assets.open("raptor/stops_$periodId.bin"), 8192
                        ),
                        routesInputStream = BufferedInputStream(
                            context.assets.open("raptor/routes_$periodId.bin"), 8192
                        )
                    )
                }

                raptorLibrary = RaptorLibrary(periods)
                // routesByPeriod and stopsByPeriod are now lazy-loaded per period on first access

                // Set initial period based on current day
                updatePeriodForDate(LocalDate.now())

                // Cache all stops for lookup (from current period)
                stopsCache = raptorLibrary?.searchStopsByName("") ?: emptyList()

                // Build performance indexes
                buildStopIndexes()

                // Pre-compute cached values to avoid repeated allocations
                cachedAssetsAvailable = true // assets verified above
                cachedStopsWithCoords = stopsCache.map { stop ->
                    RaptorStopWithCoords(
                        id = stop.id,
                        name = stop.name,
                        lat = stop.lat,
                        lon = stop.lon
                    )
                }

                isInitialized = true

                Log.i(
                    TAG,
                    "Raptor initialized in ${System.currentTimeMillis() - startTime}ms with ${periods.size} periods, current: ${raptorLibrary?.getCurrentPeriod()}"
                )

                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("RaptorRepository", "Failed to initialize Raptor library: ${e.message}", e)
                Result.failure(e)
            } finally {
                isInitializing = false
            }
        }
    }

    private fun loadSchoolHolidays() {
        val config = TransportServiceProvider.getTransportConfig()
        holidayDetector = HolidayDetector(
            context,
            config.schoolHolidaysFile,
            FrenchPublicHolidayStrategy() // Could be made factory-based or config-based later
        )
        Log.i(TAG, "Initialized generic HolidayDetector with ${config.schoolHolidaysFile}")
    }

    /**
     * Determine if a given date falls within school holidays
     */
    private fun isSchoolHoliday(date: LocalDate): Boolean {
        return holidayDetector?.isSchoolHoliday(date) ?: false
    }

    private fun isPublicHoliday(date: LocalDate): Boolean {
        return holidayDetector?.isPublicHoliday(date) ?: false
    }

    /**
     * Get the appropriate period ID for a given date
     */
    private fun getPeriodForDate(date: LocalDate): String {
        val dayOfWeek = date.dayOfWeek.value // 1 = Monday, 7 = Sunday

        if (isPublicHoliday(date)) return PERIOD_SUNDAY

        return when (dayOfWeek) {
            6 -> PERIOD_SATURDAY
            7 -> PERIOD_SUNDAY
            else -> {
                if (isSchoolHoliday(date)) {
                    PERIOD_SCHOOL_OFF_WEEKDAYS
                } else {
                    PERIOD_SCHOOL_ON_WEEKDAYS
                }
            }
        }
    }

    /**
     * Update the active period based on the given date.
     * This should be called when searching for journeys to ensure
     * the correct schedule is used.
     */
    private fun updatePeriodForDate(date: LocalDate) {
        val targetPeriod = getPeriodForDate(date)
        val currentPeriod = raptorLibrary?.getCurrentPeriod()

        if (currentPeriod != targetPeriod) {
            raptorLibrary?.setPeriod(targetPeriod)
            Log.i(TAG, "Switched period from $currentPeriod to $targetPeriod for date $date")

            // Rebuild stop cache and indexes for new period
            stopsCache = raptorLibrary?.searchStopsByName("") ?: emptyList()
            buildStopIndexes()
        }
    }

    /**
     * Ensure the correct period is set for the given date before queries.
     * Called before each route calculation.
     * @param date The date to use for period selection (default: today)
     */
    private fun ensureCorrectPeriod(date: LocalDate = LocalDate.now()) {
        updatePeriodForDate(date)
    }

    private fun periodForFlags(isSchoolHoliday: Boolean, isPublicHoliday: Boolean): String {
        val dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        if (isPublicHoliday) return PERIOD_SUNDAY
        return when (dayOfWeek) {
            Calendar.SATURDAY -> PERIOD_SATURDAY
            Calendar.SUNDAY -> PERIOD_SUNDAY
            else -> if (isSchoolHoliday) PERIOD_SCHOOL_OFF_WEEKDAYS else PERIOD_SCHOOL_ON_WEEKDAYS
        }
    }

    private fun getRoutesForPeriod(periodId: String): List<Route> {
        return routesByPeriod.getOrPut(periodId) {
            context.assets.open("raptor/routes_$periodId.bin").use { input ->
                NetworkLoader.loadRoutes(BufferedInputStream(input, 8192))
            }
        }
    }

    private fun getStopsForPeriod(periodId: String): List<Stop> {
        return stopsByPeriod.getOrPut(periodId) {
            context.assets.open("raptor/stops_$periodId.bin").use { input ->
                NetworkLoader.loadStops(BufferedInputStream(input, 8192))
            }
        }
    }

    private data class RouteVariant(
        val route: Route,
        val stopNames: List<String>
    )

    private fun getVariantsForRoute(periodId: String, routeName: String): List<RouteVariant> {
        val routes = getRoutesForPeriod(periodId)
        val stops = getStopsForPeriod(periodId)
        val stopById = stops.associateBy { it.id }
        return routes
            .filter { it.name.equals(routeName, ignoreCase = true) }
            .map { route ->
                val stopNames = route.stopIds.toList().mapNotNull { stopById[it]?.name }
                RouteVariant(route, stopNames)
            }
            .filter { it.stopNames.isNotEmpty() }
            .distinctBy { it.stopNames.joinToString("|") }
            .sortedBy { it.stopNames.lastOrNull() ?: "" }
    }

    /**
     * Build HashMap indexes for O(1) stop lookups.
     * Called once during initialization.
     */
    private fun buildStopIndexes() {
        // Index by position (for leg.fromStopIndex / leg.toStopIndex lookups)
        stopsByIndex = stopsCache.mapIndexed { index, stop -> index to stop }.toMap()

        // Pre-compute normalized names for fast accent-insensitive search
        normalizedStopNames = stopsCache.associateWith { stop ->
            SearchUtils.normalizeForSearch(stop.name)
        }
        stopIdsByNormalizedName = stopsCache
            .groupBy { stop -> SearchUtils.normalizeForSearch(stop.name) }
            .mapValues { (_, stops) -> stops.map { it.id }.distinct() }
    }

    /**
     * Ensure initialized before use (internal helper)
     */
    private suspend fun ensureInitialized() {
        if (!isInitialized) {
            initialize()
        }
    }

    /**
     * Search for stops by name with multiple strategies for better matching.
     * Uses pre-computed normalized name index for fast lookups.
     * Uses Dispatchers.Default as this is CPU-bound string matching work.
     */
    suspend fun searchStopsByName(query: String): List<RaptorStop> =
        withContext(Dispatchers.Default) {
            ensureInitialized()
            try {
                // Pré-calcul unique de la query normalisée
                val normalizedQuery = SearchUtils.normalizeForSearch(query)
                val firstWord = normalizedQuery.split(" ").firstOrNull() ?: ""

                // Étape 1: pré-filtrage rapide sur le premier mot (utilise le cache)
                val candidates = if (firstWord.isNotEmpty()) {
                    stopsCache.filter { stop ->
                        (normalizedStopNames[stop]
                            ?: SearchUtils.normalizeForSearch(stop.name)).contains(firstWord)
                    }
                } else {
                    stopsCache
                }

                // Étape 2: fuzzy matching précis avec valeurs pré-normalisées (évite les recalculs)
                val results = candidates.filter { stop ->
                    val normalizedName =
                        normalizedStopNames[stop] ?: SearchUtils.normalizeForSearch(stop.name)
                    SearchUtils.fuzzyContainsNormalized(normalizedName, normalizedQuery)
                }.map { stop ->
                    val normalizedName =
                        normalizedStopNames[stop] ?: SearchUtils.normalizeForSearch(stop.name)
                    RaptorStop(
                        id = stop.id,
                        name = stop.name,
                        lat = stop.lat,
                        lon = stop.lon
                    ) to normalizedName
                }.sortedWith(
                    compareBy(
                        { !SearchUtils.fuzzyStartsWithNormalized(it.second, normalizedQuery) },
                        { it.first.name }
                    )
                ).map { it.first }
                .filter { stop ->
                    // Only filter out stops with no lines if Raptor assets are available
                    // If assets are missing, include all stops to avoid hiding valid bus stops
                    val assetsAvailable = checkAssetsAvailable()
                    if (assetsAvailable) {
                        hasLinesForStop(stop.name)
                    } else {
                        // Assets missing - include all stops but log warning
                        if (!hasLinesForStop(stop.name)) {
                            Log.w("RaptorRepository", "Stop ${stop.name} has no desserte - Raptor assets may be missing")
                        }
                        true // Include all stops when assets are missing
                    }
                }

                results
            } catch (e: Exception) {
                Log.e("RaptorRepository", "Error searching stops: ${e.message}", e)
                emptyList()
            }
        }

    /**
     * Resolve stop IDs for a stop name with fast exact lookup.
     * Falls back to fuzzy search only when exact lookup fails.
     */
    suspend fun resolveStopIdsByName(
        stopName: String,
        maxIds: Int = 64
    ): List<Int> =
        withContext(Dispatchers.Default) {
            ensureInitialized()
            val normalized = SearchUtils.normalizeForSearch(stopName)
            if (normalized.isBlank()) return@withContext emptyList()

            stopIdsByNormalizedName[normalized]?.let { ids ->
                if (ids.isNotEmpty()) return@withContext ids.take(maxIds)
            }

            searchStopsByName(stopName).map { it.id }.take(maxIds)
        }

    /**
     * Get stop name by its ID.
     * Useful for matching WFS stops (which have gid) to Raptor stops.
     */
    fun getStopNameById(stopId: Int): String? {
        return stopsCache.find { it.id == stopId }?.name
    }

    /**
     * Get all stops as a map of id to name.
     * Useful for bulk enrichment of stop names.
     */
    fun getAllStopNamesById(): Map<Int, String> {
        return stopsCache.associate { it.id to it.name }
    }

    /**
     * Get all stops with their coordinates.
     * Useful for coordinate-based matching with WFS stops.
     */
    fun getAllStopsWithCoords(): List<RaptorStopWithCoords> {
        if (cachedStopsWithCoords.isNotEmpty()) return cachedStopsWithCoords
        val result = stopsCache.map { stop ->
            RaptorStopWithCoords(
                id = stop.id,
                name = stop.name,
                lat = stop.lat,
                lon = stop.lon
            )
        }
        cachedStopsWithCoords = result
        return result
    }

    /**
     * Check if all required Raptor assets are available
     * @return true if all assets are present, false otherwise
     */
    fun checkAssetsAvailable(): Boolean {
        cachedAssetsAvailable?.let { return it }
        val result = runCatching {
            val requiredAssets = listOf(
                "holidays.json",
                "raptor/stops_saturday.bin", "raptor/routes_saturday.bin",
                "raptor/stops_sunday.bin", "raptor/routes_sunday.bin",
                "raptor/stops_school_on_weekdays.bin", "raptor/routes_school_on_weekdays.bin",
                "raptor/stops_school_off_weekdays.bin", "raptor/routes_school_off_weekdays.bin"
            )

            requiredAssets.all { assetName ->
                runCatching { context.assets.open(assetName).close() }.isSuccess
            }
        }.getOrDefault(false)
        cachedAssetsAvailable = result
        return result
    }

    /**
     * Check if a stop has any lines serving it
     * @param stopName The name of the stop to check
     * @return true if the stop has at least one line, false otherwise
     */
    private suspend fun hasLinesForStop(stopName: String): Boolean {
        return getDesserteForStop(stopName)?.isNotEmpty() == true
    }

    /**
     * Find the N nearest stops to the given GPS coordinates, sorted by distance.
     * Uses Dispatchers.Default as this is CPU-bound distance calculation.
     *
     * @param latitude GPS latitude
     * @param longitude GPS longitude
     * @param limit Maximum number of stops to return (default 5)
     * @return List of RaptorStop sorted by distance (closest first), with unique names
     */
    suspend fun findNearestStops(
        latitude: Double,
        longitude: Double,
        limit: Int = 5
    ): List<RaptorStop> = withContext(Dispatchers.Default) {
        ensureInitialized()
        try {
            // Calculate distance for each stop and sort by distance
            stopsCache
                .map { stop ->
                    val latDiff = stop.lat - latitude
                    val lonDiff = stop.lon - longitude
                    val distance = sqrt(latDiff.pow(2) + lonDiff.pow(2))
                    stop to distance
                }
                .sortedBy { it.second }
                // Group by stop name to get unique stop names (different platforms have same name)
                .distinctBy { it.first.name }
                .take(limit)
                .filter { (stop, _) -> hasLinesForStop(stop.name) }
                .map { (stop, _) ->
                    RaptorStop(
                        id = stop.id,
                        name = stop.name,
                        lat = stop.lat,
                        lon = stop.lon
                    )
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding nearest stops: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Calculate optimized journeys between origin and destination stops.
     * Uses multi-level cache: Memory LRU -> Disk cache -> Raptor calculation.
     *
     * Uses Dispatchers.Default (CPU-optimized thread pool) instead of Dispatchers.IO
     * because Raptor algorithm is CPU-bound, not I/O-bound. Default uses all CPU cores
     * while IO is limited to 64 threads optimized for blocking I/O operations.
     *
     * @param originStopIds List of origin stop IDs (to handle stops with multiple platforms)
     * @param destinationStopIds List of destination stop IDs
     * @param departureTimeSeconds Departure time in seconds from midnight (default: current time)
     * @param date The date to search for (default: today). Used to select the correct schedule period.
     * @param blockedRouteNames Set of route name patterns to exclude from routing (e.g., "JD" blocks all JD lines, "RX" blocks RhôneExpress)
     */
    suspend fun getOptimizedPaths(
        originStopIds: List<Int>,
        destinationStopIds: List<Int>,
        departureTimeSeconds: Int? = null,
        date: LocalDate = LocalDate.now(),
        blockedRouteNames: Set<String> = emptySet()
    ): List<JourneyResult> = withContext(Dispatchers.Default) {
        ensureInitialized()
        ensureCorrectPeriod(date) // Auto-select period based on selected date

        // Early return for empty inputs to avoid unnecessary computation
        if (originStopIds.isEmpty() || destinationStopIds.isEmpty()) {
            Log.w(
                TAG,
                "getOptimizedPaths: origin or destination stop IDs are empty, skipping calculation"
            )
            return@withContext emptyList()
        }

        try {
            val depTime = departureTimeSeconds ?: getCurrentTimeInSeconds()

            // Smart time rounding: 15 min in off-peak, 5 min in peak hours
            val roundedDepTime = getRoundedDepartureTime(depTime)

            // Build cache key (includes date to ensure different periods are cached separately)
            val cacheKey = buildCacheKey(
                originStopIds,
                destinationStopIds,
                roundedDepTime,
                date,
                blockedRouteNames
            )

            // Level 1: Check in-memory LRU cache
            val memoryCached = journeyCache.get(cacheKey)
            val cacheTimestamp = journeyCacheTimestamps[cacheKey]

            if (memoryCached != null && cacheTimestamp != null) {
                val cacheAge = System.currentTimeMillis() - cacheTimestamp
                if (cacheAge < JOURNEY_CACHE_VALIDITY_MS) {
                    return@withContext memoryCached
                }
            }

            // Level 2: Check disk cache (daily validity)
            val diskCached = journeyDiskCache.get(cacheKey)
            if (diskCached != null) {
                // Promote to memory cache
                journeyCache.put(cacheKey, diskCached)
                journeyCacheTimestamps[cacheKey] = System.currentTimeMillis()
                return@withContext diskCached
            }

            // Note: Empty checks already handled above, no need to check again
            Log.i(
                TAG,
                "getOptimizedPaths: Cache miss, calculating with Raptor for ${originStopIds.size} origin(s) -> ${destinationStopIds.size} destination(s)"
            )

            // Level 3: Calculate with Raptor
            val journeys = raptorLibrary?.getOptimizedPaths(
                originStopIds = originStopIds,
                destinationStopIds = destinationStopIds,
                departureTime = depTime,
                blockedRouteNames = blockedRouteNames
            ) ?: emptyList()

            // Performance: Pre-allocate results list with estimated capacity
            val results = ArrayList<JourneyResult>(journeys.size)

            // Use explicit for loop instead of mapNotNull to reduce lambda allocations
            for (legs in journeys) {
                if (legs.isEmpty()) continue

                // Pre-allocate journey legs list
                val journeyLegs = ArrayList<JourneyLeg>(legs.size)
                var hasInvalidLeg = false

                for (leg in legs) {
                    // Use HashMap index for O(1) stop lookup
                    val fromStop = stopsByIndex[leg.fromStopIndex]
                    val toStop = stopsByIndex[leg.toStopIndex]

                    if (fromStop == null || toStop == null) {
                        if (DEBUG_LOGGING) {
                            Log.w(
                                "RaptorRepository",
                                "getOptimizedPaths: Stop not found - fromIdx=${leg.fromStopIndex}, toIdx=${leg.toStopIndex}"
                            )
                        }
                        hasInvalidLeg = true
                        break
                    }

                    // Map intermediate stops using explicit for loop
                    val intermediateIndices = leg.intermediateStopIndices
                    val intermediateTimes = leg.intermediateArrivalTimes
                    val intermediateStops = ArrayList<IntermediateStop>(intermediateIndices.size)

                    for (idx in intermediateIndices.indices) {
                        val stop = stopsByIndex[intermediateIndices[idx]]
                        val arrivalTime =
                            if (idx < intermediateTimes.size) intermediateTimes[idx] else null
                        if (stop != null && arrivalTime != null) {
                            intermediateStops.add(
                                IntermediateStop(
                                    stopName = stop.name,
                                    arrivalTime = arrivalTime,
                                    lat = stop.lat,
                                    lon = stop.lon
                                )
                            )
                        }
                    }

                    journeyLegs.add(
                        JourneyLeg(
                            fromStopId = fromStop.id.toString(),
                            fromStopName = fromStop.name,
                            fromLat = fromStop.lat,
                            fromLon = fromStop.lon,
                            toStopId = toStop.id.toString(),
                            toStopName = toStop.name,
                            toLat = toStop.lat,
                            toLon = toStop.lon,
                            departureTime = leg.departureTime,
                            arrivalTime = leg.arrivalTime,
                            routeName = leg.routeName,
                            routeColor = null, // Library doesn't provide color
                            isWalking = leg.isTransfer,
                            direction = leg.direction,
                            intermediateStops = intermediateStops
                        )
                    )
                }

                // Skip this journey if any leg was invalid
                if (hasInvalidLeg || journeyLegs.isEmpty()) continue

                results.add(
                    JourneyResult(
                        departureTime = legs.first().departureTime,
                        arrivalTime = legs.last().arrivalTime,
                        legs = journeyLegs
                    )
                )
            }

            // Store results in both memory and disk cache
            if (results.isNotEmpty()) {
                // Level 1: Memory cache
                journeyCache.put(cacheKey, results)
                journeyCacheTimestamps[cacheKey] = System.currentTimeMillis()

                // Level 2: Disk cache (async, fire and forget)
                journeyDiskCache.put(cacheKey, results)
            }

            results
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating paths: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Calculate optimized journeys that arrive by a specific time.
     * Useful for "I need to be there by X" scenarios.
     *
     * @param originStopIds List of origin stop IDs
     * @param destinationStopIds List of destination stop IDs
     * @param arrivalTimeSeconds Desired arrival time in seconds from midnight
     * @param searchWindowMinutes How far back to search for departures (default: 120 minutes)
     * @param date The date to search for (default: today). Used to select the correct schedule period.
     * @param blockedRouteNames Set of route name patterns to exclude from routing (e.g., "JD" blocks all JD lines, "RX" blocks RhôneExpress)
     * @return List of JourneyResult sorted by latest departure (arrive as late as possible while still being on time)
     */
    suspend fun getOptimizedPathsArriveBy(
        originStopIds: List<Int>,
        destinationStopIds: List<Int>,
        arrivalTimeSeconds: Int,
        searchWindowMinutes: Int = 120,
        date: LocalDate = LocalDate.now(),
        blockedRouteNames: Set<String> = emptySet()
    ): List<JourneyResult> = withContext(Dispatchers.Default) {
        ensureInitialized()
        ensureCorrectPeriod(date)
        try {
            if (originStopIds.isEmpty()) {
                Log.w(TAG, "getOptimizedPathsArriveBy: originStopIds is empty!")
                return@withContext emptyList()
            }
            if (destinationStopIds.isEmpty()) {
                Log.w(TAG, "getOptimizedPathsArriveBy: destinationStopIds is empty!")
                return@withContext emptyList()
            }

            // Use raptor-kt's arrive-by search
            val journeys = raptorLibrary?.getOptimizedPathsArriveBy(
                originStopIds = originStopIds,
                destinationStopIds = destinationStopIds,
                arrivalTime = arrivalTimeSeconds,
                searchWindowMinutes = searchWindowMinutes,
                blockedRouteNames = blockedRouteNames
            ) ?: emptyList()

            // Map results using the same logic as getOptimizedPaths
            val results = ArrayList<JourneyResult>(journeys.size)

            for (legs in journeys) {
                if (legs.isEmpty()) continue

                val journeyLegs = ArrayList<JourneyLeg>(legs.size)
                var hasInvalidLeg = false

                for (leg in legs) {
                    val fromStop = stopsByIndex[leg.fromStopIndex]
                    val toStop = stopsByIndex[leg.toStopIndex]

                    if (fromStop == null || toStop == null) {
                        if (DEBUG_LOGGING) {
                            Log.w(
                                TAG,
                                "getOptimizedPathsArriveBy: Stop not found - fromIdx=${leg.fromStopIndex}, toIdx=${leg.toStopIndex}"
                            )
                        }
                        hasInvalidLeg = true
                        break
                    }

                    val intermediateIndices = leg.intermediateStopIndices
                    val intermediateTimes = leg.intermediateArrivalTimes
                    val intermediateStops = ArrayList<IntermediateStop>(intermediateIndices.size)

                    for (idx in intermediateIndices.indices) {
                        val stop = stopsByIndex[intermediateIndices[idx]]
                        val arrivalTime =
                            if (idx < intermediateTimes.size) intermediateTimes[idx] else null
                        if (stop != null && arrivalTime != null) {
                            intermediateStops.add(
                                IntermediateStop(
                                    stopName = stop.name,
                                    arrivalTime = arrivalTime,
                                    lat = stop.lat,
                                    lon = stop.lon
                                )
                            )
                        }
                    }

                    journeyLegs.add(
                        JourneyLeg(
                            fromStopId = fromStop.id.toString(),
                            fromStopName = fromStop.name,
                            fromLat = fromStop.lat,
                            fromLon = fromStop.lon,
                            toStopId = toStop.id.toString(),
                            toStopName = toStop.name,
                            toLat = toStop.lat,
                            toLon = toStop.lon,
                            departureTime = leg.departureTime,
                            arrivalTime = leg.arrivalTime,
                            routeName = leg.routeName,
                            routeColor = null,
                            isWalking = leg.isTransfer,
                            direction = leg.direction,
                            intermediateStops = intermediateStops
                        )
                    )
                }

                if (hasInvalidLeg || journeyLegs.isEmpty()) continue

                results.add(
                    JourneyResult(
                        departureTime = legs.first().departureTime,
                        arrivalTime = legs.last().arrivalTime,
                        legs = journeyLegs
                    )
                )
            }

            results
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating arrive-by paths: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Smart time rounding for better cache hit rate:
     * - Peak hours (7-9, 17-19): 5 minute intervals (more precision needed)
     * - Off-peak hours: 15 minute intervals (fewer variations, better hits)
     */
    private fun getRoundedDepartureTime(timeSeconds: Int): Int {
        val hour = (timeSeconds / 3600) % 24 // Handle times past midnight (e.g., hour 25 -> 1)
        val isPeakHour = (hour in 7..9) || (hour in 17..19)

        return if (isPeakHour) {
            // 5 minute rounding during peak
            (timeSeconds / 300) * 300
        } else {
            // 15 minute rounding during off-peak
            (timeSeconds / 900) * 900
        }
    }

    /**
     * Build a cache key for journey results.
     * Sorts IDs to ensure same key regardless of order.
     * Uses reusable StringBuilder to reduce GC pressure.
     * Includes date and blocked route names to ensure different filters return different results.
     */
    private fun buildCacheKey(
        originIds: List<Int>,
        destIds: List<Int>,
        time: Int,
        date: LocalDate,
        blockedRouteNames: Set<String>
    ): String {
        val sb = cacheKeyBuilder.get()!!
        sb.setLength(0) // Clear without allocation

        // Sort and append origin IDs
        val sortedOrigin = originIds.sorted()
        for (i in sortedOrigin.indices) {
            if (i > 0) sb.append(',')
            sb.append(sortedOrigin[i])
        }

        sb.append('|')

        // Sort and append destination IDs
        val sortedDest = destIds.sorted()
        for (i in sortedDest.indices) {
            if (i > 0) sb.append(',')
            sb.append(sortedDest[i])
        }

        sb.append('|')
        sb.append(time)
        sb.append('|')
        sb.append(date.toString()) // Include date to differentiate cache by period
        sb.append('|')

        val sortedBlocked = blockedRouteNames.sorted()
        for (i in sortedBlocked.indices) {
            if (i > 0) sb.append(',')
            sb.append(sortedBlocked[i])
        }

        return sb.toString()
    }

    /**
     * Preload journey cache from disk to memory.
     * Call at app startup for faster initial queries.
     */
    suspend fun preloadJourneyCache() {
        journeyDiskCache.preloadToMemory()
    }

    private fun getCurrentTimeInSeconds(): Int {
        val calendar = Calendar.getInstance()
        val hours = calendar.get(Calendar.HOUR_OF_DAY)
        val minutes = calendar.get(Calendar.MINUTE)
        val seconds = calendar.get(Calendar.SECOND)
        return hours * 3600 + minutes * 60 + seconds
    }

    fun searchLinesByName(query: String): List<LineSearchResult> {
        val currentPeriod = raptorLibrary?.getCurrentPeriod() ?: return emptyList()
        val routes = getRoutesForPeriod(currentPeriod)
        val allNames = routes
            .map { it.name }
            .distinct()
        if (query.isBlank()) {
            return allNames.sorted().map {
                LineSearchResult(lineName = it, category = TransportServiceProvider.getTransportLineRules().getTransportType(it))
            }
        }
        val normalizedQuery = query.trim().uppercase()
        return allNames
            .filter {
                it.uppercase().contains(normalizedQuery) || normalizedQuery.contains(it.uppercase())
            }
            .sortedWith(
                compareBy(
                {
                    if (it.equals(normalizedQuery, ignoreCase = true)) 0 else if (it.uppercase()
                            .startsWith(normalizedQuery)
                    ) 1 else 2
                },
                { it }
            ))
            .take(20)
            .map { lineName ->
                LineSearchResult(
                    lineName = lineName,
                    category = TransportServiceProvider.getTransportLineRules().getTransportType(lineName)
                )
            }
    }

    fun getHeadsigns(routeName: String): Map<Int, String> {
        val period = raptorLibrary?.getCurrentPeriod() ?: PERIOD_SCHOOL_ON_WEEKDAYS
        val variants = getVariantsForRoute(period, routeName)
        return variants.mapIndexed { index, variant ->
            index to (variant.stopNames.lastOrNull() ?: "Direction ${index + 1}")
        }.toMap()
    }

    fun getStopSequences(routeName: String, directionId: Int): List<Pair<String, Int>> {
        val period = raptorLibrary?.getCurrentPeriod() ?: PERIOD_SCHOOL_ON_WEEKDAYS
        val variants = getVariantsForRoute(period, routeName)
        val selected = variants.getOrNull(directionId) ?: return emptyList()
        return selected.stopNames.mapIndexed { index, stopName -> stopName to (index + 1) }
    }

    fun getSchedules(
        lineName: String,
        stopName: String,
        directionId: Int,
        isSchoolHoliday: Boolean,
        isPublicHoliday: Boolean
    ): List<String> {
        val period = periodForFlags(isSchoolHoliday, isPublicHoliday)
        val variants = getVariantsForRoute(period, lineName)
        val selected = variants.getOrNull(directionId) ?: return emptyList()
        val stopIdx = selected.stopNames.indexOfFirst { it.equals(stopName, ignoreCase = true) }
        if (stopIdx < 0) return emptyList()

        val route = selected.route
        val times = mutableListOf<String>()
        for (trip in 0 until route.tripCount) {
            val seconds = route.flatStopTimes[(trip * route.stopCountInRoute) + stopIdx]
            val hour = seconds / 3600
            val minute = (seconds % 3600) / 60
            times.add(String.format(Locale.ROOT, "%02d:%02d", hour, minute))
        }
        return times.distinct().sorted()
    }

    fun getDesserteForStop(stopName: String): String? {
        // Check if Raptor library failed to initialize (assets missing)
        if (raptorLibrary == null) {
            Log.w("RaptorRepository", "getDesserteForStop called but Raptor library not initialized. Check logcat for asset errors.")
            return null
        }

        val period = raptorLibrary?.getCurrentPeriod() ?: PERIOD_SCHOOL_ON_WEEKDAYS
        val stops = getStopsForPeriod(period)
        val routes = getRoutesForPeriod(period)
        val routeById = routes.groupBy { it.id }

        val dessertes = stops
            .filter { it.name.equals(stopName, ignoreCase = true) }
            .flatMap { stop ->
                stop.routeIds.flatMap { routeId ->
                    val variants = routeById[routeId].orEmpty()
                        .distinctBy { it.stopIds.joinToString(",") }
                    variants.mapIndexed { index, route ->
                        val dir = if (index == 0) "A" else "R"
                        "${route.name}:$dir"
                    }
                }
            }
            .distinct()
        return if (dessertes.isEmpty()) null else dessertes.joinToString(",")
    }
}
