package eu.dotshell.pelo.generic.data.telemetry

import eu.dotshell.pelo.platform.PlatformContext
import eu.dotshell.pelo.platform.SecureStorage
import eu.dotshell.pelo.platform.randomId
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Provides the per-day pseudonymous identifier (daily_id) attached to every telemetry
 * message. The id is a random id — no derivation from any device identifier.
 *
 * Rotation: the id is bound to a "day" string (YYYY-MM-DD, local timezone). On every read,
 * if the stored day differs from today's local day, a new id is generated.
 *
 * Storage: [SecureStorage] (encrypted on Android via the Keystore, with a plain fallback).
 */
class DailyIdProvider(context: PlatformContext) {

    private val storage = SecureStorage(context, PREFS_NAME)

    /**
     * Returns the current daily id, rotating if the local day has changed since the last call.
     */
    fun currentOrRotate(
        now: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    ): Rotation {
        val today = now.toString()
        val storedDay = storage.getString(KEY_DAY)
        val storedId = storage.getString(KEY_ID)

        return if (storedDay == today && storedId != null) {
            Rotation(id = storedId, day = today, rotated = false)
        } else {
            val newId = randomId()
            storage.putString(KEY_ID, newId)
            storage.putString(KEY_DAY, today)
            storage.putLong(KEY_GENERATED_AT, Clock.System.now().toEpochMilliseconds())
            Rotation(id = newId, day = today, rotated = storedDay != null)
        }
    }

    /** Peek at the current id without triggering rotation. Null if none was ever generated. */
    fun peek(): String? = storage.getString(KEY_ID)

    fun peekDay(): String? = storage.getString(KEY_DAY)

    /** Forget the current id entirely — used when the user opts out and we wipe local state. */
    fun clear() {
        storage.remove(KEY_ID)
        storage.remove(KEY_DAY)
        storage.remove(KEY_GENERATED_AT)
    }

    data class Rotation(
        val id: String,
        val day: String,
        val rotated: Boolean
    )

    companion object {
        private const val PREFS_NAME = "telemetry_daily_id"
        private const val KEY_ID = "id"
        private const val KEY_DAY = "day"
        private const val KEY_GENERATED_AT = "generated_at"
    }
}
