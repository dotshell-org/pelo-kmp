package com.pelotcl.app.generic.data.telemetry

import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.pelotcl.app.generic.data.config.TelemetryConfigData
import java.time.Duration

/**
 * Orchestrates the session lifecycle and the debounced upload trigger.
 *
 * Wires into [ProcessLifecycleOwner] so we observe app-wide foreground/background transitions
 * (not per-Activity), matching the user's notion of "open" / "close".
 *
 * Debounce mechanics:
 *  - On [onStart] we open a session via [TelemetryEmitter.openSession] and cancel any
 *    in-flight upload work scheduled by a previous close — this is the "user came back within
 *    the debounce window" case.
 *  - On [onStop] we close the session and enqueue a [TelemetryUploadWorker] with an
 *    initial delay equal to [TelemetryConfigData.closeDebounceSeconds]. WorkManager guarantees
 *    a single instance under [TelemetryUploadWorker.UNIQUE_WORK_NAME] thanks to
 *    [ExistingWorkPolicy.REPLACE].
 *  - WorkManager itself does not have a "cancel scheduled work" hook on a foreground event,
 *    so we explicitly cancel by unique name from [onStart].
 *
 * This object is initialized exactly once from [com.pelotcl.app.PeloApplication.onCreate].
 */
object TelemetryService {

    private const val TAG = "TelemetryService"

    @Volatile
    private var initialized = false

    @Volatile
    private var activeSessionId: String? = null

    private lateinit var appContext: Context
    private lateinit var config: TelemetryConfigData

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            handleForeground()
        }

        override fun onStop(owner: LifecycleOwner) {
            handleBackground()
        }
    }

    fun initialize(context: Context, config: TelemetryConfigData) {
        if (initialized) return
        if (!config.enabled) {
            Log.i(TAG, "Telemetry disabled in config — service inactive")
            return
        }
        appContext = context.applicationContext
        this.config = config
        TelemetryEmitter.initialize(appContext, config)
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
        initialized = true
        Log.i(TAG, "TelemetryService initialized")
    }

    private fun handleForeground() {
        // Cancel a pending close-triggered upload if the user came back within the debounce window.
        WorkManager.getInstance(appContext)
            .cancelUniqueWork(TelemetryUploadWorker.UNIQUE_WORK_NAME)

        val sessionId = TelemetryEmitter.openSession() ?: return
        activeSessionId = sessionId
    }

    private fun handleBackground() {
        val sessionId = activeSessionId ?: return
        TelemetryEmitter.closeSession(sessionId)
        activeSessionId = null
        scheduleUpload()
    }

    private fun scheduleUpload() {
        val debounceSeconds = config.closeDebounceSeconds.coerceAtLeast(0)
        val request: OneTimeWorkRequest = TelemetryUploadWorker.buildRequest()
            .setInitialDelay(Duration.ofSeconds(debounceSeconds))
            .build()

        WorkManager.getInstance(appContext)
            .enqueueUniqueWork(
                TelemetryUploadWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
    }
}
