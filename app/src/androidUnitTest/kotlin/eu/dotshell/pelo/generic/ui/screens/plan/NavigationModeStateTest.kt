package eu.dotshell.pelo.generic.ui.screens.plan

import eu.dotshell.pelo.generic.data.models.navigation.NavigationProgress
import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.IntermediateStop
import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.JourneyLeg
import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.JourneyResult
import eu.dotshell.pelo.generic.service.NavigationSession
import eu.dotshell.pelo.generic.utils.location.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins how a session becomes an instruction: the countdown that used to freeze at the trip length
 * while waiting for a bus, the walking legs the guidance used to drop, and the line-change badge.
 */
class NavigationModeStateTest {

    private fun ride(
        from: String,
        to: String,
        routeName: String?,
        departure: Int,
        arrival: Int,
        walking: Boolean = false,
        stops: List<IntermediateStop> = emptyList(),
    ) = JourneyLeg(
        fromStopId = from,
        fromStopName = from,
        fromLat = 45.75,
        fromLon = 4.85,
        toStopId = to,
        toStopName = to,
        toLat = 45.76,
        toLon = 4.86,
        departureTime = departure,
        arrivalTime = arrival,
        routeName = routeName,
        routeColor = null,
        isWalking = walking,
        direction = if (walking) null else "Debourg",
        intermediateStops = stops,
    )

    private val journey = JourneyResult(
        departureTime = 8 * 3600,
        arrivalTime = 8 * 3600 + 30 * 60,
        legs = listOf(
            ride("Home", "Bellecour", null, 8 * 3600, 8 * 3600 + 5 * 60, walking = true),
            ride(
                "Bellecour", "Saxe", "T1", 8 * 3600 + 8 * 60, 8 * 3600 + 18 * 60,
                stops = listOf(IntermediateStop("Guillotière", 8 * 3600 + 13 * 60, 45.755, 4.842)),
            ),
            ride("Saxe", "Part-Dieu", "B", 8 * 3600 + 20 * 60, 8 * 3600 + 30 * 60),
        ),
    )

    private fun sessionAt(
        now: Int,
        progress: NavigationProgress,
        hasFreshFix: Boolean = true,
    ) = NavigationSession(
        isActive = true,
        journey = journey,
        progress = progress,
        location = GeoPoint(45.75, 4.85),
        hasFreshFix = hasFreshFix,
        nowSeconds = now,
    )

    @Test
    fun `remaining time counts down to arrival, not the trip length`() {
        // 07:55, ten minutes before the journey even departs. The old formatter fell back on the
        // full trip duration here (30 min) and stayed there until departure.
        val state = buildNavigationModeUiState(
            sessionAt(7 * 3600 + 55 * 60, NavigationProgress(legIndex = 0, stopIndex = 0, stopCount = 2))
        )!!

        assertEquals(35 * 60, state.remainingSeconds)
    }

    @Test
    fun `remaining time never goes negative`() {
        val state = buildNavigationModeUiState(
            sessionAt(9 * 3600, NavigationProgress(legIndex = 2, stopIndex = 1, stopCount = 2))
        )!!
        assertEquals(0, state.remainingSeconds)
    }

    @Test
    fun `a walking leg produces a walk instruction, not a blank line badge`() {
        val state = buildNavigationModeUiState(
            sessionAt(
                8 * 3600 + 60,
                NavigationProgress(legIndex = 0, stopIndex = 0, stopCount = 2, distanceToNextMeters = 320)
            )
        )!!

        val instruction = state.instruction
        assertTrue(instruction is NavigationInstruction.WalkTo)
        assertEquals("Bellecour", (instruction as NavigationInstruction.WalkTo).stopName)
        assertEquals(320, instruction.distanceMeters)
        // The badge shows the line being walked to, so the step is not an anonymous grey disc.
        assertEquals("T1", state.displayedLeg?.routeName)
    }

    @Test
    fun `waiting for the vehicle asks the traveller to board`() {
        val state = buildNavigationModeUiState(
            sessionAt(8 * 3600 + 6 * 60, NavigationProgress(legIndex = 1, stopIndex = 0, stopCount = 3))
        )!!

        val instruction = state.instruction
        assertTrue(instruction is NavigationInstruction.BoardAt)
        assertEquals(2 * 60, (instruction as NavigationInstruction.BoardAt).secondsUntilDeparture)
    }

    @Test
    fun `riding reports the stops left before getting off`() {
        val state = buildNavigationModeUiState(
            sessionAt(8 * 3600 + 10 * 60, NavigationProgress(legIndex = 1, stopIndex = 0, stopCount = 3))
        )!!

        val instruction = state.instruction as NavigationInstruction.RideTo
        assertEquals("Saxe", instruction.stopName)
        assertEquals(2, instruction.remainingStops)
        assertFalse(instruction.changesLine)
    }

    @Test
    fun `reaching a leg terminus with another ride ahead flags the line change`() {
        val state = buildNavigationModeUiState(
            sessionAt(8 * 3600 + 18 * 60, NavigationProgress(legIndex = 1, stopIndex = 2, stopCount = 3))
        )!!

        assertTrue(state.shouldChangeLine)
        assertEquals("T1", state.previousLeg?.routeName)
        assertEquals("B", state.displayedLeg?.routeName)
        assertTrue((state.instruction as NavigationInstruction.RideTo).changesLine)
    }

    @Test
    fun `the last leg never flags a line change`() {
        val state = buildNavigationModeUiState(
            sessionAt(8 * 3600 + 29 * 60, NavigationProgress(legIndex = 2, stopIndex = 1, stopCount = 2))
        )!!

        assertFalse(state.shouldChangeLine)
        assertNull(state.upcomingLeg)
    }

    @Test
    fun `arrival wins over every other instruction`() {
        val state = buildNavigationModeUiState(
            sessionAt(
                8 * 3600 + 30 * 60,
                NavigationProgress(legIndex = 2, stopIndex = 1, stopCount = 2, isArrived = true)
            )
        )!!

        assertTrue(state.isArrived)
        assertEquals(NavigationInstruction.Arrived, state.instruction)
    }

    @Test
    fun `no fix yet reads as acquiring signal rather than a stale first step`() {
        val session = NavigationSession(
            isActive = true,
            journey = journey,
            progress = NavigationProgress(legIndex = 0, stopIndex = 0, stopCount = 2),
            location = null,
            hasFreshFix = false,
            nowSeconds = 8 * 3600,
        )

        val state = buildNavigationModeUiState(session)!!
        assertEquals(NavigationInstruction.AcquiringSignal, state.instruction)
    }

    @Test
    fun `progress fraction spans departure to arrival`() {
        val progress = NavigationProgress(legIndex = 1, stopIndex = 1, stopCount = 3)
        assertEquals(0f, buildNavigationModeUiState(sessionAt(8 * 3600, progress))!!.progressFraction, 0.001f)
        assertEquals(
            0.5f,
            buildNavigationModeUiState(sessionAt(8 * 3600 + 15 * 60, progress))!!.progressFraction,
            0.001f,
        )
        assertEquals(
            1f,
            buildNavigationModeUiState(sessionAt(8 * 3600 + 45 * 60, progress))!!.progressFraction,
            0.001f,
        )
    }

    @Test
    fun `a session without a journey has no state`() {
        assertNull(buildNavigationModeUiState(NavigationSession()))
    }
}
