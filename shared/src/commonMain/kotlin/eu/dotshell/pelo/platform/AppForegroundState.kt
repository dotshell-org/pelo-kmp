package eu.dotshell.pelo.platform

import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the app is in front of the user, app-wide rather than per-screen.
 *
 * Exists so long-lived streams can stand down while nobody is looking at them. The vehicle
 * positions feed is the motivating case: it is a server-sent event stream that pushes the whole
 * fleet, and it used to stay open for as long as the process lived.
 *
 * Starts as `true`. On both platforms the signal only arrives on a transition, so assuming the
 * foreground until told otherwise keeps a stream running that is already running, rather than
 * silently withholding one that should be live.
 */
expect object AppForegroundState {

    val isForeground: StateFlow<Boolean>

    /**
     * Begins observing. Idempotent — later calls do nothing — so callers need not coordinate.
     */
    fun start(context: PlatformContext)
}
