package com.pelotcl.app.generic.data.telemetry

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks the user's opt-in decision for telemetry collection.
 *
 * Stored in a plain (non-encrypted) SharedPreferences: the value is a boolean preference,
 * not sensitive data. The daily_id and any in-flight events are kept in [DailyIdProvider]
 * (encrypted) and [TelemetryStorage] (filesDir) respectively.
 *
 * The schema_version_accepted lets us re-prompt the user if a future breaking change to
 * the payload schema introduces a new category of collected data (e.g., trip detection in
 * Vague 4) — protecting informed consent integrity.
 */
class OptInManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<OptInState> = _state.asStateFlow()

    val isOptedIn: Boolean
        get() = _state.value.optedIn

    val hasDecided: Boolean
        get() = _state.value.decidedAtEpochMs != null

    fun acceptCurrentSchema(currentSchemaVersion: Int) {
        update(
            OptInState(
                optedIn = true,
                decidedAtEpochMs = System.currentTimeMillis(),
                schemaVersionAccepted = currentSchemaVersion
            )
        )
    }

    fun decline() {
        update(
            OptInState(
                optedIn = false,
                decidedAtEpochMs = System.currentTimeMillis(),
                schemaVersionAccepted = null
            )
        )
    }

    /**
     * Returns true if the user previously opted in but the current schema version
     * exceeds what they consented to — meaning we must re-ask before collecting.
     */
    fun needsReConsent(currentSchemaVersion: Int): Boolean {
        val s = _state.value
        return s.optedIn && (s.schemaVersionAccepted ?: 0) < currentSchemaVersion
    }

    private fun load(): OptInState {
        return OptInState(
            optedIn = prefs.getBoolean(KEY_OPTED_IN, false),
            decidedAtEpochMs = prefs.getLong(KEY_DECIDED_AT, 0L).takeIf { it > 0 },
            schemaVersionAccepted = prefs.getInt(KEY_SCHEMA_VERSION, -1).takeIf { it >= 0 }
        )
    }

    private fun update(new: OptInState) {
        prefs.edit {
            putBoolean(KEY_OPTED_IN, new.optedIn)
            putLong(KEY_DECIDED_AT, new.decidedAtEpochMs ?: 0L)
            if (new.schemaVersionAccepted != null) {
                putInt(KEY_SCHEMA_VERSION, new.schemaVersionAccepted)
            } else {
                remove(KEY_SCHEMA_VERSION)
            }
        }
        _state.value = new
    }

    companion object {
        private const val PREFS_NAME = "telemetry_opt_in"
        private const val KEY_OPTED_IN = "opted_in"
        private const val KEY_DECIDED_AT = "decided_at"
        private const val KEY_SCHEMA_VERSION = "schema_version_accepted"
    }
}

data class OptInState(
    val optedIn: Boolean = false,
    val decidedAtEpochMs: Long? = null,
    val schemaVersionAccepted: Int? = null
)
