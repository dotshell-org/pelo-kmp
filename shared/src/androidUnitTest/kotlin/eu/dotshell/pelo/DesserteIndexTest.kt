package eu.dotshell.pelo

import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.buildDessertesByStopName
import io.raptor.model.Route
import io.raptor.model.Stop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the desserte table that replaced the per-call scan in RaptorRepository.
 *
 * The old shape rebuilt a groupBy over every route in the period and filtered every stop on each
 * call, and searchStopsByName made up to fifty of those calls per keystroke. The table has to
 * produce exactly what that scan produced. Pure synthetic — no PlatformContext, no .bin assets.
 */
class DesserteIndexTest {

    private fun route(id: Int, name: String, stopIds: IntArray) = Route(
        id = id,
        name = name,
        stopIds = stopIds,
        tripCount = 1,
        stopCountInRoute = stopIds.size,
        flatStopTimes = IntArray(stopIds.size),
        tripIds = intArrayOf(id),
        hasOvernightTrips = false
    )

    private fun stop(id: Int, name: String, routeIds: IntArray) =
        Stop(id, name, 45.75, 4.85, routeIds, emptyList())

    /** Two distinct stop sequences under one route id become the A and R directions. */
    @Test
    fun bothDirectionsOfARouteAreLabelledAAndR() {
        val routes = mapOf(
            1 to listOf(
                route(1, "C3", intArrayOf(10, 11, 12)),
                route(1, "C3", intArrayOf(12, 11, 10))
            )
        )
        val table = buildDessertesByStopName(listOf(stop(10, "Bellecour", intArrayOf(1))), routes)

        assertEquals("C3:A,C3:R", table["bellecour"])
    }

    /** Variants sharing a stop sequence are one direction, not two — this is the distinctBy. */
    @Test
    fun duplicateStopSequencesCollapseToOneDirection() {
        val routes = mapOf(
            1 to listOf(
                route(1, "C3", intArrayOf(10, 11)),
                route(1, "C3", intArrayOf(10, 11))
            )
        )
        val table = buildDessertesByStopName(listOf(stop(10, "Bellecour", intArrayOf(1))), routes)

        assertEquals("C3:A", table["bellecour"])
    }

    /** Several quays share a physical stop name; their routes merge into one entry. */
    @Test
    fun stopsSharingANameAreMergedAndDeduplicated() {
        val routes = mapOf(
            1 to listOf(route(1, "C3", intArrayOf(10, 11))),
            2 to listOf(route(2, "T1", intArrayOf(10, 12)))
        )
        val stops = listOf(
            stop(10, "Bellecour", intArrayOf(1)),
            stop(20, "BELLECOUR", intArrayOf(2)),
            // Same route again on a third quay: must not appear twice.
            stop(21, "bellecour", intArrayOf(1))
        )

        assertEquals("C3:A,T1:A", buildDessertesByStopName(stops, routes)["bellecour"])
    }

    /** A stop whose routes are unknown has no desserte at all, rather than an empty string. */
    @Test
    fun stopWithNoResolvableRoutesIsAbsent() {
        val table = buildDessertesByStopName(
            listOf(stop(10, "Nowhere", intArrayOf(99)), stop(11, "Empty", IntArray(0))),
            emptyMap()
        )

        assertNull(table["nowhere"])
        assertNull(table["empty"])
    }

    /** The table is looked up with a lowercased name, whatever case the caller asks in. */
    @Test
    fun lookupIsCaseInsensitiveThroughLowercasing() {
        val routes = mapOf(1 to listOf(route(1, "C3", intArrayOf(10))))
        val table = buildDessertesByStopName(listOf(stop(10, "Gare Part-Dieu", intArrayOf(1))), routes)

        assertEquals("C3:A", table["Gare Part-Dieu".lowercase()])
        assertEquals("C3:A", table["GARE PART-DIEU".lowercase()])
    }
}
