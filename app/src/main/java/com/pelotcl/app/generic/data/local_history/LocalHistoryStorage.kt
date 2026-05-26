package com.pelotcl.app.generic.data.local_history

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persistent, append-only history kept **100% on device** — never uploaded.
 *
 * Used to:
 *  1. Feed the [LocalProfileComputer] (usage status + habitual lines on 30-day window).
 *  2. Power a future user-facing "Mes trajets" screen (transparency benefit in
 *     exchange for opt-in).
 *
 * Storage: a single JSON file per category in `filesDir/local_history/`. Reads are
 * loaded fully into memory because the working set is small (30 days × ~10 trips
 * × small payload ≈ a few KB). For longer-term retention we may switch to
 * day-bucketed files later.
 *
 * The favorites audit log captures add/remove events for "Mes favoris" — kept for
 * UI history only, never propagated to telemetry.
 */
class LocalHistoryStorage(context: Context) {

    private val baseDir: File = File(context.filesDir, "local_history").also { it.mkdirs() }
    private val tripFile = File(baseDir, FILE_TRIPS)
    private val favoritesFile = File(baseDir, FILE_FAVORITES)
    private val mutex = Mutex()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    // ---------- Trips ----------

    suspend fun appendTrip(trip: LocalTripRecord) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val current = readList(tripFile, LocalTripRecord.serializer()) ?: emptyList()
            val updated = current + trip
            writeList(tripFile, updated, LocalTripRecord.serializer())
        }
    }

    suspend fun readTripsWithinDays(days: Int): List<LocalTripRecord> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val all = readList(tripFile, LocalTripRecord.serializer()) ?: return@withContext emptyList()
            val cutoffMs = System.currentTimeMillis() - days * MS_PER_DAY
            all.filter { it.endedAtEpochMs >= cutoffMs }
        }
    }

    suspend fun pruneTripsOlderThan(days: Int) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val all = readList(tripFile, LocalTripRecord.serializer()) ?: return@withContext
            val cutoffMs = System.currentTimeMillis() - days * MS_PER_DAY
            val pruned = all.filter { it.endedAtEpochMs >= cutoffMs }
            if (pruned.size != all.size) {
                writeList(tripFile, pruned, LocalTripRecord.serializer())
            }
        }
    }

    // ---------- Favorites audit log ----------

    suspend fun appendFavoriteAudit(entry: FavoriteAuditEntry) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val current = readList(favoritesFile, FavoriteAuditEntry.serializer()) ?: emptyList()
            writeList(favoritesFile, current + entry, FavoriteAuditEntry.serializer())
        }
    }

    suspend fun readFavoritesAudit(): List<FavoriteAuditEntry> = mutex.withLock {
        withContext(Dispatchers.IO) {
            readList(favoritesFile, FavoriteAuditEntry.serializer()) ?: emptyList()
        }
    }

    /**
     * Wipe everything — exposed for the "Delete my local history" button in settings.
     */
    suspend fun wipeAll() = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching { tripFile.delete() }
            runCatching { favoritesFile.delete() }
        }
    }

    private fun <T> readList(
        file: File,
        elementSerializer: kotlinx.serialization.KSerializer<T>
    ): List<T>? {
        if (!file.exists()) return null
        return try {
            val text = file.readText(Charsets.UTF_8)
            if (text.isBlank()) null
            else json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(elementSerializer),
                text
            )
        } catch (e: Exception) {
            Log.e(TAG, "Corrupted ${file.name}, dropping", e)
            runCatching { file.delete() }
            null
        }
    }

    private fun <T> writeList(
        file: File,
        value: List<T>,
        elementSerializer: kotlinx.serialization.KSerializer<T>
    ) {
        try {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(
                json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(elementSerializer),
                    value
                ),
                Charsets.UTF_8
            )
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist ${file.name}", e)
        }
    }

    companion object {
        private const val TAG = "LocalHistoryStorage"
        private const val FILE_TRIPS = "trips.json"
        private const val FILE_FAVORITES = "favorites_audit.json"
        private const val MS_PER_DAY = 24L * 60 * 60 * 1000
    }
}

/**
 * One completed trip kept locally. Richer than the telemetry [TelemetryEvent.TripCompleted]
 * because we also keep timestamps in epoch_ms (cheaper to compare in the profile computer)
 * and a `lines_used` field already correlated against GTFS so the profile doesn't need to
 * re-do that work on every flush.
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
    val action: String,                                // "added" | "removed"
    @SerialName("favorite_type") val favoriteType: String,    // "stop" | "address" | "line"
    @SerialName("ref_id") val refId: String? = null    // stop_id / line_id ; null for address
)
