package eu.dotshell.pelo.generic.data.telemetry

import eu.dotshell.pelo.platform.BackgroundScheduler
import kotlin.concurrent.Volatile

/**
 * Platform-agnostic session lifecycle bridge for telemetry.
 *
 * A platform observer (Android: `ProcessLifecycleOwner` via `TelemetryService`) forwards
 * app-wide foreground/background transitions here:
 *  - [onForeground] cancels any pending deferred upload and opens a session.
 *  - [onBackground] closes the session and schedules a deferred upload via [BackgroundScheduler]
 *    as a process-death-safe fallback.
 *
 * Near-real-time uploads while in the foreground are no longer this class's concern: each action
 * now drives its own debounced flush from [TelemetryEmitter] (see [TelemetryFlushScheduler]), so
 * there is no periodic polling loop here anymore.
 */
class TelemetrySessionController(
    private val scheduler: BackgroundScheduler,
    private val debounceSeconds: Long
) {

    @Volatile
    private var activeSessionId: String? = null

    fun onForeground() {
        scheduler.cancelTelemetryUpload()
        activeSessionId = TelemetryEmitter.openSession()
    }

    fun onBackground() {
        val sessionId = activeSessionId ?: return
        TelemetryEmitter.closeSession(sessionId)
        activeSessionId = null
        // Fallback for events emitted right before backgrounding: the in-process debounced flush
        // may not complete before the OS suspends us, so schedule a deferred, process-death-safe
        // upload too. Overlap with the in-process flush is de-duplicated by the uploader.
        scheduler.scheduleTelemetryUpload(debounceSeconds.coerceAtLeast(0))
    }
}
