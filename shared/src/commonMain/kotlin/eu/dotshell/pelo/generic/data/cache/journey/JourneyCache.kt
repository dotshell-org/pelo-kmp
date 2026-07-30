package eu.dotshell.pelo.generic.data.cache.journey

import eu.dotshell.pelo.platform.ioDispatcher

import eu.dotshell.pelo.generic.data.offline.GzipFileStore
import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.JourneyResult
import eu.dotshell.pelo.platform.FileSystem
import eu.dotshell.pelo.platform.Log
import eu.dotshell.pelo.platform.PlatformContext
import kotlin.concurrent.Volatile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Multi-level cache for journey results:
 * - Level 1: In-memory LRU (fast, limited size, 30-min validity)
 * - Level 2: gzipped disk cache (persists across restarts, valid until the local day changes)
 *
 * Fully cross-platform: disk IO via [GzipFileStore]/[FileSystem] (okio), memory LRU via an
 * access-ordered [LinkedHashMap] guarded by a [Mutex] (replaces `android.util.LruCache`).
 */
class JourneyCache private constructor(context: PlatformContext) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val fileSystem = FileSystem(context)
    private val cacheDir = "${fileSystem.cacheDir()}/journey_cache".also { GzipFileStore.ensureDir(it) }

    private val diskMutex = Mutex()
    private val memoryMutex = Mutex()

    // Manual LRU over an insertion-ordered LinkedHashMap. The JVM-only access-order constructor
    // and removeEldestEntry override are NOT in the common stdlib (they'd compile for Android but
    // break the iOS/Native build), so eviction is done by hand in [putMemory]. Insertion order is
    // preserved identically on JVM and Native. Guarded by memoryMutex.
    private val memoryCache = LinkedHashMap<String, CachedJourney>()

    private fun putMemory(key: String, value: CachedJourney) {
        memoryCache.remove(key) // re-insert to move the entry to the most-recently-used end
        memoryCache[key] = value
        while (memoryCache.size > MEMORY_CACHE_SIZE) {
            val eldest = memoryCache.keys.firstOrNull() ?: break
            memoryCache.remove(eldest)
        }
    }

    @Volatile
    private var cachedDate: Int = getCurrentDayOfYear()

    companion object {
        private const val TAG = "JourneyCache"
        private const val MEMORY_CACHE_VALIDITY_MS = 30 * 60 * 1000L
        private const val MEMORY_CACHE_SIZE = 50
        private const val CACHE_FILE_PREFIX = "journey_"
        private const val CACHE_FILE_SUFFIX = ".json.gz"
        private const val MAX_DISK_CACHE_SIZE_BYTES = 5 * 1024 * 1024L
        private const val MAX_DISK_ENTRIES = 200

        @Volatile
        private var INSTANCE: JourneyCache? = null

        fun getInstance(context: PlatformContext): JourneyCache =
            INSTANCE ?: JourneyCache(context).also { INSTANCE = it }
    }

    private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

    /** Get journeys from cache (memory first, then disk). Null if absent or expired. */
    suspend fun get(cacheKey: String): List<JourneyResult>? {
        checkDateChange()

        memoryMutex.withLock {
            val memoryCached = memoryCache[cacheKey]
            if (memoryCached != null) {
                if (nowMs() - memoryCached.timestamp < MEMORY_CACHE_VALIDITY_MS) {
                    putMemory(cacheKey, memoryCached) // mark as most-recently-used
                    return memoryCached.journeys.map { it.toJourneyResult() }
                } else {
                    memoryCache.remove(cacheKey)
                }
            }
        }

        return withContext(ioDispatcher) {
            val diskResult = readFromDiskPath(getCacheFilePath(cacheKey))
            if (diskResult != null) {
                memoryMutex.withLock {
                    putMemory(
                        cacheKey,
                        CachedJourney(
                            journeys = diskResult.map { SerializableJourneyResult.fromJourneyResult(it) },
                            timestamp = nowMs()
                        )
                    )
                }
            }
            diskResult
        }
    }

    /** Store journeys in both memory and disk. */
    suspend fun put(cacheKey: String, journeys: List<JourneyResult>) {
        if (journeys.isEmpty()) return

        val serializableJourneys = journeys.map { SerializableJourneyResult.fromJourneyResult(it) }
        val cachedJourney = CachedJourney(journeys = serializableJourneys, timestamp = nowMs())

        memoryMutex.withLock { putMemory(cacheKey, cachedJourney) }

        withContext(ioDispatcher) { writeToDisk(cacheKey, serializableJourneys) }
    }

    /** Preload the most recent disk entries into memory (call at startup). */
    suspend fun preloadToMemory() = withContext(ioDispatcher) {
        checkDateChange()
        try {
            val files = GzipFileStore.list(cacheDir)
                .filter { it.name.startsWith(CACHE_FILE_PREFIX) && it.name.endsWith(CACHE_FILE_SUFFIX) }
            val sortedFiles = files
                .sortedByDescending { GzipFileStore.lastModified(it.toString()) }
                .take(30)

            for (path in sortedFiles) {
                try {
                    val cacheKey = decodeKey(
                        path.name.removePrefix(CACHE_FILE_PREFIX).removeSuffix(CACHE_FILE_SUFFIX)
                    )
                    val alreadyInMemory = memoryMutex.withLock { memoryCache[cacheKey] != null }
                    if (alreadyInMemory) continue

                    val journeys = readFromDiskPath(path.toString())
                    if (journeys != null) {
                        memoryMutex.withLock {
                            putMemory(
                                cacheKey,
                                CachedJourney(
                                    journeys = journeys.map { SerializableJourneyResult.fromJourneyResult(it) },
                                    timestamp = nowMs()
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load cache file ${path.name}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to preload journey cache: ${e.message}")
        }
    }

    private suspend fun checkDateChange() {
        val today = getCurrentDayOfYear()
        if (today != cachedDate) {
            memoryMutex.withLock { memoryCache.clear() }
            cachedDate = today
        }
    }

    private fun getCurrentDayOfYear(): Int {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        return today.dayOfYear + today.year * 1000
    }

    private fun getCacheFilePath(cacheKey: String): String =
        "$cacheDir/$CACHE_FILE_PREFIX${encodeKey(cacheKey)}$CACHE_FILE_SUFFIX"

    private suspend fun writeToDisk(cacheKey: String, journeys: List<SerializableJourneyResult>) {
        diskMutex.withLock {
            try {
                GzipFileStore.writeGzip(getCacheFilePath(cacheKey), json.encodeToString(journeys))
                enforceDiskSizeLimit()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to write cache to disk: ${e.message}")
            }
        }
    }

    private fun readFromDiskPath(path: String): List<JourneyResult>? {
        if (!GzipFileStore.exists(path)) return null
        return try {
            val jsonString = GzipFileStore.readGzip(path) ?: return null
            json.decodeFromString<List<SerializableJourneyResult>>(jsonString)
                .map { it.toJourneyResult() }
        } catch (_: Exception) {
            GzipFileStore.delete(path)
            null
        }
    }

    private fun enforceDiskSizeLimit() {
        try {
            val files = GzipFileStore.list(cacheDir)
            if (files.isEmpty()) return

            val sortedFiles = files.sortedBy { GzipFileStore.lastModified(it.toString()) }
            var fileCount = sortedFiles.size
            var totalSize = sortedFiles.sumOf { GzipFileStore.size(it.toString()) }

            for (path in sortedFiles) {
                if (fileCount <= MAX_DISK_ENTRIES && totalSize <= MAX_DISK_CACHE_SIZE_BYTES) break
                totalSize -= GzipFileStore.size(path.toString())
                fileCount--
                GzipFileStore.delete(path.toString())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enforce disk size limit: ${e.message}")
        }
    }

    /**
     * Trim memory under pressure. [level] is the Android `ComponentCallbacks2` trim level
     * (20 = UI_HIDDEN, 40 = BACKGROUND). Best-effort and lock-free (a memory hint), so the
     * disk cache always remains as the source of truth.
     */
    fun trimMemory(level: Int) {
        if (level >= 20) {
            runCatching { memoryCache.clear() }
        }
    }

    // ── Filename-safe, reversible key encoding (replaces java.net.URLEncoder) ──────────────
    // Keeps [A-Za-z0-9.-]; everything else becomes %XX (ASCII). Reversible so [preloadToMemory]
    // can recover the original cache key from the filename.

    private fun encodeKey(key: String): String = buildString(key.length + 8) {
        for (c in key) {
            if (c.isLetterOrDigit() && c.code < 128 || c == '.' || c == '-') {
                append(c)
            } else {
                append('%')
                append(c.code.toString(16).uppercase().padStart(2, '0'))
            }
        }
    }

    private fun decodeKey(name: String): String = buildString(name.length) {
        var i = 0
        while (i < name.length) {
            val c = name[i]
            if (c == '%' && i + 2 < name.length) {
                val code = name.substring(i + 1, i + 3).toIntOrNull(16)
                if (code != null) {
                    append(code.toChar())
                    i += 3
                    continue
                }
            }
            append(c)
            i++
        }
    }
}
