package eu.dotshell.pelo.generic.data.telemetry

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/**
 * Near-real-time upload driver: every telemetry action asks for a flush, which is debounced a
 * few seconds so that a burst of actions (e.g. `itinerary_calculated` immediately followed by
 * `itinerary_chosen`, or several quick searches) coalesces into a single upload, while an
 * isolated action is still shipped within [debounceMs].
 *
 * This replaces the old 60-second foreground polling loop in [TelemetrySessionController]: the
 * trigger is now the action itself, not a fixed timer or the session-close transition.
 *
 * Mutual exclusion between overlapping uploads (this scheduler, the background scheduler, the
 * startup catch-up, WorkManager) is guaranteed downstream by [TelemetryUploader.uploadOnce], so
 * this class only owns the debounce. Any event appended while an upload is in flight will have
 * requested its own flush, so it is never lost.
 */
class TelemetryFlushScheduler(
    private val scope: CoroutineScope,
    private val debounceMs: Long
) {

    @Volatile
    private var debounceJob: Job? = null

    /**
     * Ask for an upload [debounceMs] after the last call in a burst. Repeated calls within the
     * window restart the timer, so the whole burst ships in one request.
     */
    fun requestFlush() {
        // Restart the debounce window. The uploader serializes overlapping attempts, so a
        // benign double-schedule (two threads racing here) at worst yields one empty no-op POST.
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(debounceMs)
            TelemetryUploader.uploadOnce(
                attemptCount = 0,
                trigger = TelemetryUploader.TRIGGER_ACTIVITY
            )
        }
    }
}
