package com.pelotcl.app.generic.data.telemetry

import android.content.Context
import android.util.Log
import com.pelotcl.app.generic.data.config.TelemetryConfigData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-wide entry point for telemetry. Call sites in the rest of the codebase only need to
 * call [emit], [openSession], or [closeSession] — they do not depend on the storage, the
 * daily id provider, or the opt-in state directly.
 *
 * Lifecycle:
 *  - [initialize] is called once from [com.pelotcl.app.PeloApplication.onCreate].
 *  - After that, all methods are safe to call from any thread; emits are dispatched onto
 *    an internal [Dispatchers.IO] scope to keep call sites non-blocking.
 *
 * Opt-in gating:
 *  - If the user has not opted in, every emit is a no-op (no allocation beyond the function call).
 *  - Pending events from before opt-out are kept on disk until the user explicitly wipes them
 *    from the settings screen (so they can be inspected first if needed).
 */
object TelemetryEmitter {

    private const val TAG = "TelemetryEmitter"

    private val componentsRef = AtomicReference<Components?>(null)

    private data class Components(
        val optIn: OptInManager,
        val dailyIdProvider: DailyIdProvider,
        val repository: DailyReportRepository,
        val config: TelemetryConfigData,
        val scope: CoroutineScope
    )

    fun initialize(context: Context, config: TelemetryConfigData) {
        if (componentsRef.get() != null) {
            Log.w(TAG, "initialize() called twice — ignoring")
            return
        }
        if (!config.enabled) {
            Log.i(TAG, "Telemetry disabled in config — emitter remains inactive")
            return
        }

        val appCtx = context.applicationContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val optIn = OptInManager(appCtx)
        val dailyIdProvider = DailyIdProvider(appCtx)
        val storage = TelemetryStorage(appCtx)
        val appVersion = resolveAppVersion(appCtx)

        val repository = DailyReportRepository(
            storage = storage,
            scope = scope,
            networkCode = config.networkCode,
            appVersion = appVersion,
            schemaVersion = config.schemaVersion
        )

        componentsRef.set(
            Components(
                optIn = optIn,
                dailyIdProvider = dailyIdProvider,
                repository = repository,
                config = config,
                scope = scope
            )
        )

        // Bootstrap repository state from disk (if any) for the current daily id.
        val rotation = dailyIdProvider.currentOrRotate()
        scope.launch {
            repository.initFor(rotation.id, rotation.day)
        }
    }

    fun isInitialized(): Boolean = componentsRef.get() != null

    fun optInManager(): OptInManager? = componentsRef.get()?.optIn

    fun config(): TelemetryConfigData? = componentsRef.get()?.config

    fun repository(): DailyReportRepository? = componentsRef.get()?.repository

    fun dailyIdProvider(): DailyIdProvider? = componentsRef.get()?.dailyIdProvider

    /**
     * Emit a generic telemetry event. No-op if not initialized or user is not opted-in.
     */
    fun emit(event: TelemetryEvent) {
        val c = componentsRef.get() ?: return
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
        val c = componentsRef.get() ?: return null
        if (!c.optIn.isOptedIn) return null
        val sessionId = UUID.randomUUID().toString()
        val openedAt = Instant.now().toString()
        c.scope.launch {
            ensureDailyIdFresh(c)
            c.repository.openSession(sessionId, openedAt)
        }
        return sessionId
    }

    fun closeSession(sessionId: String) {
        val c = componentsRef.get() ?: return
        if (!c.optIn.isOptedIn) return
        val closedAt = Instant.now().toString()
        c.scope.launch {
            ensureDailyIdFresh(c)
            c.repository.closeSession(sessionId, closedAt)
        }
    }

    /**
     * Check if the daily id has rotated since the last emit. If so, the previous day's state
     * is reset (we expect the [TelemetryUploader] to have flushed it during the rotation
     * handling at the previous app shutdown — if not, the [PendingDelta] is lost). For now
     * we accept this trade-off; a future enhancement can persist a per-daily_id state file.
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

    private fun resolveAppVersion(context: Context): String {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName ?: "unknown"
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve app version", e)
            "unknown"
        }
    }
}
