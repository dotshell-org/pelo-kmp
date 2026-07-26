package eu.dotshell.pelo.generic.service

/**
 * No-op on Android: NavigationModeForegroundService's ongoing notification already carries the
 * live instruction and a stop action. A second surface would only compete with it.
 */
actual object NavigationLiveActivity {
    actual fun start(state: NavigationLiveActivityState) = Unit
    actual fun update(state: NavigationLiveActivityState) = Unit
    actual fun end() = Unit
}
