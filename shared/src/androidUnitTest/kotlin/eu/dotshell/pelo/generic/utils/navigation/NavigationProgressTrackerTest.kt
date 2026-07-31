package eu.dotshell.pelo.generic.utils.navigation

import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.IntermediateStop
import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.JourneyLeg
import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.JourneyResult
import eu.dotshell.pelo.generic.utils.location.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the guidance rules that the navigation mode used to get wrong: progress that can only move
 * forward, a timetable fallback for when the signal is gone, and an arrival that actually fires.
 */
class NavigationProgressTrackerTest {

    // A two-leg journey around Lyon: walk to Bellecour, then ride to Part-Dieu via two stops.
    private val bellecour = GeoPoint(45.7578, 4.8320)
    private val guillotiere = GeoPoint(45.7554, 4.8420)
    private val saxe = GeoPoint(45.7570, 4.8500)
    private val partDieu = GeoPoint(45.7605, 4.8590)
    private val home = GeoPoint(45.7540, 4.8250)

    private val journey = JourneyResult(
        departureTime = 8 * 3600,
        arrivalTime = 8 * 3600 + 20 * 60,
        legs = listOf(
            JourneyLeg(
                fromStopId = "-1",
                fromStopName = "Ma position",
                fromLat = home.latitude,
                fromLon = home.longitude,
                toStopId = "1",
                toStopName = "Bellecour",
                toLat = bellecour.latitude,
                toLon = bellecour.longitude,
                departureTime = 8 * 3600,
                arrivalTime = 8 * 3600 + 5 * 60,
                routeName = null,
                routeColor = null,
                isWalking = true,
            ),
            JourneyLeg(
                fromStopId = "1",
                fromStopName = "Bellecour",
                fromLat = bellecour.latitude,
                fromLon = bellecour.longitude,
                toStopId = "4",
                toStopName = "Part-Dieu",
                toLat = partDieu.latitude,
                toLon = partDieu.longitude,
                departureTime = 8 * 3600 + 6 * 60,
                arrivalTime = 8 * 3600 + 20 * 60,
                routeName = "T1",
                routeColor = null,
                isWalking = false,
                direction = "Debourg",
                intermediateStops = listOf(
                    IntermediateStop("Guillotière", 8 * 3600 + 10 * 60, guillotiere.latitude, guillotiere.longitude),
                    IntermediateStop("Saxe", 8 * 3600 + 14 * 60, saxe.latitude, saxe.longitude),
                ),
            ),
        ),
    )

    @Test
    fun `starts on the first leg`() {
        val progress = NavigationProgressTracker(journey).current()
        assertEquals(0, progress.legIndex)
        assertEquals(0, progress.stopIndex)
        assertFalse(progress.isArrived)
    }

    @Test
    fun `a fix at an intermediate stop advances to it`() {
        val tracker = NavigationProgressTracker(journey)
        val progress = tracker.update(guillotiere, 8 * 3600 + 10 * 60)

        assertEquals(1, progress.legIndex)
        assertEquals(1, progress.stopIndex)
        assertEquals(2, progress.remainingStopsOnLeg)
    }

    @Test
    fun `progress never rewinds when a fix jumps backwards`() {
        val tracker = NavigationProgressTracker(journey)
        tracker.update(saxe, 8 * 3600 + 14 * 60)

        // A stray fix back at the boarding stop — a reflection, a cached position, or simply a
        // route that loops near itself. It must not throw the guidance back to the first leg.
        val progress = tracker.update(bellecour, 8 * 3600 + 15 * 60)

        assertEquals(1, progress.legIndex)
        assertEquals(2, progress.stopIndex)
    }

    @Test
    fun `the timetable carries progress when no fix is available`() {
        val tracker = NavigationProgressTracker(journey)
        tracker.update(bellecour, 8 * 3600 + 6 * 60)

        // Underground: no usable fix for eight minutes. Guidance used to freeze on the boarding
        // stop for the whole ride; it should now have moved through the line's stops.
        val progress = tracker.update(null, 8 * 3600 + 14 * 60)

        assertEquals(1, progress.legIndex)
        assertEquals(2, progress.stopIndex)
        assertTrue(progress.isDeadReckoning)
    }

