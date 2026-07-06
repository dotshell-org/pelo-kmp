package eu.dotshell.massilia.generic.data.consent

import eu.dotshell.massilia.platform.PlatformContext
import eu.dotshell.massilia.platform.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock

class TermsConsentManager(context: PlatformContext) {

    private val settings = Settings(context, PREFS_NAME)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<TermsConsentState> = _state.asStateFlow()

    val isAccepted: Boolean
        get() = _state.value.accepted

    fun accept(version: Int) {
        update(
            TermsConsentState(
                accepted = true,
                decidedAtEpochMs = Clock.System.now().toEpochMilliseconds(),
                versionAccepted = version
            )
        )
    }

    fun decline() {
        update(
            TermsConsentState(
                accepted = false,
                decidedAtEpochMs = Clock.System.now().toEpochMilliseconds(),
                versionAccepted = null
            )
        )
    }

    private fun load(): TermsConsentState {
        return TermsConsentState(
            accepted = settings.getBoolean(KEY_ACCEPTED, false),
            decidedAtEpochMs = settings.getLong(KEY_DECIDED_AT, 0L).takeIf { it > 0 },
            versionAccepted = settings.getInt(KEY_VERSION, -1).takeIf { it >= 0 }
        )
    }

    private fun update(new: TermsConsentState) {
        settings.putBoolean(KEY_ACCEPTED, new.accepted)
        settings.putLong(KEY_DECIDED_AT, new.decidedAtEpochMs ?: 0L)
        if (new.versionAccepted != null) {
            settings.putInt(KEY_VERSION, new.versionAccepted)
        } else {
            settings.remove(KEY_VERSION)
        }
        _state.value = new
    }

    companion object {
        private const val PREFS_NAME = "terms_consent"
        private const val KEY_ACCEPTED = "accepted"
        private const val KEY_DECIDED_AT = "decided_at"
        private const val KEY_VERSION = "version_accepted"
    }
}

data class TermsConsentState(
    val accepted: Boolean = false,
    val decidedAtEpochMs: Long? = null,
    val versionAccepted: Int? = null
)
