package eu.dotshell.pelo.generic.data.alerts

import eu.dotshell.pelo.platform.PlatformContext
import eu.dotshell.pelo.platform.SecureStorage
import eu.dotshell.pelo.platform.randomId
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The daily-rotating identifier sent with a disruption report or a vote.
 *
 * Deliberately its own value rather than the telemetry `DailyIdProvider`'s, despite the identical
 * mechanics. Two reasons, both of which would be violated by sharing:
 *  - telemetry is opt-in and reporting is not, so an opted-out traveller would lose the ability to
 *    report at all, or would be silently re-identified after opting out;
 *  - the privacy notice promises the telemetry id is never cross-referenced with other processing.
 *    Sending it to the alerts endpoint is exactly such a cross-reference.
 *
 * Like its telemetry sibling it is a random value bound to a local calendar day, so it cannot be
 * traced back to the device and cannot follow anyone past midnight — long enough to hold a troll
 * accountable for a day, too short to build a history.
 */
class AlertDeviceIdProvider(context: PlatformContext) {

    private val storage = SecureStorage(context, PREFS_NAME)

    /** Returns today's id, minting a new one when the local day has changed. */
    fun currentOrRotate(
        now: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    ): String {
        val today = now.toString()
        val storedDay = storage.getString(KEY_DAY)
        val storedId = storage.getString(KEY_ID)

        if (storedDay == today && storedId != null) return storedId

        val newId = randomId()
        storage.putString(KEY_ID, newId)
        storage.putString(KEY_DAY, today)
        return newId
    }

    /** Forgets the current id. Used when wiping local state at the user's request. */
    fun clear() {
        storage.remove(KEY_ID)
        storage.remove(KEY_DAY)
    }

    companion object {
        private const val PREFS_NAME = "alerts_daily_id"
        private const val KEY_ID = "id"
        private const val KEY_DAY = "day"

        /** Header the backend reads it from. */
        const val HEADER = "X-Alert-Device-Id"
    }
}
