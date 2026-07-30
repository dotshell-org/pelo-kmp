package eu.dotshell.pelo.generic.utils.geo

import eu.dotshell.pelo.generic.utils.location.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The nearest-neighbour ranking used to compare raw degree deltas, which over-weights east–west
 * separation by ~43% at Lyon's latitude and picked the wrong stop for a traveller standing
 * between two of them.
 */
class GeometryUtilsMetricTest {

    private val lyon = 45.75

    @Test
    fun `squaredMeters agrees with haversine to within a percent over short spans`() {
        val metric = sqrt(GeometryUtils.squaredMeters(lyon, 4.85, lyon + 0.01, 4.86))
        val haversine = GeometryUtils.distanceMeters(lyon, 4.85, lyon + 0.01, 4.86)
        assertTrue(
            "planar $metric vs haversine $haversine",
            abs(metric - haversine) / haversine < 0.01,
        )
    }

    @Test
    fun `equal metric offsets north and east rank equally`() {
        // 500 m north and 500 m east of the same origin. Compared in raw degrees the eastern
        // point looked ~43% further away and lost every nearest-stop contest.
        val northMetres = sqrt(GeometryUtils.squaredMeters(lyon, 4.85, lyon + 0.0045, 4.85))
        val eastMetres = sqrt(
            GeometryUtils.squaredMeters(lyon, 4.85, lyon, 4.85 + 0.0045 / kotlin.math.cos(Math.toRadians(lyon)))
        )
        assertEquals(northMetres, eastMetres, 1.0)

        val degreesNorth = GeometryUtils.squaredDistance(lyon, 4.85, lyon + 0.0045, 4.85)
        val degreesEast = GeometryUtils.squaredDistance(
            lyon, 4.85, lyon, 4.85 + 0.0045 / kotlin.math.cos(Math.toRadians(lyon))
        )
        assertTrue("the degree metric really is biased", degreesEast > degreesNorth * 1.4)
    }

    @Test
    fun `shortestAngleDelta crosses the north seam the short way`() {
        assertEquals(20.0, GeometryUtils.shortestAngleDelta(350.0, 10.0), 0.001)
        assertEquals(-20.0, GeometryUtils.shortestAngleDelta(10.0, 350.0), 0.001)
        assertEquals(0.0, GeometryUtils.shortestAngleDelta(90.0, 90.0), 0.001)
        assertTrue(abs(GeometryUtils.shortestAngleDelta(0.0, 180.0)) <= 180.0)
    }

    @Test
    fun `a fix far off the path yields no navigation axis`() {
        val path = listOf(GeoPoint(lyon, 4.85), GeoPoint(lyon + 0.01, 4.86))
        assertNull(GeometryUtils.findNavigationAxisSegment(GeoPoint(45.90, 5.20), path))
    }

    @Test
    fun `a fix on the path yields the segment ahead`() {
        val path = listOf(GeoPoint(lyon, 4.85), GeoPoint(lyon + 0.01, 4.86))
        val segment = GeometryUtils.findNavigationAxisSegment(GeoPoint(lyon + 0.005, 4.855), path)
        assertNotNull(segment)
        val bearing = GeometryUtils.computeBearingDegrees(segment!!.first, segment.second)
        assertTrue("north-east heading, got $bearing", bearing in 20.0..70.0)
    }
}
