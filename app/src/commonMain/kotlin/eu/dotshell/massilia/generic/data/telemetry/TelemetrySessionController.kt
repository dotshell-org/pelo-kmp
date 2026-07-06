package eu.dotshell.massilia.generic.data.telemetry

import eu.dotshell.massilia.platform.BackgroundScheduler
import kotlin.concurrent.Volatile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Platform-agnostic session debounce orchestration for telemetry.
 *
 * A platform observer (Android: `ProcessLifecycleOwner` via `TelemetryService`) forwards
 * app-wide foreground/background transitions here:
 *  - [onForeground] opens a session, cancels any pending close-triggered upload, and
 *    starts a periodic coroutine loop to flush accumulated events every 60 seconds.
 *  - [onBackground] closes the session, cancels the periodic loop, and schedules a
 *    debounced upload via [BackgroundScheduler] to flush any remaining teardown events.
 */
class TelemetrySessionController(
    private val scheduler: BackgroundScheduler,
    private val debounceSeconds: Long
) {

    @Volatile
    private var activeSessionId: String? = null

    private var periodicUploadJob: Job? = null

    fun onForeground() {
        scheduler.cancelTelemetryUpload()
        activeSessionId = TelemetryEmitter.openSession()

        val scope = TelemetryEmitter.scope()
        if (scope != null) {
            periodicUploadJob?.cancel()
            periodicUploadJob = scope.launch {
                while (isActive) {
                    delay(60_000L)
                    val optIn = TelemetryEmitter.optInManager()
                    if (optIn != null && optIn.isOptedIn) {
                        val repo = TelemetryEmitter.repository()
                        if (repo != null) {
                            val snapshot = repo.snapshotPendingForUpload()
                            if (snapshot != null) {
                                TelemetryUploader.uploadOnce(attemptCount = 0)
                            }
                        }
                    }
                }
            }
        }
    }

    fun onBackground() {
        periodicUploadJob?.cancel()
        periodicUploadJob = null

        val sessionId = activeSessionId ?: return
        TelemetryEmitter.closeSession(sessionId)
        activeSessionId = null
        scheduler.scheduleTelemetryUpload(debounceSeconds.coerceAtLeast(0))
    }
}
