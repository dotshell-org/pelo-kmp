package eu.dotshell.massilia.generic.data.cache

import eu.dotshell.massilia.platform.ioDispatcher

import eu.dotshell.massilia.generic.data.config.AppConfigLoader
import eu.dotshell.massilia.generic.data.models.geojson.Feature
import eu.dotshell.massilia.generic.data.models.geojson.StopFeature
import eu.dotshell.massilia.generic.data.offline.sanitizeForSerialization
import eu.dotshell.massilia.generic.data.offline.sanitizeStopsForSerialization
import eu.dotshell.massilia.generic.data.offline.GzipFileStore
import eu.dotshell.massilia.platform.FileSystem
import eu.dotshell.massilia.platform.Log
import eu.dotshell.massilia.platform.PlatformContext
import eu.dotshell.massilia.platform.Settings
import kotlin.concurrent.Volatile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Generic implementation of [TransportCache] driven by config.json.
 * Cross-platform: gzip + file IO via okio ([GzipFileStore]) under the app cache
 * dir, timestamps via the [Settings] abstraction.
 */
class TransportCacheImpl(context: PlatformContext) : TransportCache {

    private class LineCacheSlot(
        val fileName: String,
        val timestampKey: String,
        @Volatile var memory: List<Feature>? = null,
        @Volatile var timestamp: Long = 0L,
        val mutex: Mutex = Mutex()
    )

    private val fileSystem = FileSystem(context)
    private val settings = Settings(context, "transport_cache_meta")

    private val cacheConfig = AppConfigLoader.loadConfig(fileSystem).cache
    private val cacheValidityDuration = cacheConfig.validityHours * 3_600_000L

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val stopsMutex = Mutex()
    private val cacheDir = "${fileSystem.cacheDir()}/transport_data".also { GzipFileStore.ensureDir(it) }

    private val metroSlot = LineCacheSlot(FILE_METRO_LINES, KEY_METRO_LINES_TIMESTAMP)
    private val tramSlot = LineCacheSlot(FILE_TRAM_LINES, KEY_TRAM_LINES_TIMESTAMP)
    private val busSlot = LineCacheSlot(FILE_BUS_LINES, KEY_BUS_LINES_TIMESTAMP)

    private val lineSlots: List<LineCacheSlot> = buildList {
        add(metroSlot)
        add(tramSlot)
        if (cacheConfig.cacheBusLines) add(busSlot)
    }

    @Volatile
    private var stopsCache: List<StopFeature>? = null

    @Volatile
    private var stopsTimestamp: Long = 0L

    companion object {
        private const val TAG = "TransportCache"

        private const val KEY_METRO_LINES_TIMESTAMP = "metro_lines_timestamp"
        private const val KEY_TRAM_LINES_TIMESTAMP = "tram_lines_timestamp"
        private const val KEY_BUS_LINES_TIMESTAMP = "bus_lines_timestamp"
        private const val KEY_STOPS_TIMESTAMP = "stops_timestamp"

        private const val FILE_METRO_LINES = "metro_lines.json.gz"
        private const val FILE_TRAM_LINES = "tram_lines.json.gz"
        private const val FILE_BUS_LINES = "bus_lines.json.gz"
        private const val FILE_STOPS = "stops.json.gz"
    }

    override fun hasAnyCachedData(): Boolean {
        if (lineSlots.any { it.memory != null } || stopsCache != null) return true

        val hasLineCache = lineSlots.any { settings.getLong(it.timestampKey, 0) > 0 }
        val hasStopsCache = settings.getLong(KEY_STOPS_TIMESTAMP, 0) > 0
        return hasLineCache || hasStopsCache
    }

    override fun needsCacheRefresh(): Boolean {
        if (!hasAnyCachedData()) return true

        val now = Clock.System.now().toEpochMilliseconds()
        val lineExpired = lineSlots.any { slot ->
            val timestamp = settings.getLong(slot.timestampKey, 0)
            timestamp > 0 && (now - timestamp) >= cacheValidityDuration
        }
        val stopsTs = settings.getLong(KEY_STOPS_TIMESTAMP, 0)
        val stopsExpired = stopsTs > 0 && (now - stopsTs) >= cacheValidityDuration

        return lineExpired || stopsExpired
    }

    private fun isTimestampValid(timestamp: Long): Boolean {
        return (Clock.System.now().toEpochMilliseconds() - timestamp) < cacheValidityDuration
    }

    private suspend inline fun <reified T> writeToCompressedFile(fileName: String, data: T) =
        withContext(ioDispatcher) {
            try {
                if ((data as? List<*>)?.isEmpty() == true) {
                    Log.w(TAG, "Attempted to write empty data to $fileName, skipping")
                    return@withContext
                }
                val jsonString = json.encodeToString(data)
                if (jsonString.isBlank()) {
                    Log.e(TAG, "Serialization produced blank JSON for $fileName")
                    return@withContext
                }
                GzipFileStore.writeGzip("$cacheDir/$fileName", jsonString)
            } catch (e: Exception) {
                Log.e(TAG, "Error writing to $fileName", e)
            }
        }

