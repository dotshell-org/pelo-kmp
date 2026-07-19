package eu.dotshell.pelo

import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.JourneyLeg
import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.JourneyResult
import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.journeyWalkingMeters
import io.raptor.WalkingParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ItineraryWalkUtilsTest {

    private val mps = WalkingParams.DEFAULT.speedMetersPerSecond // 4.8 km/h
    private val eps = 1e-6

    private fun leg(durationSeconds: Int, walking: Boolean, start: Int = 0): JourneyLeg = JourneyLeg(
        fromStopId = "a", fromStopName = "A", fromLat = 0.0, fromLon = 0.0,
        toStopId = "b", toStopName = "B", toLat = 0.0, toLon = 0.0,
        departureTime = start, arrivalTime = start + durationSeconds,
        routeName = if (walking) null else "24", routeColor = null, isWalking = walking
    )

    private fun journey(vararg legs: JourneyLeg) =
        JourneyResult(departureTime = 0, arrivalTime = legs.lastOrNull()?.arrivalTime ?: 0, legs = legs.toList())

    @Test fun sumsOnlyWalkingLegsTimesSpeed() {
        // 300 s walk + 600 s ride + 120 s walk => 420 s of walking.
        val j = journey(leg(300, walking = true), leg(600, walking = false), leg(120, walking = true))
        assertEquals(420.0 * mps, journeyWalkingMeters(j, mps), eps)
    }

    @Test fun transitOnlyJourneyHasNoWalk() {
        val j = journey(leg(600, walking = false), leg(900, walking = false))
        assertEquals(0.0, journeyWalkingMeters(j, mps), eps)
    }

    @Test fun sixKilometerCapKeepsAndRejects() {
        val cap = 6000.0
        // 6 km at 4.8 km/h = 4500 s exactly on the cap; one second more is over.
        val onCap = journey(leg(4500, walking = true))
        val overCap = journey(leg(4501, walking = true))
        assertTrue(journeyWalkingMeters(onCap, mps) <= cap)
        assertFalse(journeyWalkingMeters(overCap, mps) <= cap)
    }
}
