package eu.dotshell.pelo.generic.data.dataset

import kotlinx.datetime.LocalDate

/**
 * How the bundled timetables stand relative to today.
 *
 * The operator (TCL) republishes daily on a rolling ~120-day window, so a build is
 * never suddenly invalid — it drifts. [EXPIRING_SOON] is therefore the state that
 * matters in practice: it fires while the app still works, which is exactly when
 * there is still time to ship fresh data.
 */
enum class DataFreshness {
    /** End date comfortably ahead. */
    VALID,

    /** Still valid, but the window closes within [DatasetFreshness.SOON_THRESHOLD_DAYS]. */
    EXPIRING_SOON,

    /** Past the end date: schedules may now be wrong, silently. */
    EXPIRED,

    /** No usable end date — nothing honest to say. */
    UNKNOWN
}

object DatasetFreshness {

    /** Roughly a release cycle of head start before the data goes stale. */
    const val SOON_THRESHOLD_DAYS = 30

    /**
     * Classifies [endDate] against [today].
     *
     * The boundary is inclusive on purpose: a dataset whose last valid day IS today
     * is still [VALID]-or-[EXPIRING_SOON], not [EXPIRED] — service on the final day
     * is covered by the feed.
     */
    fun classify(
        endDate: LocalDate?,
        today: LocalDate,
        soonThresholdDays: Int = SOON_THRESHOLD_DAYS
    ): DataFreshness {
        if (endDate == null) return DataFreshness.UNKNOWN
        val remaining = daysBetween(today, endDate)
        return when {
            remaining < 0 -> DataFreshness.EXPIRED
            remaining <= soonThresholdDays -> DataFreshness.EXPIRING_SOON
            else -> DataFreshness.VALID
        }
    }

    /** Parses an ISO `yyyy-MM-dd` date, returning null on anything unusable. */
    fun parseIsoDate(value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null
        return try {
            LocalDate.parse(value)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Formats [date] as "17 novembre 2026" using caller-supplied month names, so the
     * wording follows the app locale instead of being hardcoded to French.
     * Falls back to `dd/MM/yyyy` when the month list is not the expected 12 entries.
     */
    fun formatLongDate(date: LocalDate, monthNames: List<String>): String {
        if (monthNames.size != 12) {
            return "${pad2(date.dayOfMonth)}/${pad2(date.monthNumber)}/${date.year}"
        }
        return "${date.dayOfMonth} ${monthNames[date.monthNumber - 1]} ${date.year}"
    }

    private fun pad2(value: Int): String = value.toString().padStart(2, '0')

    private fun daysBetween(from: LocalDate, to: LocalDate): Int =
        to.toEpochDays() - from.toEpochDays()
}