    private suspend inline fun <reified T> readFromCompressedFile(fileName: String): T? =
        withContext(ioDispatcher) {
            try {
                val jsonString = GzipFileStore.readGzip("$cacheDir/$fileName")
                if (jsonString.isNullOrBlank()) {
                    GzipFileStore.delete("$cacheDir/$fileName")
                    return@withContext null
                }
                json.decodeFromString<T>(jsonString)
            } catch (e: Exception) {
                Log.e(TAG, "Error reading from $fileName", e)
                GzipFileStore.delete("$cacheDir/$fileName")
                null
            }
        }

    private suspend fun invalidateLineCache(fileName: String, timestampKey: String) {
        withContext(ioDispatcher) {
            GzipFileStore.delete("$cacheDir/$fileName")
        }
        settings.putLong(timestampKey, 0L)
    }

    private fun isInvalidLineCache(lines: List<Feature>): Boolean {
        if (lines.isEmpty()) return false
        val validEntries = lines.count {
            it.properties.lineName.isNotBlank() && it.properties.traceCode.isNotBlank()
        }
        return validEntries == 0
    }

    private suspend fun saveLineCache(slot: LineCacheSlot, lines: List<Feature>) {
        slot.memory = lines
        slot.timestamp = Clock.System.now().toEpochMilliseconds()
        settings.putLong(slot.timestampKey, slot.timestamp)
        writeToCompressedFile(slot.fileName, lines.sanitizeForSerialization())
    }

    private suspend fun loadLineCache(slot: LineCacheSlot): List<Feature>? {
        if (slot.memory != null && isTimestampValid(slot.timestamp)) {
            if (isInvalidLineCache(slot.memory.orEmpty())) {
                slot.memory = null
                slot.timestamp = 0L
                invalidateLineCache(slot.fileName, slot.timestampKey)
                return null
            }
            return slot.memory
        }

        val timestamp = settings.getLong(slot.timestampKey, 0)
        if (isTimestampValid(timestamp)) {
            val lines = readFromCompressedFile<List<Feature>>(slot.fileName)
            if (lines != null) {
                if (isInvalidLineCache(lines)) {
                    invalidateLineCache(slot.fileName, slot.timestampKey)
                    return null
                }
                slot.memory = lines
                slot.timestamp = timestamp
                return lines
            }
        }

        return null
    }

    override suspend fun saveMetroLines(lines: List<Feature>) {
        metroSlot.mutex.withLock { saveLineCache(metroSlot, lines) }
    }

    override suspend fun getMetroLines(): List<Feature>? = metroSlot.mutex.withLock {
        loadLineCache(metroSlot)
    }

    override suspend fun saveTramLines(lines: List<Feature>) {
        tramSlot.mutex.withLock { saveLineCache(tramSlot, lines) }
    }

    override suspend fun getTramLines(): List<Feature>? = tramSlot.mutex.withLock {
        loadLineCache(tramSlot)
    }

    override suspend fun saveBusLines(lines: List<Feature>) {
        if (!cacheConfig.cacheBusLines) return
        busSlot.mutex.withLock { saveLineCache(busSlot, lines) }
    }

    override suspend fun getBusLines(): List<Feature>? = busSlot.mutex.withLock {
        if (!cacheConfig.cacheBusLines) return@withLock null
        loadLineCache(busSlot)
    }

    override suspend fun saveStops(stops: List<StopFeature>) {
        stopsMutex.withLock {
            stopsCache = stops
            stopsTimestamp = Clock.System.now().toEpochMilliseconds()

            settings.putLong(KEY_STOPS_TIMESTAMP, stopsTimestamp)
            writeToCompressedFile(FILE_STOPS, stops.sanitizeStopsForSerialization())
        }
    }

    override suspend fun getStops(): List<StopFeature>? = stopsMutex.withLock {
        if (stopsCache != null && isTimestampValid(stopsTimestamp)) {
            return@withLock stopsCache
        }

        val timestamp = settings.getLong(KEY_STOPS_TIMESTAMP, 0)
        if (isTimestampValid(timestamp)) {
            val stops = readFromCompressedFile<List<StopFeature>>(FILE_STOPS)
            if (stops != null) {
                stopsCache = stops
                stopsTimestamp = timestamp
                return@withLock stops
            }
        }

        null
    }

    override suspend fun preloadFromDisk() {
        coroutineScope {
            val jobs = lineSlots.map { slot -> async { getLineFromDisk(slot) } } +
                listOf(async { getStops() })
            jobs.awaitAll()
        }
    }

    private suspend fun getLineFromDisk(slot: LineCacheSlot) {
        slot.mutex.withLock { loadLineCache(slot) }
    }
}
