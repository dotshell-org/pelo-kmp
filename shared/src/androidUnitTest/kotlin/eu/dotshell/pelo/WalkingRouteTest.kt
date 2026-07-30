package eu.dotshell.pelo

import eu.dotshell.pelo.generic.data.network.routing.OsrmRouteResponse
import eu.dotshell.pelo.generic.data.repository.routing.buildWalkingPath
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the OSRM foot-routing response mapping: GeoJSON [lon, lat] ordering, stitching of the
 * exact endpoints around the road-snapped geometry, and unusable-response fallbacks.
 */
class WalkingRouteTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    private val fixture = """
    {
      "code": "Ok",
      "routes": [{
        "distance": 2161.8,
        "duration": 1730.9,
        "geometry": { "type": "LineString",
                      "coordinates": [[4.831956, 45.757812], [4.833, 45.760], [4.835757, 45.774491]] }
      }]
    }
    """.trimIndent()

    @Test
    fun stitchesExactEndpointsAroundSnappedGeometry() {
        val response = json.decodeFromString<OsrmRouteResponse>(fixture)
        val path = buildWalkingPath(response, fromLat = 45.7578, fromLon = 4.8320, toLat = 45.7745, toLon = 4.8357)

        requireNotNull(path)
        assertEquals(5, path.size) // exact from + 3 street points + exact to
        assertEquals(4.8320, path.first()[0], 1e-9)
        assertEquals(45.7578, path.first()[1], 1e-9)
        assertEquals(4.831956, path[1][0], 1e-9) // street points keep [lon, lat] order
        assertEquals(4.8357, path.last()[0], 1e-9)
        assertEquals(45.7745, path.last()[1], 1e-9)
    }

    @Test
    fun nonOkOrEmptyResponsesGiveNoPath() {
        val notOk = json.decodeFromString<OsrmRouteResponse>("""{"code":"NoRoute","routes":[]}""")
        assertNull(buildWalkingPath(notOk, 45.0, 4.0, 45.1, 4.1))

        val emptyGeometry = json.decodeFromString<OsrmRouteResponse>(
            """{"code":"Ok","routes":[{"geometry":{"coordinates":[[4.83,45.75]]}}]}"""
        )
        assertNull(buildWalkingPath(emptyGeometry, 45.0, 4.0, 45.1, 4.1))
    }
}
