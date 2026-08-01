package eu.dotshell.pelo.generic.ui.screens.plan

import androidx.compose.runtime.Immutable
import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.JourneyLeg
import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.JourneyResult
import eu.dotshell.pelo.generic.service.NavigationLiveActivityState
import eu.dotshell.pelo.generic.service.NavigationRouteSegment
import eu.dotshell.pelo.generic.service.NavigationSegmentKind
import eu.dotshell.pelo.generic.service.NavigationSession
import eu.dotshell.pelo.generic.utils.LineColorHelper

private const val DAY_SECONDS = 24 * 3600

/**
 * How long the traveller must be continuously off-route before replanning is offered.
 *
 * Long enough that a bad fix, a tunnel mouth or a brief detour around a building does not prompt
 * anything; short enough that someone who boarded the wrong vehicle is not carried away in silence.
 */
private const val REROUTE_PROMPT_AFTER_SECONDS = 25

/**
 * What the guidance is telling the traveller to do, as data rather than prose. Keeping the
 * wording out of here is what lets the overlay translate it — the previous version hard-coded
 * French sentences into the state, so an English device got a half-translated screen.
 */
@Immutable
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

/**
 * @Immutable so the overlay can skip. Every property is a val; the type was inferred unstable only
 * through its four JourneyLeg fields and the instruction. Note that guidance republishes a whole
 * new instance every second — which is exactly the contract: a new value, not a mutated one.
 */
@Immutable
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
    val isArrived: Boolean,
    val isOffRoute: Boolean,
    /**
     * Off-route long enough that the plan is probably wrong, so replanning is worth offering.
     * Offered rather than applied: a transit reroute can mean entirely different lines, and the
     * traveller may simply have stepped away on purpose.
     */
    val canReroute: Boolean,
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
        isArrived = progress.isArrived,
        isOffRoute = progress.isOffRoute,
        canReroute = progress.offRouteSeconds >= REROUTE_PROMPT_AFTER_SECONDS && !progress.isArrived,
        isDeadReckoning = progress.isDeadReckoning,
    )
}

/**
 * The same session reduced to what a glanceable surface shows: the journey drawn as consecutive
 * segments, how far along them the traveller is, and the sentence to read.
 *
 * Pure, like [buildNavigationModeUiState], and for the same reason — it runs in a foreground
 * service and in a Live Activity push, neither of which can reach a composition. That is why
 * [instructionText] arrives already resolved and [lineColor] is injected: the default reaches into
 * the loaded app config, which a unit test has no business needing.
 */
fun buildNavigationLiveActivityState(
    ui: NavigationModeUiState,
    journey: JourneyResult,
    instructionText: String,
    lineColor: (String) -> Int? = { LineColorHelper.getColorForLineString(it) },
): NavigationLiveActivityState {
    val reference = journey.departureTime
    val segments = mutableListOf<NavigationRouteSegment>()
    val transferOffsets = mutableListOf<Int>()

    // The last ride, so a change can be told apart from simply getting off at the end.
    val lastRideIndex = journey.legs.indexOfLast { !it.isWalking }

    var cursor = reference
    var offset = 0

    journey.legs.forEachIndexed { index, leg ->
        val departure = normalizeTimeAroundReference(leg.departureTime, reference)
        val arrival = normalizeTimeAroundReference(leg.arrivalTime, reference)

        // Standing at a stop waiting for a departure is time spent on this journey. Leaving it out
        // would make the segments sum to less than the trip, and every renderer would then place
        // the tracker ahead of where the traveller actually is.
        val wait = (departure - cursor).coerceAtLeast(0)
        if (wait > 0) {
            segments += NavigationRouteSegment(wait, NavigationSegmentKind.WAIT, null)
            offset += wait
        }

        val duration = (arrival - departure).coerceAtLeast(0)
        segments += NavigationRouteSegment(
            seconds = duration,
            kind = if (leg.isWalking) NavigationSegmentKind.WALK else NavigationSegmentKind.RIDE,
            colorArgb = if (leg.isWalking) null else leg.routeName?.let(lineColor),
        )
        offset += duration
        cursor = departure + duration

        // Marked where the traveller gets off, not where they board again: alighting is the
        // moment that needs acting on, and the two are a walk and a wait apart.
        if (!leg.isWalking && index < lastRideIndex) transferOffsets += offset
    }

    // Only reachable on inconsistent data, where the journey outlasts its own last leg. Kept so
    // the segments still sum to the trip, which is what makes the progress below exact.
    val tail = (normalizeTimeAroundReference(journey.arrivalTime, reference) - cursor)
        .coerceAtLeast(0)
    if (tail > 0) {
        segments += NavigationRouteSegment(tail, NavigationSegmentKind.WAIT, null)
        offset += tail
    }

    return NavigationLiveActivityState(
        instruction = instructionText,
        lineName = ui.displayedLeg?.routeName,
        remainingMinutes = (ui.remainingSeconds + 59) / 60,
        arrivalTimeText = ui.arrivalTimeText,
        isArrived = ui.isArrived,
        destination = journey.legs.lastOrNull()?.toStopName.orEmpty(),
        segments = segments,
        transferOffsetsSeconds = transferOffsets,
        // Derived from the remaining time rather than the clock, so this stays free of one: a
        // journey started before its departure clamps to zero, an arrival clamps to the end.
        progressSeconds = (offset - ui.remainingSeconds).coerceIn(0, offset),
        totalSeconds = offset,
    )
}

/**
 * Everything a glanceable surface would actually notice changing, and nothing else.
 *
 * The session republishes once a second so its countdown counts down. Reposting a notification or
 * pushing a Live Activity at that rate would be waste the platform eventually starts throttling —
 * and none of it would be visible: the minute is the finest thing on show, apart from the bar,
 * which is quantised here to a percent of the journey.
 */
data class NavigationGlanceKey(
    val instruction: NavigationInstruction,
    val lineName: String?,
    val remainingMinutes: Int,
    val isArrived: Boolean,
    val progressPercent: Int,
)

fun navigationGlanceKey(ui: NavigationModeUiState, journey: JourneyResult): NavigationGlanceKey {
    val total = journeyDurationSeconds(journey)
    return NavigationGlanceKey(
        instruction = ui.instruction,
        lineName = ui.displayedLeg?.routeName,
        remainingMinutes = (ui.remainingSeconds + 59) / 60,
        isArrived = ui.isArrived,
        progressPercent = if (total <= 0) {
            0
        } else {
            (total - ui.remainingSeconds).coerceIn(0, total) * 100 / total
        },
    )
}

/** The journey's own length in seconds, wrap-safe across midnight. Never negative. */
fun journeyDurationSeconds(journey: JourneyResult): Int =
    (normalizeTimeAroundReference(journey.arrivalTime, journey.departureTime) - journey.departureTime)
        .coerceAtLeast(0)

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
