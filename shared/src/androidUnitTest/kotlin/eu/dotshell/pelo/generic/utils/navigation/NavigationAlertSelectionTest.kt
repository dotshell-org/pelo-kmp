package eu.dotshell.pelo.generic.utils.navigation

import eu.dotshell.pelo.generic.data.models.realtime.alerts.official.TrafficAlert
import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.JourneyLeg
import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.JourneyResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the traveller is shown mid-journey has to earn the interruption: the right line, still
 * ahead, and severe enough to change a decision.
 */
class NavigationAlertSelectionTest {

    private fun leg(route: String?, walking: Boolean = false) = JourneyLeg(
        fromStopId = "a", fromStopName = "A", fromLat = 45.75, fromLon = 4.85,
        toStopId = "b", toStopName = "B", toLat = 45.76, toLon = 4.86,
        departureTime = 0, arrivalTime = 600,
        routeName = route, routeColor = null, isWalking = walking,
    )

    private val journey = JourneyResult(
        departureTime = 0,
        arrivalTime = 1800,
        legs = listOf(leg(null, walking = true), leg("T1"), leg("B12"), leg(null, walking = true)),
    )

    private fun alert(
        number: Int,
        line: String,
        severityType: String = "SIGNIFICANT_DELAYS",
        severityLevel: Int = 20,
    ) = TrafficAlert(
        cause = "", startDate = "", endDate = "", lastUpdate = "",
        lineCode = line, lineName = line, objectList = "",
        message = "message $number", mode = "", alertNumber = number,
        severityLevel = severityLevel, title = "title $number",
        alertType = "", objectType = "", severityType = severityType,
    )

    private fun select(fromLeg: Int, alerts: Map<String, List<TrafficAlert>>) =
        selectNavigationAlert(journey, fromLeg) { alerts[it].orEmpty() }

    @Test
    fun `no alert on the journey's lines yields nothing`() {
        assertEquals(null, select(0, mapOf("T3" to listOf(alert(1, "T3")))))
    }

    @Test
    fun `an alert on a line ahead is surfaced`() {
        val picked = select(0, mapOf("B12" to listOf(alert(7, "B12"))))
        assertEquals(7, picked?.alertNumber)
    }

    @Test
    fun `a line already behind is ignored`() {
        // Riding B12 now: a disruption on T1 is history and cannot change any decision left.
        assertNull(select(2, mapOf("T1" to listOf(alert(7, "T1")))))
    }

    @Test
    fun `informational alerts never interrupt`() {
        val alerts = mapOf("T1" to listOf(alert(3, "T1", "INFORMATION", severityLevel = 40)))
        assertNull(select(0, alerts))
    }

    @Test
    fun `unknown severity never interrupts`() {
        // level 0 would otherwise sort as the most severe of all and win every comparison.
        val alerts = mapOf("T1" to listOf(alert(3, "T1", "SOMETHING_ELSE", severityLevel = 0)))
        assertNull(select(0, alerts))
    }

    @Test
    fun `the most severe alert wins`() {
        val picked = select(
            0,
            mapOf(
                "T1" to listOf(alert(1, "T1", "OTHER_EFFECT", severityLevel = 30)),
                "B12" to listOf(alert(2, "B12", "SIGNIFICANT_DELAYS", severityLevel = 20)),
            )
        )
        assertEquals(2, picked?.alertNumber)
    }

    @Test
    fun `equal severities resolve the same way every time`() {
        val alerts = mapOf(
            "T1" to listOf(alert(9, "T1")),
            "B12" to listOf(alert(4, "B12")),
        )
        // Without a tie-break the banner could swap between two equals on every refresh.
        assertEquals(4, select(0, alerts)?.alertNumber)
        assertEquals(4, select(0, alerts)?.alertNumber)
    }

    @Test
    fun `an alert affecting two lines of the journey is considered once`() {
        val shared = alert(5, "T1")
        val picked = select(0, mapOf("T1" to listOf(shared), "B12" to listOf(shared)))
        assertEquals(5, picked?.alertNumber)
    }

    @Test
    fun `a walking-only journey has no line to alert on`() {
        val walkOnly = JourneyResult(0, 600, listOf(leg(null, walking = true)))
        assertNull(selectNavigationAlert(walkOnly, 0) { listOf(alert(1, "T1")) })
    }

    @Test
    fun `no journey yields nothing`() {
        assertNull(selectNavigationAlert(null, 0) { listOf(alert(1, "T1")) })
    }
}