    @Test
    fun `a fix far from the route is reported as off-route and never snaps progress to it`() {
        val tracker = NavigationProgressTracker(journey)
        val before = tracker.update(home, 8 * 3600)

        // Kilometres away from every stop. Without a snap radius the nearest-stop scan still
        // returned a winner, and that arbitrary match silently rewrote which leg was current.
        val progress = tracker.update(GeoPoint(45.9000, 5.2000), 8 * 3600)

        assertTrue(progress.isOffRoute)
        assertEquals(before.legIndex, progress.legIndex)
        assertEquals(before.stopIndex, progress.stopIndex)
    }

    @Test
    fun `an off-route fix still lets the timetable carry progress`() {
        val tracker = NavigationProgressTracker(journey)
        tracker.update(bellecour, 8 * 3600 + 6 * 60)

        // Off-route is treated like no fix at all: the schedule is the only signal left, and it
        // is a better one than a position that cannot be placed on the route.
        val progress = tracker.update(GeoPoint(45.9000, 5.2000), 8 * 3600 + 14 * 60)

        assertTrue(progress.isOffRoute)
        assertEquals(1, progress.legIndex)
        assertEquals(2, progress.stopIndex)
    }

    @Test
    fun `off-route time accumulates only while continuously astray`() {
        val tracker = NavigationProgressTracker(journey)
        val away = GeoPoint(45.9000, 5.2000)

        tracker.update(bellecour, 8 * 3600 + 6 * 60)
        assertEquals(0, tracker.update(away, 8 * 3600 + 6 * 60).offRouteSeconds)
        assertEquals(30, tracker.update(away, 8 * 3600 + 6 * 60 + 30).offRouteSeconds)

        // Back on the route: the clock resets, so a brief detour never accumulates towards a
        // replanning prompt.
        assertEquals(0, tracker.update(guillotiere, 8 * 3600 + 7 * 60).offRouteSeconds)
        assertEquals(0, tracker.update(away, 8 * 3600 + 7 * 60 + 10).offRouteSeconds)
    }

    @Test
    fun `losing the signal is not being off-route`() {
        val tracker = NavigationProgressTracker(journey)
        tracker.update(bellecour, 8 * 3600 + 6 * 60)

        // No fix at all: dead reckoning, not astray. Offering to replan here would be guessing.
        val progress = tracker.update(null, 8 * 3600 + 8 * 60)
        assertFalse(progress.isOffRoute)
        assertEquals(0, progress.offRouteSeconds)
    }

    @Test
    fun `arrival fires once the destination is reached`() {
        val tracker = NavigationProgressTracker(journey)
        tracker.update(saxe, 8 * 3600 + 14 * 60)

        val progress = tracker.update(partDieu, 8 * 3600 + 20 * 60)

        assertTrue(progress.isArrived)
    }

    @Test
    fun `arrival does not fire while still short of the destination`() {
        val tracker = NavigationProgressTracker(journey)
        val progress = tracker.update(saxe, 8 * 3600 + 14 * 60)
        assertFalse(progress.isArrived)
    }

    @Test
    fun `arrival falls back on the timetable when there is no fix to confirm it`() {
        val tracker = NavigationProgressTracker(journey)
        tracker.update(bellecour, 8 * 3600 + 6 * 60)

        val stillRiding = tracker.update(null, 8 * 3600 + 20 * 60)
        assertFalse("grace period not yet elapsed", stillRiding.isArrived)

        val arrived = tracker.update(null, 8 * 3600 + 22 * 60)
        assertTrue(arrived.isArrived)
    }

    @Test
    fun `journeys that run past midnight are handled`() {
        // 23:50 → 00:10, expressed as a GTFS service day (24:10 = 87_000s).
        val nightJourney = journey.copy(
            departureTime = 23 * 3600 + 50 * 60,
            arrivalTime = 24 * 3600 + 10 * 60,
            legs = journey.legs.map {
                it.copy(
                    departureTime = it.departureTime + 15 * 3600 + 50 * 60,
                    arrivalTime = it.arrivalTime + 15 * 3600 + 50 * 60,
                    intermediateStops = it.intermediateStops.map { stop ->
                        stop.copy(arrivalTime = stop.arrivalTime + 15 * 3600 + 50 * 60)
                    },
                )
            },
        )
        val tracker = NavigationProgressTracker(nightJourney)

        // 00:05 on the wall clock is 24:05 on the service day — past Saxe, not before departure.
        val progress = tracker.update(null, 5 * 60)

        assertEquals(1, progress.legIndex)
        assertTrue(progress.stopIndex >= 2)
    }
}
