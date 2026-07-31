package eu.dotshell.pelo.generic.data.telemetry

import eu.dotshell.pelo.generic.data.local_history.LocalHistoryStorage

/**
 * Computes the [Profile] that goes into every outgoing [Message].
 *
 * The profile is **derived locally** from data that never leaves the device:
 *  - `usage_status`: bucketed count of sessions in the trailing 30 days.
 *  - `habitual_lines`: top 5 lines used across trips in the trailing 30 days, sorted by frequency.
 *
 * Bucketing thresholds (from the plan §3.5):
 *   sessions < 5  → "occasional"
 *   sessions < 20 → "regular"
 *   else          → "intensive"
 *
 * If there is no local history yet (fresh install or after a wipe), the profile is the safe
 * default `{ "occasional", [] }`.
 *
 * Note on completeness:
 *  - `linesUsed` per trip is populated in Vague 4 once the [TripDetector] is online.
 *  - Until then, `habitual_lines` will be empty even for active users. That is acceptable:
 *    the field is documented as best-effort.
 */
object LocalProfileComputer {

    private const val WINDOW_DAYS_DEFAULT = 30
    private const val OCCASIONAL_THRESHOLD = 5
    private const val REGULAR_THRESHOLD = 20
    private const val HABITUAL_LINES_LIMIT = 5

    suspend fun compute(
        storage: LocalHistoryStorage,
        windowDays: Int = WINDOW_DAYS_DEFAULT
    ): Profile {
        val sessionsCount = storage.countSessionsWithinDays(windowDays)
        val trips = storage.readTripsWithinDays(windowDays)

        val usageStatus = when {
            sessionsCount < OCCASIONAL_THRESHOLD -> "occasional"
            sessionsCount < REGULAR_THRESHOLD -> "regular"
            else -> "intensive"
        }

        val habitualLines = trips
            .flatMap { it.linesUsed }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(HABITUAL_LINES_LIMIT)
            .map { it.key }

        return Profile(usageStatus = usageStatus, habitualLines = habitualLines)
    }
}
