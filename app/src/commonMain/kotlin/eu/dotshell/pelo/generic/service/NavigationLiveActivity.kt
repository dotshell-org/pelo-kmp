package eu.dotshell.pelo.generic.service

/**
 * The guidance, reduced to what fits on a lock screen or in the Dynamic Island.
 *
 * Deliberately flat and made of primitives: it crosses into Swift, where a Kotlin sealed hierarchy
 * would be awkward to pattern-match, and it is serialised into a Live Activity's content state.
 */
data class NavigationLiveActivityState(
    val instruction: String,
    val lineName: String?,
    val remainingMinutes: Int,
    val arrivalTimeText: String,
    val isArrived: Boolean,
)

/**
 * A glanceable, always-on rendering of the session outside the app.
 *
 * Android already has one — the foreground service's ongoing notification — so this exists for
 * iOS, where nothing survived leaving the app. The common code drives it either way; the Android
 * side is a no-op rather than a second, competing notification.
 */
expect object NavigationLiveActivity {

    /** Begin showing the session. Ignored if one is already showing. */
    fun start(state: NavigationLiveActivityState)

    /** Push a new state onto the running activity. */
    fun update(state: NavigationLiveActivityState)

    /** Take it down. Safe to call when nothing is showing. */
    fun end()
}
