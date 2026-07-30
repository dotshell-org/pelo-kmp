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
 * The instruction currently on screen, so the platform's ongoing notification can show it.
 * Activity and service share a process, so a plain flow is enough; the UI owns the wording
 * (it is the side that can resolve string resources for the active locale).
 */
object NavigationNotificationBridge {

    private val _instruction = MutableStateFlow<String?>(null)
    val instruction: StateFlow<String?> = _instruction

    fun setInstruction(text: String?) {
        _instruction.value = text
    }
}
