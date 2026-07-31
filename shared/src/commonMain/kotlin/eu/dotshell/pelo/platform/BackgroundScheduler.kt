package eu.dotshell.pelo.platform

/**
 * Schedules deferrable background work that must survive process death.
 *
 * Android: WorkManager (unique work names for coalescing/cancellation).
 * iOS: best-effort stub (a BGTaskScheduler-backed implementation can replace it later).
 */
expect class BackgroundScheduler(context: PlatformContext) {

    /**
     * Schedule a one-shot telemetry upload after [delaySeconds]. Duplicate schedules coalesce
     * into a single pending upload (the latest delay wins).
     */
    fun scheduleTelemetryUpload(delaySeconds: Long)

    /** Cancel a pending telemetry upload (e.g. the user returned within the debounce window). */
    fun cancelTelemetryUpload()

    /** Ensure the periodic traffic-alerts refresh is scheduled (idempotent). */
    fun ensureTrafficAlertsScheduled()
}
