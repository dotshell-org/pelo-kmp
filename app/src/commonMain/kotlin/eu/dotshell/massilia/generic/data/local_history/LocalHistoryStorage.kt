package eu.dotshell.massilia.generic.data.local_history

import eu.dotshell.massilia.platform.ioDispatcher

import eu.dotshell.massilia.platform.FileSystem
import eu.dotshell.massilia.platform.Log
import eu.dotshell.massilia.platform.PlatformContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Persistent, append-only history kept **100% on device** — never uploaded.
 *
 * Used to:
 *  1. Feed the [LocalProfileComputer] (usage status + habitual lines on 30-day window).
 *  2. Power a future user-facing "Mes trajets" screen (transparency benefit in
 *     exchange for opt-in).
 *
 * Multiplatform: uses [FileSystem] abstraction instead of java.io.File + Context.
 */
class LocalHistoryStorage(context: PlatformContext) {

    private val fs = FileSystem(context)
    private val mutex = Mutex()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    // ── Trips ────────────────────────────────────────────────────────────────

    suspend fun appendTrip(trip: LocalTripRecord) = mutex.withLock {
        withContext(ioDispatcher) {
            val current = readList(FILE_TRIPS, LocalTripRecord.serializer()) ?: emptyList()
            writeList(FILE_TRIPS, current + trip, LocalTripRecord.serializer())
        }
    }

    suspend fun readTripsWithinDays(days: Int): List<LocalTripRecord> = mutex.withLock {
        withContext(ioDispatcher) {
            val all = readList(FILE_TRIPS, LocalTripRecord.serializer()) ?: return@withContext emptyList()
            val cutoffMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - days * MS_PER_DAY
            all.filter { it.endedAtEpochMs >= cutoffMs }
        }
    }

    suspend fun pruneTripsOlderThan(days: Int) = mutex.withLock {
        withContext(ioDispatcher) {
            val all = readList(FILE_TRIPS, LocalTripRecord.serializer()) ?: return@withContext
            val cutoffMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - days * MS_PER_DAY
            val pruned = all.filter { it.endedAtEpochMs >= cutoffMs }
            if (pruned.size != all.size) {
                writeList(FILE_TRIPS, pruned, LocalTripRecord.serializer())
            }
        }
    }

    // ── Sessions ─────────────────────────────────────────────────────────────

    suspend fun appendSession(entry: SessionAuditEntry) = mutex.withLock {
        withContext(ioDispatcher) {
            val current = readList(FILE_SESSIONS, SessionAuditEntry.serializer()) ?: emptyList()
            val updated = (current + entry).takeLast(MAX_SESSIONS)
            writeList(FILE_SESSIONS, updated, SessionAuditEntry.serializer())
        }
    }

    suspend fun countSessionsWithinDays(days: Int): Int = mutex.withLock {
        withContext(ioDispatcher) {
            val all = readList(FILE_SESSIONS, SessionAuditEntry.serializer()) ?: return@withContext 0
            val cutoffMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - days * MS_PER_DAY
            all.count { it.openedAtEpochMs >= cutoffMs }
        }
    }

    // ── Favorites audit log ───────────────────────────────────────────────────

    suspend fun appendFavoriteAudit(entry: FavoriteAuditEntry) = mutex.withLock {
        withContext(ioDispatcher) {
            val current = readList(FILE_FAVORITES, FavoriteAuditEntry.serializer()) ?: emptyList()
            writeList(FILE_FAVORITES, current + entry, FavoriteAuditEntry.serializer())
        }
    }

    suspend fun readFavoritesAudit(): List<FavoriteAuditEntry> = mutex.withLock {
        withContext(ioDispatcher) {
            readList(FILE_FAVORITES, FavoriteAuditEntry.serializer()) ?: emptyList()
        }
    }

    /**
     * Wipe everything — exposed for the "Delete my local history" button in settings.
     */
    suspend fun wipeAll() = mutex.withLock {
        withContext(ioDispatcher) {
            runCatching { fs.deleteFile("$BASE_DIR/$FILE_TRIPS") }
            runCatching { fs.deleteFile("$BASE_DIR/$FILE_FAVORITES") }
            runCatching { fs.deleteFile("$BASE_DIR/$FILE_SESSIONS") }
        }
    }

    // ── Internal I/O helpers ─────────────────────────────────────────────────

    private fun <T> readList(fileName: String, elementSerializer: KSerializer<T>): List<T>? {
        val path = "$BASE_DIR/$fileName"
        return try {
            val text = fs.readFile(path) ?: return null
            if (text.isBlank()) null
            else json.decodeFromString(ListSerializer(elementSerializer), text)
        } catch (e: Exception) {
            Log.e(TAG, "Corrupted $fileName, dropping: ${e.message}")
            runCatching { fs.deleteFile(path) }
            null
        }
    }

    private fun <T> writeList(fileName: String, value: List<T>, elementSerializer: KSerializer<T>) {
        try {
            val path = "$BASE_DIR/$fileName"
            val encoded = json.encodeToString(ListSerializer(elementSerializer), value)
            // Single overwrite. The previous "delete target then rewrite" left a window where a
            // crash between the delete and the write lost the data entirely (and the tmp copy was
            // never read back on load). writeFile overwrites in place — atomic on iOS
            // (NSString.writeToFile atomically=true) and a plain truncate-write on Android.
            fs.writeFile(path, encoded)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist $fileName: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "LocalHistoryStorage"
        private const val BASE_DIR = "local_history"
        private const val FILE_TRIPS = "trips.json"
        private const val FILE_FAVORITES = "favorites_audit.json"
        private const val FILE_SESSIONS = "sessions.json"
        private const val MS_PER_DAY = 24L * 60 * 60 * 1000
        private const val MAX_SESSIONS = 365
    }
}

/**
 * One session boundary kept locally. Only the count over a sliding 30-day window is exposed
 * to the [LocalProfileComputer] — the raw timestamps stay on device.
 */
@Serializable
data class SessionAuditEntry(
    @SerialName("opened_at_epoch_ms") val openedAtEpochMs: Long
)

/**
 * One completed trip kept locally. Richer than the telemetry TripCompleted event
 * because we also keep timestamps in epoch_ms and a `lines_used` field already
 * correlated against GTFS so the profile doesn't need to re-do that work on every flush.
 */
@Serializable
data class LocalTripRecord(
    @SerialName("started_at_epoch_ms") val startedAtEpochMs: Long,
    @SerialName("ended_at_epoch_ms") val endedAtEpochMs: Long,
    @SerialName("stops_passed") val stopsPassed: List<String>,
    @SerialName("lines_used") val linesUsed: List<String> = emptyList()
)

/**
 * Favorite add/remove audit. Kept for UI history; **never** uploaded.
 */
@Serializable
data class FavoriteAuditEntry(
    @SerialName("at_epoch_ms") val atEpochMs: Long,
    val action: String,                              // "added" | "removed"
    @SerialName("favorite_type") val favoriteType: String,  // "stop" | "address" | "line"
    @SerialName("ref_id") val refId: String? = null  // stop_id / line_id ; null for address
)
