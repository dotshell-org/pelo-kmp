package eu.dotshell.massilia.generic.data.telemetry

import eu.dotshell.massilia.platform.ioDispatcher

import eu.dotshell.massilia.generic.data.config.TelemetryConfigData
import eu.dotshell.massilia.generic.data.local_history.LocalHistoryStorage
import eu.dotshell.massilia.generic.data.local_history.SessionAuditEntry
import eu.dotshell.massilia.platform.Log
import eu.dotshell.massilia.platform.PlatformContext
import eu.dotshell.massilia.platform.appVersionName
import eu.dotshell.massilia.platform.randomId
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Process-wide entry point for telemetry, shared across platforms.
 *
 * Call sites in the rest of the codebase only need to call [emit], [openSession], or
 * [closeSession] — they do not depend on the storage, the daily id provider, or the
 * opt-in state directly.
 *
 * Lifecycle:
 *  - [initialize] is called once at startup (Android: `MassiliaApplication.onCreate`).
 *  - After that, all methods are safe to call from any thread; emits are dispatched onto
 *    an internal [ioDispatcher] scope to keep call sites non-blocking.
 *
 * Opt-in gating:
 *  - If the user has not opted in, every emit is a no-op (no allocation beyond the function call).
 *  - Pending events from before opt-out are kept on disk until the user explicitly wipes them
 *    from the settings screen (so they can be inspected first if needed).
 *
 * Background scheduling (debounced uploads, foreground/background session transitions) stays
 * platform-specific — see the Android `TelemetryService`.
 */
object TelemetryEmitter {

    private const val TAG = "TelemetryEmitter"

    @Volatile
    private var components: Components? = null

    private data class Components(
        val optIn: OptInManager,
        val dailyIdProvider: DailyIdProvider,
        val repository: DailyReportRepository,
        val config: TelemetryConfigData,
        val scope: CoroutineScope,
        val localHistory: LocalHistoryStorage
    )

    fun initialize(context: PlatformContext, config: TelemetryConfigData) {
        if (components != null) {
            Log.w(TAG, "initialize() called twice — ignoring")
            return
        }
        if (!config.enabled) {
            Log.i(TAG, "Telemetry disabled in config — emitter remains inactive")
            return
        }

        val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
        val optIn = OptInManager(context)
        val dailyIdProvider = DailyIdProvider(context)
        val storage = TelemetryStorage(context)
        val localHistory = LocalHistoryStorage(context)
        val appVersion = appVersionName(context)

        val repository = DailyReportRepository(
            storage = storage,
            scope = scope,
            networkCode = config.networkCode,
            appVersion = appVersion,
            schemaVersion = config.schemaVersion
        )

        components = Components(
            optIn = optIn,
            dailyIdProvider = dailyIdProvider,
            repository = repository,
            config = config,
            scope = scope,
            localHistory = localHistory
        )

        // Bootstrap repository state from disk (if any) for the current daily id.
        val rotation = dailyIdProvider.currentOrRotate()
        scope.launch {
            repository.initFor(rotation.id, rotation.day)

            val currentState = repository.state.value
            if (currentState != null) {
                val snapshot = repository.snapshotPendingForUpload()
                if (snapshot != null) {
                    val lastMod = try {
                        Instant.parse(currentState.lastModifiedAt)
                    } catch (e: Exception) {
                        null
                    }
                    if (lastMod != null) {
                        val durationSinceLastMod = Clock.System.now() - lastMod
                        if (durationSinceLastMod.inWholeMinutes >= 5) {
                            Log.i(TAG, "Unsent telemetry found (last modified ${durationSinceLastMod.inWholeMinutes} minutes ago). Triggering upload...")
                            launch {
                                val outcome = TelemetryUploader.uploadOnce(attemptCount = 0)
                                Log.i(TAG, "Startup telemetry upload finished with outcome: $outcome")
                            }
                        }
                    }
                }
            }
        }
    }

    fun isInitialized(): Boolean = components != null

    fun optInManager(): OptInManager? = components?.optIn

    fun config(): TelemetryConfigData? = components?.config

    fun repository(): DailyReportRepository? = components?.repository

    fun dailyIdProvider(): DailyIdProvider? = components?.dailyIdProvider

    fun localHistory(): LocalHistoryStorage? = components?.localHistory

    fun scope(): CoroutineScope? = components?.scope

    /**
     * Emit a generic telemetry event. No-op if not initialized or user is not opted-in.
     */
    fun emit(event: TelemetryEvent) {
        val c = components ?: return
        if (!c.optIn.isOptedIn) return
        c.scope.launch {
            ensureDailyIdFresh(c)
            c.repository.appendEvent(event)
        }
    }

    /**
     * Opens a new session locally. Returns the session_id so the lifecycle observer can match it
     * to the eventual close.
     */
    fun openSession(): String? {
        val c = components ?: return null
        if (!c.optIn.isOptedIn) return null
        val sessionId = randomId()
        val now = Clock.System.now()
        val openedAt = now.toString()
        val openedAtMs = now.toEpochMilliseconds()
        c.scope.launch {
            ensureDailyIdFresh(c)
            c.repository.openSession(sessionId, openedAt)
            // Mirror the open into the LOCAL-ONLY session log so [LocalProfileComputer] can
            // see the trailing 30-day session count even after daily_id rotations clear the
            // current report's session list.
            c.localHistory.appendSession(SessionAuditEntry(openedAtEpochMs = openedAtMs))
        }
        return sessionId
    }

    fun closeSession(sessionId: String) {
        val c = components ?: return
        if (!c.optIn.isOptedIn) return
        val closedAt = Clock.System.now().toString()
        c.scope.launch {
            ensureDailyIdFresh(c)
            c.repository.closeSession(sessionId, closedAt)
        }
    }

    /**
     * Erase every pending telemetry artifact from disk and rotate the daily id so the previous
     * one is no longer referenced anywhere on device. Intended to be called from the opt-out
     * flow (after the user flips the switch off): even if a future opt-in happens, the
     * accumulated state from the previous opted-in window stays buried.
     *
     * Does NOT touch [LocalHistoryStorage] (trips + favorites audit + session log) — those are
     * pure user-facing local data and have their own dedicated "Supprimer mon historique
     * local" button.
     */
    fun wipePendingAndState() {
        val c = components ?: return
        c.scope.launch {
            // Reset the in-memory + on-disk daily report and pending delta.
            val rotation = c.dailyIdProvider.currentOrRotate()
            c.repository.resetForNewDay(rotation.id, rotation.day)
            // Then drop the daily id itself so the next opt-in starts cleanly.
            c.dailyIdProvider.clear()
        }
    }

    /**
     * Check if the daily id has rotated since the last emit. If so, the previous day's state
     * is reset (we expect the upload worker to have flushed it during the rotation handling at
     * the previous app shutdown — if not, the [PendingDelta] is lost). For now we accept this
     * trade-off; a future enhancement can persist a per-daily_id state file.
     */
    private suspend fun ensureDailyIdFresh(c: Components) {
        val rotation = c.dailyIdProvider.currentOrRotate()
        val currentStateId = c.repository.state.value?.dailyId
        if (currentStateId != rotation.id) {
            // Either bootstrap (currentStateId == null) or genuine rotation
            if (currentStateId == null) {
                c.repository.initFor(rotation.id, rotation.day)
            } else {
                Log.i(
                    TAG,
                    "daily_id rotated mid-process ($currentStateId -> ${rotation.id}); resetting state"
                )
                c.repository.resetForNewDay(rotation.id, rotation.day)
            }
        }
    }
}
