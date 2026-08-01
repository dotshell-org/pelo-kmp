package eu.dotshell.pelo.generic.service

/** What a stretch of the progress bar stands for. */
enum class NavigationSegmentKind {
    WALK,
    /** Standing at a stop between two legs — as much a part of the journey as the rest. */
    WAIT,
    RIDE,
}

/**
 * One stretch of the journey, long enough to draw. [seconds] is a duration, not a position: the
 * segments are consecutive and their sum is the journey's whole length, which is what lets a
 * renderer place progress on the same scale without being told the geometry twice.
 */
data class NavigationRouteSegment(
    val seconds: Int,
    val kind: NavigationSegmentKind,
    /** The line's own colour, ARGB. Null for anything but a [NavigationSegmentKind.RIDE]. */
    val colorArgb: Int?,
)

/**
 * The guidance, reduced to what fits on a lock screen, in the Dynamic Island or in a status bar
 * chip.
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
    /** Where the journey ends — the last leg's stop. */
    val destination: String,
    val segments: List<NavigationRouteSegment>,
    /** Offsets into the journey, in seconds, at which a line change happens. */
    val transferOffsetsSeconds: List<Int>,
    /** Elapsed along the journey, clamped to `0..totalSeconds`. */
    val progressSeconds: Int,
    /** Sum of [segments]. Zero for a journey with no measurable duration. */
    val totalSeconds: Int,
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
