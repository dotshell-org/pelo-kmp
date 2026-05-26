package com.pelotcl.app.generic.data.telemetry

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * JSON-file backed persistence for telemetry state. Two files per app installation:
 *
 *   filesDir/telemetry/state.json    → [DailyReportState] for the current daily_id
 *   filesDir/telemetry/pending.json  → [PendingDelta] for the current daily_id
 *
 * The choice of flat files (instead of Room) is intentional: telemetry payloads
 * are small (a few KB / day), reads happen at most once per session close, and
 * we avoid pulling in KSP + Room which would complicate the Kotlin 2.3 / AGP 9
 * toolchain.
 *
 * Concurrency:
 * - All disk IO happens on [Dispatchers.IO].
 * - In-process reads/writes are guarded by [mutex]; cross-process is not a concern
 *   (Pelo runs in a single process).
 *
 * Atomicity:
 * - Writes go to a `*.tmp` sibling and are then renamed over the target file to
 *   avoid partial-write corruption on crash.
 */
class TelemetryStorage(context: Context) {

    private val baseDir: File = File(context.filesDir, "telemetry").also { it.mkdirs() }
    private val stateFile = File(baseDir, FILE_STATE)
    private val pendingFile = File(baseDir, FILE_PENDING)
    private val mutex = Mutex()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
        prettyPrint = false
    }

    suspend fun readState(): DailyReportState? = mutex.withLock {
        readJson(stateFile, DailyReportState.serializer())
    }

    suspend fun writeState(state: DailyReportState) = mutex.withLock {
        writeJson(stateFile, state, DailyReportState.serializer())
    }

    suspend fun readPending(): PendingDelta? = mutex.withLock {
        readJson(pendingFile, PendingDelta.serializer())
    }

    suspend fun writePending(pending: PendingDelta) = mutex.withLock {
        writeJson(pendingFile, pending, PendingDelta.serializer())
    }

    /**
     * Reset both state and pending. Used when the daily_id rotates after the previous
     * day's final flush succeeded.
     */
    suspend fun reset() = mutex.withLock {
        runCatching { stateFile.delete() }
        runCatching { pendingFile.delete() }
    }

    private suspend fun <T> readJson(
        file: File,
        serializer: kotlinx.serialization.KSerializer<T>
    ): T? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        try {
            json.decodeFromString(serializer, file.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            Log.e(TAG, "Corrupted ${file.name}, dropping", e)
            runCatching { file.delete() }
            null
        }
    }

    private suspend fun <T> writeJson(
        file: File,
        value: T,
        serializer: kotlinx.serialization.KSerializer<T>
    ) = withContext(Dispatchers.IO) {
        try {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(json.encodeToString(serializer, value), Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                // renameTo can fail on some FS — fall back to copy+delete
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist ${file.name}", e)
        }
    }

    companion object {
        private const val TAG = "TelemetryStorage"
        private const val FILE_STATE = "state.json"
        private const val FILE_PENDING = "pending.json"
    }
}
