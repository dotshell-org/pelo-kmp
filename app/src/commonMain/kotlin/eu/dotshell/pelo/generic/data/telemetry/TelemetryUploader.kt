package eu.dotshell.pelo.generic.data.telemetry

import eu.dotshell.pelo.platform.Log
import eu.dotshell.pelo.platform.createHttpClientEngine
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

/**
 * Turns the [DailyReportRepository]'s pending delta into a single JSON POST to the telemetry
 * endpoint. Platform-agnostic: the only platform concern (the retry attempt count) is passed in
 * by the caller, and the [Outcome] is mapped to the platform scheduler's retry policy
 * (Android: WorkManager `Result`).
 *
 * Idempotency: we snapshot, send, and only [DailyReportRepository.markSent] on a 2xx response.
 * If the process dies between the round-trip and markSent, the next attempt re-sends the same
 * events (the server dedups by event_id).
 */
object TelemetryUploader {

    private const val TAG = "TelemetryUploader"
    private const val MAX_ATTEMPTS = 5

    // Why this upload fired, recorded on the wire so the backend can tell near-real-time
    // action flushes apart from the background/close fallback and the startup catch-up.
    const val TRIGGER_ACTIVITY = "activity"            // per-action debounced flush (foreground)
    const val TRIGGER_STARTUP = "startup"              // leftover pending shipped at app launch
    const val TRIGGER_SESSION_CLOSED = "session_closed" // background / session-close fallback

    /** Result of a single attempt; the platform layer maps this to its own retry mechanism. */
    enum class Outcome { SUCCESS, RETRY, GIVE_UP }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // Serializes overlapping upload attempts (the per-action debounced flush, the background
    // scheduler, the startup catch-up, WorkManager). Because snapshot → send → markSent runs
    // under this lock, a second caller acquires it only after the first has marked its events
    // sent, so it snapshots the now-empty delta and no event is sent twice.
    private val uploadMutex = Mutex()

    // Singleton across upload attempts and lifecycle restarts (mirrors the old OkHttp lazy client).
    private val httpClient by lazy { HttpClient(createHttpClientEngine()) }

    /**
     * Run a single upload attempt. [attemptCount] is the 0-based retry index from the platform
     * scheduler; once it reaches [MAX_ATTEMPTS] a failure yields [Outcome.GIVE_UP] instead of
     * [Outcome.RETRY]. "Nothing to do" cases (not initialized, opted out, empty delta) yield
     * [Outcome.SUCCESS].
     */
    suspend fun uploadOnce(
        attemptCount: Int,
        trigger: String = TRIGGER_SESSION_CLOSED
    ): Outcome = uploadMutex.withLock {
        uploadOnceLocked(attemptCount, trigger)
    }

    private suspend fun uploadOnceLocked(attemptCount: Int, trigger: String): Outcome {
        val repo = TelemetryEmitter.repository() ?: return Outcome.SUCCESS
        val config = TelemetryEmitter.config() ?: return Outcome.SUCCESS
        val optIn = TelemetryEmitter.optInManager()
        if (optIn == null || !optIn.isOptedIn) return Outcome.SUCCESS

        // On a cold process the worker may run before TelemetryEmitter.initialize's async
        // initFor has completed, leaving state.value == null.
        if (repo.state.value == null) {
            val provider = TelemetryEmitter.dailyIdProvider()
            val dailyId = provider?.peek()
            val day = provider?.peekDay()
            if (dailyId != null && day != null) {
                try {
                    repo.initFor(dailyId, day)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to init telemetry repo for upload: ${e.message}")
                    return Outcome.RETRY
                }
            } else {
                Log.i(TAG, "No daily id available; nothing to upload")
                return Outcome.SUCCESS
            }
        }

        val snapshot = repo.snapshotPendingForUpload() ?: return Outcome.SUCCESS
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
            sentAt = Clock.System.now().toString(),
            trigger = trigger,
            day = snapshot.day,
            sessions = snapshot.sessions,
            profile = profile,
            events = snapshot.events
        )
        // Belt-and-suspenders: drop any event whose `kind` is not in the CGU allowlist and cap
        // per-kind cardinality before serializing.
        val message = PayloadGuardrail.sanitize(rawMessage)
        val payload = json.encodeToString(Message.serializer(), message)
        // Introspect the serialized form for surprises (oversized strings, etc.). Per the
        // guardrail contract we must never silently send a payload that trips it: drop the batch
        // so the suspect data can't leak and won't be retried forever.
        if (!PayloadGuardrail.assertNoSurprises(payload)) {
            Log.e(TAG, "Telemetry payload tripped the guardrail — dropping batch without sending")
            repo.markSent(snapshot.eventIds, snapshot.sessionIds)
            return Outcome.GIVE_UP
        }
        var lastException: Exception? = null
        for (attempt in 1..3) {
            try {
                val response = httpClient.post(config.endpointUrl) {
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }
                if (response.status.isSuccess()) {
                    repo.markSent(snapshot.eventIds, snapshot.sessionIds)
                    return Outcome.SUCCESS
                } else {
                    Log.w(TAG, "Upload failed HTTP ${response.status.value}")
                    if (attemptCount >= MAX_ATTEMPTS) return Outcome.GIVE_UP else return Outcome.RETRY
                }
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Upload attempt $attempt failed: ${e.message}. Retrying in 2s...")
                if (attempt < 3) {
                    kotlinx.coroutines.delay(2000L)
                }
            }
        }

        Log.w(TAG, "All upload attempts failed. Last exception: ${lastException?.message}")
        return if (attemptCount >= MAX_ATTEMPTS) Outcome.GIVE_UP else Outcome.RETRY
    }
}
