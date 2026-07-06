package eu.dotshell.massilia.specific.data.local

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parses the real bundled lines.bin (RLN2) and checks the RTM network invariants:
 * line count, known metro/tram lines with their GTFS colors, and that every
 * shape point falls inside the Marseille bounding box.
 */
class RtmLinesParserTest {

    private fun linesBin(): ByteArray {
        // Unit tests run with the module directory as working dir; fall back to
        // the repo root in case the runner uses it instead.
        val candidates = listOf(
            "src/commonMain/composeResources/files/raptor/lines.bin",
            "app/src/commonMain/composeResources/files/raptor/lines.bin"
        )
        val file = candidates.map(::File).firstOrNull(File::exists)
        assertNotNull("lines.bin not found from ${File(".").absolutePath}", file)
        return file!!.readBytes()
    }

    @Test
    fun parsesTheFullRtmNetwork() {
        val lines = RtmLinesParser.parse(linesBin())
        assertEquals("RTM GTFS has 125 routes", 125, lines.size)

        val byName = lines.associateBy { it.name }

        val m1 = byName["M1"]
        assertNotNull("M1 missing", m1)
        assertEquals("009FE3", m1!!.colorHex.uppercase())
        assertEquals("M1 is a metro (GTFS route_type 1)", 1, m1.gtfsRouteType)
        assertTrue("M1 has at least one shape", m1.paths.isNotEmpty())

        val m2 = byName["M2"]
        assertNotNull("M2 missing", m2)
        assertEquals("E30613", m2!!.colorHex.uppercase())

        for (tram in listOf("T1", "T2", "T3")) {
            val line = byName[tram]
            assertNotNull("$tram missing", line)
            assertEquals("$tram is a tram (GTFS route_type 0)", 0, line!!.gtfsRouteType)
        }

        for (water in listOf("FERRY", "NAV1", "NAV2", "NAV3")) {
            val line = byName[water]
            assertNotNull("$water missing", line)
            assertEquals("$water is a ferry (GTFS route_type 4)", 4, line!!.gtfsRouteType)
        }
    }

    @Test
    fun everyShapePointIsInsideTheMarseilleBoundingBox() {
        val lines = RtmLinesParser.parse(linesBin())
        var points = 0
        for (line in lines) {
            assertTrue("${line.name} has no shape", line.paths.isNotEmpty())
            for (path in line.paths) {
                assertTrue("${line.name} path with <2 points", path.points.size >= 2)
                for (point in path.points) {
                    val lon = point[0]
                    val lat = point[1]
                    assertTrue("${line.name}: lat $lat out of bounds", lat in 43.1..43.5)
                    assertTrue("${line.name}: lon $lon out of bounds", lon in 5.1..5.7)
                    points++
                }
            }
        }
        // The bundled RTM lines.bin carries ~71k shape points (~570 per line).
        assertTrue("expected a dense shape set, got $points points", points > 50_000)
    }
}
