package eu.dotshell.pelo.generic.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slicing a line between two stops is only meaningful if the line actually goes past them. This
 * is the check that decides that — the one whose absence let an unrelated variant of the same line
 * name contribute an arbitrary chunk of its trace to the map.
 *
 * Coordinates are GeoJSON order, [lon, lat].
 */
class NearestVertexOnLineTest {

    // A short north-easterly run through Lyon, roughly 150 m between vertices.
    private val line = listOf(
        listOf(4.8500, 45.7500),
        listOf(4.8520, 45.7510),
        listOf(4.8540, 45.7520),
        listOf(4.8560, 45.7530),
    )

    @Test
    fun `a stop on the line matches its nearest vertex`() {
        val match = nearestVertexOnLine(line, listOf(4.8540, 45.7520))!!
        assertEquals(2, match.index)
        assertTrue("essentially zero, got ${match.distanceMeters}", match.distanceMeters < 1.0)
    }

    @Test
    fun `a stop beside the line still matches, with its offset reported`() {
        // A platform sits off the centreline; that must not disqualify the variant.
        val match = nearestVertexOnLine(line, listOf(4.8540, 45.7523))!!
        assertEquals(2, match.index)
        assertTrue("tens of metres, got ${match.distanceMeters}", match.distanceMeters in 10.0..100.0)
    }

    @Test
    fun `a stop the line never approaches reports a distance that disqualifies it`() {
        val match = nearestVertexOnLine(line, listOf(4.9000, 45.8000))!!
        assertTrue(
            "must exceed the acceptance bound, got ${match.distanceMeters}",
            match.distanceMeters > MAX_STOP_TO_LINE_METERS,
        )
    }

    @Test
    fun `east-west offsets are not overstated`() {
        // A degree of longitude is ~0.70 of a degree of latitude at Lyon. Compared in raw degrees
        // this stop looked far enough to be rejected; in metres it is the same distance as the
        // northward case and must be accepted.
        val north = nearestVertexOnLine(line, listOf(4.8540, 45.7529))!!
        val east = nearestVertexOnLine(line, listOf(4.8553, 45.7520))!!
        assertEquals(north.distanceMeters, east.distanceMeters, 15.0)
        assertTrue(east.distanceMeters < MAX_STOP_TO_LINE_METERS)
    }

    @Test
    fun `an empty line matches nothing`() {
        assertNull(nearestVertexOnLine(emptyList(), listOf(4.85, 45.75)))
    }

    @Test
    fun `a malformed stop coordinate matches nothing`() {
        assertNull(nearestVertexOnLine(line, listOf(4.85)))
    }

    @Test
    fun `malformed vertices are skipped rather than crashing`() {
        val ragged = listOf(listOf(4.8500), line[2], emptyList())
        val match = nearestVertexOnLine(ragged, listOf(4.8540, 45.7520))!!
        assertEquals(1, match.index)
    }
}
