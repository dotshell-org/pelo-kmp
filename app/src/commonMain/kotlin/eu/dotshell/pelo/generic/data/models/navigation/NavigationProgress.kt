package eu.dotshell.pelo.generic.data.models.navigation

/**
 * How far along a planned journey the traveller has got.
 *
 * [legIndex] indexes `JourneyResult.legs` directly — walking legs included — so the guidance can
 * talk about the walk to the platform, not just the ride. [stopIndex] walks the current leg's stop
 * chain: 0 is its origin, [stopCount] - 1 its terminus.
 *
 * Progress only ever moves forward; see `NavigationProgressTracker`.
 */
data class NavigationProgress(
    val legIndex: Int = 0,
    val stopIndex: Int = 0,
    val stopCount: Int = 1,
    val isArrived: Boolean = false,
    /** Metres to the next point of the chain, when a usable fix snapped onto the route. */
    val distanceToNextMeters: Int? = null,
    /** The last fix was too far from the planned route to be believed. */
    val isOffRoute: Boolean = false,
    /** No usable fix right now — progress is being driven by the timetable alone. */
    val isDeadReckoning: Boolean = false,
) {
    /** Stops still to go before the current leg's terminus. */
    val remainingStopsOnLeg: Int
        get() = (stopCount - 1 - stopIndex).coerceAtLeast(0)

    val isAtLegTerminus: Boolean
        get() = stopIndex >= stopCount - 1
}
