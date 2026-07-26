package eu.dotshell.pelo.generic.ui.screens.plan

import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.JourneyLeg
import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.JourneyResult
import eu.dotshell.pelo.generic.service.NavigationSession

private const val DAY_SECONDS = 24 * 3600

/**
 * What the guidance is telling the traveller to do, as data rather than prose. Keeping the
 * wording out of here is what lets the overlay translate it — the previous version hard-coded
 * French sentences into the state, so an English device got a half-translated screen.
 */
sealed interface NavigationInstruction {

    /** Walk to [stopName]; [distanceMeters] is null until a fix says how far it is. */
    data class WalkTo(val stopName: String, val distanceMeters: Int?) : NavigationInstruction

    /** Wait at [stopName] for a departure [secondsUntilDeparture] away. */
    data class BoardAt(val stopName: String, val secondsUntilDeparture: Int) : NavigationInstruction

    /** Stay aboard until [stopName], [remainingStops] stops away. */
    data class RideTo(
        val stopName: String,
        val remainingStops: Int,
        val changesLine: Boolean,
    ) : NavigationInstruction

    data object Arrived : NavigationInstruction

    /** Navigation is running but no fix has arrived yet. */
    data object AcquiringSignal : NavigationInstruction

    /** The journey has no leg we can describe (empty or malformed). */
    data object InProgress : NavigationInstruction
}

data class NavigationModeUiState(
    val currentLeg: JourneyLeg?,
    /** The leg whose line badge is shown — the ride in progress, or the one being walked to. */
    val displayedLeg: JourneyLeg?,
    /** The line being left behind, when a change is imminent. */
    val previousLeg: JourneyLeg?,
    /** The ride after [displayedLeg], previewed in the "up next" strip. */
    val upcomingLeg: JourneyLeg?,
    val shouldChangeLine: Boolean,
    val instruction: NavigationInstruction,
    /** Headsign of [displayedLeg]; null when the dataset does not provide one. */
    val direction: String?,
    val remainingSeconds: Int,
    val arrivalTimeText: String,
    /** 0f at departure, 1f at arrival — drives the progress bar. */
    val progressFraction: Float,
    val isArrived: Boolean,
    val isOffRoute: Boolean,
    /** Guidance is running on the timetable because no fresh fix is available. */
    val isDeadReckoning: Boolean,
)

/**
 * Derive everything the overlay renders from the live [session]. Pure — no clock read, no
 * resource lookup — so it is directly unit-testable.
 */
