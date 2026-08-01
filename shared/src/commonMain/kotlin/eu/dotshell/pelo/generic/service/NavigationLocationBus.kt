package eu.dotshell.pelo.generic.service

import eu.dotshell.pelo.generic.utils.location.GeoPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Fixes produced by a platform background location stream — on Android, the navigation foreground
 * service.
 *
 * Without this the service's stream fed telemetry and nothing else: guidance ran off the UI's own
 * stream, which the system throttles once the app is backgrounded, so the session froze the moment
 * the traveller put their phone away and only caught up when they took it out again.
 */
object NavigationLocationBus {

    private val _fixes = MutableSharedFlow<GeoPoint>(replay = 1, extraBufferCapacity = 16)
    val fixes: SharedFlow<GeoPoint> = _fixes

    fun publish(point: GeoPoint) {
        _fixes.tryEmit(point)
    }
}

/**
 * The session as a glanceable surface shows it, for the platform code that renders it outside the
 * app — on Android, the navigation foreground service's ongoing notification.
 *
 * Published by [NavigationModeController] from its own scope, not from the composition. That is
 * the whole point: an Activity that is not on screen stops recomposing, and this is precisely the
 * state that has to keep moving once the traveller has put their phone away.
 *
 * Activity and service share a process, so a plain flow is enough. Null means no session.
 */
object NavigationGlanceBridge {

    private val _state = MutableStateFlow<NavigationLiveActivityState?>(null)
    val state: StateFlow<NavigationLiveActivityState?> = _state

    fun publish(state: NavigationLiveActivityState?) {
        _state.value = state
    }
}
