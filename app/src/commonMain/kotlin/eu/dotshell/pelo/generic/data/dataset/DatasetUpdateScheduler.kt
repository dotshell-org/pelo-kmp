package eu.dotshell.pelo.generic.data.dataset

import eu.dotshell.pelo.platform.Log

/**
 * Decides WHEN an over-the-air dataset check may run, and drives it. Two gates, both
 * meant to respect the user: never more than once per [minIntervalMs], and only on an
 * unmetered network — a full dataset is multi-megabyte, and pushing that over cellular
 * unasked is exactly the kind of thing that earns a one-star review.
 */
class DatasetUpdateScheduler(
    private val manager: DatasetUpdateManager,
    private val storage: DatasetStorage,
    private val isUnmetered: () -> Boolean,
    private val now: () -> Long,
    private val minIntervalMs: Long = DEFAULT_INTERVAL_MS
) {
    companion object {
        const val DEFAULT_INTERVAL_MS = 24L * 60 * 60 * 1000 // once a day
        private const val TAG = "DatasetUpdateScheduler"

        /**
         * Pure gate: may a check run now? A non-positive [lastCheck] (never checked)
         * always passes the interval, so the first eligible unmetered launch checks.
         */
        fun shouldCheck(now: Long, lastCheck: Long, unmetered: Boolean, minIntervalMs: Long): Boolean =
            unmetered && (lastCheck <= 0L || now - lastCheck >= minIntervalMs)
    }

    /**
     * Runs a check if both gates allow it, recording the attempt so the interval holds.
     * Returns the outcome, or null when a gate blocked it. Safe to call at every launch.
     */
    suspend fun maybeCheck(): CheckResult? {
        if (!shouldCheck(now(), lastCheck(), isUnmetered(), minIntervalMs)) return null
        // Record BEFORE the network call so a failure still spaces out retries.
        recordCheck(now())
        val result = manager.checkForUpdate()
        Log.i(TAG, "Dataset check: $result")
        return result
    }

    /** Forces a check regardless of interval or network (the settings "check now" action). */
    suspend fun checkNow(): CheckResult {
        recordCheck(now())
        return manager.checkForUpdate()
    }

    private fun lastCheck(): Long =
        storage.readText(lastCheckFile)?.trim()?.toLongOrNull() ?: 0L

    private fun recordCheck(atMillis: Long) {
        storage.writeBytes(lastCheckFile, atMillis.toString().encodeToByteArray())
    }

    private val lastCheckFile: String get() = "${storage.root}/last_check"
}