fun buildNavigationModeUiState(session: NavigationSession): NavigationModeUiState? {
    val journey = session.journey ?: return null
    val progress = session.progress
    val now = session.nowSeconds
    val reference = journey.departureTime

    val remainingSeconds = computeRemainingJourneySeconds(journey, now)
    val arrivalText = journey.formatArrivalTime()
    val fraction = computeProgressFraction(journey, now)

    val currentLeg = journey.legs.getOrNull(progress.legIndex)

    // The ride the badge normally shows: the one under way, or — mid-walk — the one being walked
    // to, so the traveller sees which line they are heading for instead of a blank badge.
    val ridingIndex = if (currentLeg != null && !currentLeg.isWalking) {
        progress.legIndex
    } else {
        journey.legs.indexOfFirstFrom(progress.legIndex + 1) { !it.isWalking }
    }
    val ridingLeg = journey.legs.getOrNull(ridingIndex)
    val nextRideIndex = if (ridingIndex < 0) {
        -1
    } else {
        journey.legs.indexOfFirstFrom(ridingIndex + 1) { !it.isWalking }
    }
    val nextRideLeg = journey.legs.getOrNull(nextRideIndex)

    // Pulling into the stop where the line changes: promote the incoming line to the main badge
    // and keep the outgoing one above it, so the card reads "leave this, take that".
    val isChangingLine = currentLeg != null &&
            !currentLeg.isWalking &&
            progress.isAtLegTerminus &&
            nextRideLeg != null

    val displayedLeg = if (isChangingLine) nextRideLeg else ridingLeg
    val previousLeg = if (isChangingLine) ridingLeg else null
    val upcomingIndex = if (isChangingLine) {
        journey.legs.indexOfFirstFrom(nextRideIndex + 1) { !it.isWalking }
    } else {
        nextRideIndex
    }
    val upcomingLeg = journey.legs.getOrNull(upcomingIndex)

    val instruction = when {
        progress.isArrived -> NavigationInstruction.Arrived
        currentLeg == null -> NavigationInstruction.InProgress
        currentLeg.isWalking -> NavigationInstruction.WalkTo(
            stopName = currentLeg.toStopName,
            distanceMeters = progress.distanceToNextMeters,
        )
        else -> {
            val departure = normalizeTimeAroundReference(currentLeg.departureTime, reference)
            val nowNormalized = normalizeTimeAroundReference(now, reference)
            if (nowNormalized < departure && progress.stopIndex == 0) {
                NavigationInstruction.BoardAt(
                    stopName = currentLeg.fromStopName,
                    secondsUntilDeparture = departure - nowNormalized,
                )
            } else {
                NavigationInstruction.RideTo(
                    stopName = currentLeg.toStopName,
                    remainingStops = progress.remainingStopsOnLeg,
                    changesLine = isChangingLine,
                )
            }
        }
    }

    return NavigationModeUiState(
        currentLeg = currentLeg,
        displayedLeg = displayedLeg,
        previousLeg = previousLeg,
        upcomingLeg = upcomingLeg,
        shouldChangeLine = isChangingLine && previousLeg != null && displayedLeg != null,
        instruction = if (!session.hasFreshFix && session.location == null && !progress.isArrived) {
            NavigationInstruction.AcquiringSignal
        } else {
            instruction
        },
        direction = displayedLeg?.direction?.takeIf { it.isNotBlank() },
        remainingSeconds = remainingSeconds,
        arrivalTimeText = arrivalText,
        progressFraction = fraction,
        isArrived = progress.isArrived,
        isOffRoute = progress.isOffRoute,
        isDeadReckoning = progress.isDeadReckoning,
    )
}

/**
 * Index-based rather than value-based lookup: a journey can hold two structurally equal legs
 * (a there-and-back loop), and `indexOf` would collapse them onto the first one.
 */
private inline fun List<JourneyLeg>.indexOfFirstFrom(
    startIndex: Int,
    predicate: (JourneyLeg) -> Boolean,
): Int {
    for (index in startIndex.coerceAtLeast(0) until size) {
        if (predicate(this[index])) return index
    }
    return -1
}

/** Seconds from [nowSeconds] to arrival, wrap-safe across midnight. Never negative. */
fun computeRemainingJourneySeconds(journey: JourneyResult, nowSeconds: Int): Int {
    val reference = journey.departureTime
    val nowNormalized = normalizeTimeAroundReference(nowSeconds, reference)
    val arrivalNormalized = normalizeTimeAroundReference(journey.arrivalTime, reference)
    return (arrivalNormalized - nowNormalized).coerceAtLeast(0)
}

private fun computeProgressFraction(journey: JourneyResult, nowSeconds: Int): Float {
    val reference = journey.departureTime
    val nowNormalized = normalizeTimeAroundReference(nowSeconds, reference)
    val arrivalNormalized = normalizeTimeAroundReference(journey.arrivalTime, reference)
    val total = arrivalNormalized - reference
    if (total <= 0) return 0f
    val elapsed = nowNormalized - reference
    return (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

/**
 * Pull [timeSeconds] into the same service day as [referenceSeconds]. GTFS times run past
 * midnight ("25:30" is 01:30) while the wall clock wraps at it, so the two are only comparable
 * once both sit on the same side of the seam.
 */
private fun normalizeTimeAroundReference(timeSeconds: Int, referenceSeconds: Int): Int {
    var normalized = timeSeconds
    while (normalized < referenceSeconds - DAY_SECONDS / 2) normalized += DAY_SECONDS
    while (normalized > referenceSeconds + DAY_SECONDS / 2) normalized -= DAY_SECONDS
    return normalized
}
