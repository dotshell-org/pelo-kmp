package eu.dotshell.pelo.generic.utils.map

import eu.dotshell.pelo.generic.data.models.realtime.vehiclepositions.SimpleVehiclePosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class VehiclePathInterpolatorTest {

    private fun vehicle(lat: Double, lon: Double, line: String = "B1") = SimpleVehiclePosition(
        vehicleId = "V1", lineName = line, latitude = lat, longitude = lon,
        bearing = null, destinationName = null, direction = null
    )

    // L-shaped trace: ~405 m east along lat 43.300, then ~400 m north along lon 5.385
    private val lShape = listOf(
        listOf(5.380, 43.300),
        listOf(5.385, 43.300),
        listOf(5.385, 43.3036)
    )

    private val interpolator = VehiclePathInterpolator(mapOf("B1" to listOf(lShape)))

    @Test
    fun `glide follows the corner of the trace instead of cutting the diagonal`() {
        val from = vehicle(43.300, 5.3805)
        val to = vehicle(43.3033, 5.385)
        val plan = interpolator.plan(from, to)

        val (midLat, midLon) = plan.at(0.5)
        // The halfway point must sit ON the L (near its corner), not on the
        // straight diagonal between the endpoints (~43.30165, ~5.38275).
        val onHorizontalBranch = abs(midLat - 43.300) < 1e-4
        val onVerticalBranch = abs(midLon - 5.385) < 1e-4
        assertTrue(
            "midpoint ($midLat, $midLon) should lie on the trace",
            onHorizontalBranch || onVerticalBranch
        )

        val (startLat, startLon) = plan.at(0.0)
        assertEquals(43.300, startLat, 1e-4)
        assertEquals(5.3805, startLon, 1e-4)
        val (endLat, endLon) = plan.at(1.0)
        assertEquals(43.3033, endLat, 1e-4)
        assertEquals(5.385, endLon, 1e-4)
    }

    @Test
    fun `overlapping out-and-back passes do not send the vehicle around the terminus`() {
        // One polyline going out along lat 43.300 and back along lat 43.3001
        // (~11 m apart, like tram rails)
        val outAndBack = listOf(
            listOf(5.380, 43.300),
            listOf(5.390, 43.300),
            listOf(5.390, 43.3001),
            listOf(5.380, 43.3001)
        )
        val tram = VehiclePathInterpolator(mapOf("T1" to listOf(outAndBack)))

        // 'from' sits exactly on the RETURN pass, 'to' exactly on the OUTBOUND
        // pass ~80 m further east. Naive nearest projection puts them ~1 km
        // apart along the path (around the terminus) — the TGV bug.
        val from = vehicle(43.3001, 5.383, line = "T1")
        val to = vehicle(43.300, 5.384, line = "T1")
        val plan = tram.plan(from, to)

        // The whole glide must stay within the ~90 m neighbourhood
        for (f in listOf(0.0, 0.25, 0.5, 0.75, 1.0)) {
            val (lat, lon) = plan.at(f)
            assertTrue("f=$f drifted to lon=$lon", lon in 5.3828..5.3842)
            assertTrue("f=$f drifted to lat=$lat", abs(lat - 43.3000) < 3e-4)
        }
        val (endLat, endLon) = plan.at(1.0)
        assertEquals(43.300, endLat, 1e-4)
        assertEquals(5.384, endLon, 1e-4)
    }

    @Test
    fun `implausibly long along-path glides fall back to the straight segment`() {
        // Both endpoints ~55 m from the horizontal leg (within snap range) but
        // 2.4 km apart along a stretched L -> must NOT race along the path
        val longL = listOf(
            listOf(5.360, 43.300),
            listOf(5.390, 43.300),
            listOf(5.390, 43.3036)
        )
        val stretched = VehiclePathInterpolator(mapOf("B1" to listOf(longL)))
        val from = vehicle(43.3005, 5.3605)
        val to = vehicle(43.3005, 5.3895)
        val plan = stretched.plan(from, to)
        val (midLat, midLon) = plan.at(0.5)
        // Straight-line midpoint, off the trace
        assertEquals(43.3005, midLat, 1e-6)
        assertEquals(5.375, midLon, 1e-6)
    }

    @Test
    fun `vehicle far from every trace glides on a straight segment`() {
        val from = vehicle(43.400, 5.500) // ~10 km off the L
        val to = vehicle(43.410, 5.510)
        val plan = interpolator.plan(from, to)
        val (midLat, midLon) = plan.at(0.5)
        assertEquals(43.405, midLat, 1e-6)
        assertEquals(5.505, midLon, 1e-6)
    }

    @Test
    fun `unknown line or first appearance stays static at the target`() {
        val target = vehicle(43.305, 5.395, line = "ZZ")
        val fresh = interpolator.plan(null, target)
        assertEquals(43.305 to 5.395, fresh.at(0.3))

        val movedUnknownLine = interpolator.plan(vehicle(43.30, 5.39, "ZZ"), target)
        val (endLat, endLon) = movedUnknownLine.at(1.0)
        assertEquals(43.305, endLat, 1e-9)
        assertEquals(5.395, endLon, 1e-9)
    }

    @Test
    fun `first appearance dead-reckons along the trace at the baseline speed`() {
        val baseline = mapOf(
            "B1" to eu.dotshell.pelo.generic.data.config.LineSpeedBaselineData(
                speedMps = 5.0,
                signs = mapOf("1" to mapOf("0" to 1))
            )
        )
        val withBaseline = VehiclePathInterpolator(mapOf("B1" to listOf(lShape)), baseline)

        // On the horizontal leg heading east, direction "1", no previous position
        val target = vehicle(43.300, 5.3805).copy(direction = "1")
        val plan = withBaseline.plan(null, target)

        val (startLat, startLon) = plan.at(0.0)
        assertEquals(43.300, startLat, 1e-4)
        assertEquals(5.3805, startLon, 1e-4)

        // 5 m/s x 55 s = 275 m further east along the leg
        val (endLat, endLon) = plan.at(1.0)
        assertEquals(43.300, endLat, 1e-4)
        assertTrue("expected ~275 m east, got lon=$endLon", endLon in 5.3830..5.3848)

        // Unknown direction or unmeasured sign: no risky guess, stay put
        val noDirection = withBaseline.plan(null, vehicle(43.300, 5.3805))
        assertEquals(43.300 to 5.3805, noDirection.at(1.0))
        val unmeasured = withBaseline.plan(null, target.copy(direction = "2"))
        assertEquals(43.300 to 5.3805, unmeasured.at(1.0))
    }
}
