package com.pelotcl.app.generic.data.telemetry

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Provides the per-day pseudonymous identifier ([daily_id]) attached to every telemetry
 * message. The id is a UUID v4 — no derivation from any device identifier, no fingerprint.
 *
 * Rotation rules:
 * - The id is bound to a "day" string (YYYY-MM-DD in the user's local timezone).
 * - On every read, if the stored day differs from today's local day, a new id is generated.
 *
 * Storage: [EncryptedSharedPreferences] backed by the Android Keystore. Encryption here is
 * defense-in-depth — the daily_id itself is not strictly sensitive (it expires every 24h),
 * but on a rooted/lost device we don't want to leak past ids that could be cross-referenced
 * with backend data.
 *
 * If EncryptedSharedPreferences initialization fails (rare, but possible on some OEM ROMs
 * with broken Keystore implementations), we fall back to plain SharedPreferences with a
 * warning log — telemetry collection continues, the user is not blocked.
 */
class DailyIdProvider(private val context: Context) {

    private val prefs: SharedPreferences = buildPrefs(context)

    /**
     * Returns the current daily id, rotating if the local day has changed since the last call.
     * Returns a [Rotation.Result] indicating whether the caller should treat this as a new day.
     */
    fun currentOrRotate(now: LocalDate = LocalDate.now(ZoneId.systemDefault())): Rotation {
        val today = now.format(DAY_FORMATTER)
        val storedDay = prefs.getString(KEY_DAY, null)
        val storedId = prefs.getString(KEY_ID, null)

        return if (storedDay == today && storedId != null) {
            Rotation(id = storedId, day = today, rotated = false)
        } else {
            val newId = UUID.randomUUID().toString()
            prefs.edit()
                .putString(KEY_ID, newId)
                .putString(KEY_DAY, today)
                .putLong(KEY_GENERATED_AT, System.currentTimeMillis())
                .apply()
            Rotation(id = newId, day = today, rotated = storedDay != null)
        }
    }

    /**
     * Peek at the current id without triggering rotation. Returns null if no id has ever been
     * generated. Used by background workers that should not implicitly start a new day.
     */
    fun peek(): String? = prefs.getString(KEY_ID, null)

    fun peekDay(): String? = prefs.getString(KEY_DAY, null)

    /**
     * Forget the current id entirely — used when the user opts out and we wipe local state.
     */
    fun clear() {
        prefs.edit()
            .remove(KEY_ID)
            .remove(KEY_DAY)
            .remove(KEY_GENERATED_AT)
            .apply()
    }

    /**
     * Result of a [currentOrRotate] call. [rotated] is true when a brand-new id was generated
     * because the local day changed — the caller is responsible for finalizing the previous
     * day's report before continuing.
     */
    data class Rotation(
        val id: String,
        val day: String,
        val rotated: Boolean
    )

    private fun buildPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w(
                TAG,
                "EncryptedSharedPreferences unavailable, falling back to plain prefs", e
            )
            context.getSharedPreferences(PREFS_NAME_FALLBACK, Context.MODE_PRIVATE)
        }
    }

    companion object {
        private const val TAG = "DailyIdProvider"
        private const val PREFS_NAME = "telemetry_daily_id"
        private const val PREFS_NAME_FALLBACK = "telemetry_daily_id_plain"
        private const val KEY_ID = "id"
        private const val KEY_DAY = "day"
        private const val KEY_GENERATED_AT = "generated_at"

        private val DAY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}
