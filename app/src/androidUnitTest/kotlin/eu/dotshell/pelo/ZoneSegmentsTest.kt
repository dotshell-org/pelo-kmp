package eu.dotshell.pelo

import eu.dotshell.pelo.generic.data.models.gtfs.LineStopInfo
import eu.dotshell.pelo.generic.ui.screens.plan.ZoneSegmentKind
import eu.dotshell.pelo.generic.ui.screens.plan.computeZoneSegments
import eu.dotshell.pelo.generic.ui.screens.plan.zoneShortLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class ZoneSegmentsTest {

    private fun stops(vararg zones: String?): List<LineStopInfo> =
        zones.mapIndexed { i, z -> LineStopInfo(stopId = "s$i", stopName = "Stop $i", stopSequence = i, zone = z) }

    private fun kinds(vararg zones: String?) = computeZoneSegments(stops(*zones)).map { it.kind }
    private fun labelRows(vararg zones: String?) =
        computeZoneSegments(stops(*zones)).mapIndexedNotNull { i, s -> i.takeIf { s.showLabel } }

    @Test fun emptyListYieldsNoSegments() {
        assertEquals(0, computeZoneSegments(emptyList()).size)
    }

    @Test fun allNullZonesAreNone() {
        assertEquals(List(3) { ZoneSegmentKind.NONE }, kinds(null, null, null))
    }

    @Test fun homogeneousRunHasStartMiddleEnd() {
        assertEquals(
            listOf(ZoneSegmentKind.START, ZoneSegmentKind.MIDDLE, ZoneSegmentKind.MIDDLE, ZoneSegmentKind.END),
            kinds("1", "1", "1", "1")
        )
        // Label on the middle row: run [0..3] → 0 + (3-0)/2 = 1.
        assertEquals(listOf(1), labelRows("1", "1", "1", "1"))
    }

    @Test fun singleStopRunIsSingleWithLabel() {
        val segs = computeZoneSegments(stops("1"))
        assertEquals(ZoneSegmentKind.SINGLE, segs[0].kind)
        assertEquals(true, segs[0].showLabel)
    }

    @Test fun adjacentRunsSplit() {
        assertEquals(
            listOf(ZoneSegmentKind.START, ZoneSegmentKind.END, ZoneSegmentKind.START, ZoneSegmentKind.END),
            kinds("1", "1", "2", "2")
        )
        // Two-stop runs: labelRow = i + 0 = the START row of each run (0 and 2).
        assertEquals(listOf(0, 2), labelRows("1", "1", "2", "2"))
    }

    @Test fun nullGapSplitsSameZoneIntoTwoRuns() {
        assertEquals(
            listOf(ZoneSegmentKind.SINGLE, ZoneSegmentKind.NONE, ZoneSegmentKind.SINGLE),
            kinds("1", null, "1")
        )
    }

    @Test fun alternatingZonesAreThreeSingles() {
        assertEquals(
            listOf(ZoneSegmentKind.SINGLE, ZoneSegmentKind.SINGLE, ZoneSegmentKind.SINGLE),
            kinds("1", "2", "1")
        )
    }

    @Test fun zoneAttachedToSegments() {
        val segs = computeZoneSegments(stops("1", "1", "Zone Externe"))
        assertEquals("1", segs[0].zone)
        assertEquals("1", segs[1].zone)
        assertEquals("Zone Externe", segs[2].zone)
    }

    @Test fun shortLabelAbbreviatesExternalZone() {
        assertEquals("EXT", zoneShortLabel("Zone Externe"))
        assertEquals("EXT", zoneShortLabel("zone externe"))
        assertEquals("3", zoneShortLabel("3"))
    }
}
