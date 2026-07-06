package eu.dotshell.massilia.generic.utils.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bridges a platform-side location-permission grant to the Compose UI.
 *
 * The runtime permission is requested by platform code (e.g. the Android [android.app.Activity]),
 * whose result isn't visible to common Compose code. Platforms push the current grant state here
 * so the location subscription can restart the moment the user grants the permission — no app
 * restart required.
 */
object LocationPermissionSignal {

    private val _granted = MutableStateFlow(false)

    /** Emits `true` once the app holds location permission. */
    val granted: StateFlow<Boolean> = _granted.asStateFlow()

    /** Called by platform code after the permission state is (re)evaluated. */
    fun setGranted(granted: Boolean) {
        _granted.value = granted
    }
}
