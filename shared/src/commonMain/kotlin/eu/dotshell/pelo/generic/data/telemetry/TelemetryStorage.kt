package eu.dotshell.pelo.generic.data.telemetry

import eu.dotshell.pelo.platform.ioDispatcher

import eu.dotshell.pelo.platform.FileSystem
import eu.dotshell.pelo.platform.Log
import eu.dotshell.pelo.platform.PlatformContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * JSON-file backed persistence for telemetry state. Two files per app installation:
 *
 *   filesDir/telemetry/state.json    → [DailyReportState] for the current daily_id
 *   filesDir/telemetry/pending.json  → [PendingDelta] for the current daily_id
 *
 * Multiplatform: uses [FileSystem] abstraction instead of java.io.File + Context.
 *
 * Atomicity: writes go to a `*.tmp` sibling and are then overwritten to the target
 * file to avoid partial-write corruption on crash.
 */
class TelemetryStorage(context: PlatformContext) {

    private val fs = FileSystem(context)
    private val mutex = Mutex()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
        prettyPrint = false
    }

    suspend fun readState(): DailyReportState? = mutex.withLock {
        readJson(FILE_STATE, DailyReportState.serializer())
    }

    suspend fun writeState(state: DailyReportState) = mutex.withLock {
        writeJson(FILE_STATE, state, DailyReportState.serializer())
    }

    suspend fun readPending(): PendingDelta? = mutex.withLock {
        readJson(FILE_PENDING, PendingDelta.serializer())
    }

    suspend fun writePending(pending: PendingDelta) = mutex.withLock {
        writeJson(FILE_PENDING, pending, PendingDelta.serializer())
    }

    /**
     * Reset both state and pending. Used when the daily_id rotates after the previous
     * day's final flush succeeded.
     */
    suspend fun reset() = mutex.withLock {
        withContext(ioDispatcher) {
            runCatching { fs.deleteFile("$BASE_DIR/$FILE_STATE") }
            runCatching { fs.deleteFile("$BASE_DIR/$FILE_PENDING") }
        }
    }

    private suspend fun <T> readJson(fileName: String, serializer: KSerializer<T>): T? =
        withContext(ioDispatcher) {
            try {
                val text = fs.readFile("$BASE_DIR/$fileName") ?: return@withContext null
                json.decodeFromString(serializer, text)
            } catch (e: Exception) {
                Log.e(TAG, "Corrupted $fileName, dropping: ${e.message}")
                runCatching { fs.deleteFile("$BASE_DIR/$fileName") }
                null
            }
        }

    private suspend fun <T> writeJson(fileName: String, value: T, serializer: KSerializer<T>) =
        withContext(ioDispatcher) {
            try {
                val encoded = json.encodeToString(serializer, value)
                // Write to tmp first, then overwrite target for atomicity
                val tmpPath = "$BASE_DIR/${fileName}.tmp"
                fs.writeFile(tmpPath, encoded)
                fs.deleteFile("$BASE_DIR/$fileName")
                fs.writeFile("$BASE_DIR/$fileName", encoded)
                fs.deleteFile(tmpPath)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist $fileName: ${e.message}")
            }
        }

    companion object {
        private const val TAG = "TelemetryStorage"
        private const val BASE_DIR = "telemetry"
        private const val FILE_STATE = "state.json"
        private const val FILE_PENDING = "pending.json"
    }
}
