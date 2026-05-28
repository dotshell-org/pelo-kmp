package com.pelotcl.app.generic.data.telemetry

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream

/**
 * Background worker that turns the [DailyReportRepository]'s pending delta into a single POST
 * to the telemetry endpoint, gzipped.
 *
 * Scheduling:
 *  - Enqueued from [TelemetryService] on session-close debounce expiry, with a unique work name
 *    so multiple close events coalesce into a single upload attempt.
 *  - Constraint: [NetworkType.CONNECTED]. If offline, the OS will run us when connectivity comes
 *    back, even if the app is killed.
 *  - On failure, returns [Result.retry] which lets WorkManager apply exponential backoff. After
 *    5 failed attempts we give up and let the next close trigger a fresh attempt.
 *
 * Idempotency:
 *  - We grab a snapshot from the repository, send it, and only call [DailyReportRepository.markSent]
 *    on a 2xx response. If the worker is killed between the network round-trip and the markSent
 *    call, the next attempt will simply re-send the same events (server dedups by event_id).
 */
class TelemetryUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun doWork(): Result {
        val repo = TelemetryEmitter.repository() ?: return Result.success()
        val config = TelemetryEmitter.config() ?: return Result.success()
        val optIn = TelemetryEmitter.optInManager()
        if (optIn == null || !optIn.isOptedIn) return Result.success()

        // Ensure repository is initialized. On process restart WorkManager may start the
        // worker before the app's async repository.initFor has completed, leaving
        // repo.state.value == null and making snapshotPendingForUpload return null.
        if (repo.state.value == null) {
            val provider = TelemetryEmitter.dailyIdProvider()
            val dailyId = provider?.peek()
            val day = provider?.peekDay()
            if (dailyId != null && day != null) {
                try {
                    repo.initFor(dailyId, day)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to init telemetry repo for upload", e)
                    return Result.retry()
                }
            } else {
                Log.i(TAG, "No daily id available; nothing to upload")
                return Result.success()
            }
        }

        val snapshot = repo.snapshotPendingForUpload() ?: return Result.success()
        val localHistory = TelemetryEmitter.localHistory()
        val profile = if (localHistory != null) {
            LocalProfileComputer.compute(localHistory, windowDays = config.profileWindowDays)
        } else {
            Profile(usageStatus = "occasional", habitualLines = emptyList())
        }
        val rawMessage = Message(
            dailyId = snapshot.dailyId,
            networkCode = snapshot.networkCode,
            appVersion = snapshot.appVersion,
            schemaVersion = snapshot.schemaVersion,
            sentAt = Instant.now().toString(),
            trigger = TRIGGER_SESSION_CLOSED,
            day = snapshot.day,
            sessions = snapshot.sessions,
            profile = profile,
            events = snapshot.events
        )
        // Last belt-and-suspenders: drop any event whose `kind` is not in the CGU allowlist
        // (catches accidental schema additions in future Vagues) and cap per-kind cardinality.
        val message = PayloadGuardrail.sanitize(rawMessage)
        val payload = json.encodeToString(Message.serializer(), message)
        // Inspect the serialized form for surprises like oversized strings. We still send
        // when introspection flags a problem (the typed pipeline is the primary defense), but
        // the warning shows up in Logcat / Crashlytics for the next dev to investigate.
        PayloadGuardrail.assertNoSurprises(payload)
        val gzipped = gzip(payload)

        return try {
            val client = httpClient
            val req = Request.Builder()
                .url(config.endpointUrl)
                .header("Content-Type", "application/json")
                .header("Content-Encoding", "gzip")
                .post(gzipped.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    repo.markSent(snapshot.eventIds, snapshot.sessionIds)
                    Result.success()
                } else {
                    Log.w(TAG, "Upload failed HTTP ${response.code}")
                    if (runAttemptCount >= MAX_ATTEMPTS) Result.failure() else Result.retry()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Upload threw, will retry", e)
            if (runAttemptCount >= MAX_ATTEMPTS) Result.failure() else Result.retry()
        }
    }

    private fun gzip(text: String): ByteArray {
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return baos.toByteArray()
    }

    companion object {
        private const val TAG = "TelemetryUploadWorker"
        private const val MAX_ATTEMPTS = 5
        private const val TRIGGER_SESSION_CLOSED = "session_closed"
        val UNIQUE_WORK_NAME = "telemetry_upload"

        private val JSON_MEDIA_TYPE = "application/json".toMediaTypeOrNull()

        /**
         * Lazily-built OkHttpClient — singleton across upload attempts and lifecycle restarts.
         * 15s connect / 30s read / 30s write fit a small gzipped POST comfortably.
         */
        private val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }

        fun buildRequest(): OneTimeWorkRequest.Builder {
            return OneTimeWorkRequestBuilder<TelemetryUploadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
        }
    }
}
