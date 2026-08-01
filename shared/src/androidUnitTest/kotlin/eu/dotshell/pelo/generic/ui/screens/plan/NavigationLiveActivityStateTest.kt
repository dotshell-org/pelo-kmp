package eu.dotshell.pelo.generic.ui.screens.plan

import eu.dotshell.pelo.generic.data.models.navigation.NavigationProgress
import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.JourneyLeg
import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.JourneyResult
import eu.dotshell.pelo.generic.service.NavigationSegmentKind
import eu.dotshell.pelo.generic.service.NavigationSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the geometry a glanceable surface draws. Every renderer — the Android progress notification,
 * the Dynamic Island — places the tracker by assuming the segments sum to the whole journey, so a
 * segment quietly going missing does not show up as a gap: it shows up as guidance claiming the
 * traveller is further along than they are.
 */
class NavigationLiveActivityStateTest {

    private val t1Color = 0xFF00AA00.toInt()
    private val busColor = 0xFFCC0000.toInt()
    private val colors: (String) -> Int? = { if (it == "T1") t1Color else busColor }

    private fun leg(
        from: String,
        to: String,
        routeName: String?,
        departure: Int,
        arrival: Int,
        walking: Boolean = false,
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
    )

    /** Walk 08:00-08:05, wait to 08:08, T1 to 08:18, wait to 08:20, bus to 08:30. */
    private val journey = JourneyResult(
        departureTime = 8 * 3600,
        arrivalTime = 8 * 3600 + 30 * 60,
        legs = listOf(
            leg("Home", "Bellecour", null, 8 * 3600, 8 * 3600 + 5 * 60, walking = true),
            leg("Bellecour", "Saxe", "T1", 8 * 3600 + 8 * 60, 8 * 3600 + 18 * 60),
            leg("Saxe", "Part-Dieu", "B", 8 * 3600 + 20 * 60, 8 * 3600 + 30 * 60),
        ),
    )

    private fun stateAt(now: Int, journey: JourneyResult = this.journey, isArrived: Boolean = false) =
        buildNavigationLiveActivityState(
            ui = buildNavigationModeUiState(
                NavigationSession(
                    isActive = true,
                    journey = journey,
                    progress = NavigationProgress(
                        legIndex = 1,
                        stopIndex = 0,
                        stopCount = 2,
                        isArrived = isArrived,
                    ),
                    hasFreshFix = true,
                    nowSeconds = now,
                )
            )!!,
            journey = journey,
            instructionText = "Descendez à Part-Dieu",
            lineColor = colors,
        )

    @Test
    fun `waiting at a stop is part of the journey, so the segments still sum to it`() {
        val state = stateAt(8 * 3600 + 12 * 60)

        assertEquals(30 * 60, state.totalSeconds)
        assertEquals(30 * 60, state.segments.sumOf { it.seconds })
        assertEquals(
            listOf(
                NavigationSegmentKind.WALK to 5 * 60,
                NavigationSegmentKind.WAIT to 3 * 60,
                NavigationSegmentKind.RIDE to 10 * 60,
                NavigationSegmentKind.WAIT to 2 * 60,
                NavigationSegmentKind.RIDE to 10 * 60,
            ),
            state.segments.map { it.kind to it.seconds },
        )
    }

    @Test
    fun `only ridden segments carry a line colour`() {
        val segments = stateAt(8 * 3600 + 12 * 60).segments

        assertEquals(listOf(null, null, t1Color, null, busColor), segments.map { it.colorArgb })
    }

    @Test
    fun `progress is where the traveller is, on the same scale as the segments`() {
        // 08:12 — four minutes into the T1 ride, which starts 8 minutes in.
        val state = stateAt(8 * 3600 + 12 * 60)

        assertEquals(12 * 60, state.progressSeconds)
        assertEquals(18, state.remainingMinutes)
    }

    @Test
    fun `a journey started before its departure sits at the start, not behind it`() {
        // 07:55: the remaining time (35 min) is longer than the trip itself.
        assertEquals(0, stateAt(7 * 3600 + 55 * 60).progressSeconds)
    }

    @Test
    fun `an overrun clamps to the end rather than running past it`() {
        val state = stateAt(9 * 3600)

        assertEquals(state.totalSeconds, state.progressSeconds)
        assertEquals(0, state.remainingMinutes)
    }

    @Test
    fun `a change of line is marked where the traveller gets off`() {
        val state = stateAt(8 * 3600 + 12 * 60)

        // End of the T1 ride: 5 min walk + 3 min wait + 10 min ride.
        assertEquals(listOf(18 * 60), state.transferOffsetsSeconds)
    }

    @Test
    fun `a single ride has nothing to change to`() {
        val direct = JourneyResult(
            departureTime = 8 * 3600,
            arrivalTime = 8 * 3600 + 10 * 60,
            legs = listOf(leg("Bellecour", "Saxe", "T1", 8 * 3600, 8 * 3600 + 10 * 60)),
        )

        val state = stateAt(8 * 3600 + 60, direct)
        assertTrue(state.transferOffsetsSeconds.isEmpty())
        assertEquals(listOf(NavigationSegmentKind.RIDE), state.segments.map { it.kind })
    }

    @Test
    fun `a walk-only journey has no transfers and no colours`() {
        val onFoot = JourneyResult(
            departureTime = 8 * 3600,
            arrivalTime = 8 * 3600 + 12 * 60,
            legs = listOf(leg("Home", "Work", null, 8 * 3600, 8 * 3600 + 12 * 60, walking = true)),
        )

        val state = stateAt(8 * 3600 + 60, onFoot)
        assertTrue(state.transferOffsetsSeconds.isEmpty())
        assertEquals(listOf(NavigationSegmentKind.WALK), state.segments.map { it.kind })
        assertNull(state.segments.single().colorArgb)
    }

    /**
     * GTFS service-day times run past midnight while the wall clock wraps at it. Measured naively,
     * this journey lasts minus twenty-three hours and forty minutes.
     */
    @Test
    fun `a journey across midnight lasts twenty minutes, not minus a day`() {
        val overnight = JourneyResult(
            departureTime = 23 * 3600 + 50 * 60,
            arrivalTime = 10 * 60,
            legs = listOf(leg("Bellecour", "Saxe", "T1", 23 * 3600 + 50 * 60, 10 * 60)),
        )

        val state = stateAt(23 * 3600 + 55 * 60, overnight)
        assertEquals(20 * 60, state.totalSeconds)
        assertEquals(5 * 60, state.progressSeconds)
    }

    @Test
    fun `the destination is where the journey ends, not the time it ends at`() {
        assertEquals("Part-Dieu", stateAt(8 * 3600 + 12 * 60).destination)
    }
}
