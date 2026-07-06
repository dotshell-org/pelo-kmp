package eu.dotshell.massilia.generic.data.telemetry

import eu.dotshell.massilia.platform.PlatformContext
import eu.dotshell.massilia.platform.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock

/**
 * Tracks the user's opt-in decision for telemetry collection.
 *
 * Stored via the plain (non-encrypted) [Settings] abstraction: the value is a boolean
 * preference, not sensitive data. The daily_id and any in-flight events are kept in
 * [DailyIdProvider] (encrypted) and [TelemetryStorage] (files) respectively.
 *
 * The schema_version_accepted lets us re-prompt the user if a future breaking change to
 * the payload schema introduces a new category of collected data — protecting informed
 * consent integrity.
 */
class OptInManager(context: PlatformContext) {

    private val settings = Settings(context, PREFS_NAME)
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
                decidedAtEpochMs = Clock.System.now().toEpochMilliseconds(),
                schemaVersionAccepted = currentSchemaVersion
            )
        )
    }

    fun decline() {
        update(
            OptInState(
                optedIn = false,
                decidedAtEpochMs = Clock.System.now().toEpochMilliseconds(),
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
            optedIn = settings.getBoolean(KEY_OPTED_IN, false),
            decidedAtEpochMs = settings.getLong(KEY_DECIDED_AT, 0L).takeIf { it > 0 },
            schemaVersionAccepted = settings.getInt(KEY_SCHEMA_VERSION, -1).takeIf { it >= 0 }
        )
    }

    private fun update(new: OptInState) {
        settings.putBoolean(KEY_OPTED_IN, new.optedIn)
        settings.putLong(KEY_DECIDED_AT, new.decidedAtEpochMs ?: 0L)
        if (new.schemaVersionAccepted != null) {
            settings.putInt(KEY_SCHEMA_VERSION, new.schemaVersionAccepted)
        } else {
            settings.remove(KEY_SCHEMA_VERSION)
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
