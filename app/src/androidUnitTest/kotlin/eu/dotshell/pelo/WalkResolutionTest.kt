package eu.dotshell.pelo

import eu.dotshell.pelo.generic.data.network.routing.OsrmTableResponse
import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.buildStopWalks
import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.nearbyStopCandidates
import io.raptor.model.Stop
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Point->ResolvedPoint upgrade helpers: candidate selection around a point, OSRM
 * table decoding (null = unreachable) and street-distance -> walk-seconds conversion.
 */
class WalkResolutionTest {

    private fun stop(id: Int, lat: Double, lon: Double) =
        Stop(id, "S$id", lat, lon, IntArray(0), emptyList())

    // ~1 degree lat = 111.32 km in the helper's model; 0.001 deg ≈ 111 m
    private val center = 45.7500 to 4.8500

    @Test
    fun candidatesAreWithinRadiusSortedAndCapped() {
        val stops = listOf(
            stop(1, 45.7500, 4.8500),          // 0 m
            stop(2, 45.7530, 4.8500),          // ~334 m
            stop(3, 45.7515, 4.8500),          // ~167 m
            stop(4, 45.7600, 4.8500),          // ~1113 m -> outside 500 m
            stop(5, 45.7500, 4.8560)           // ~466 m (lon scaled by cos)
        )
        val candidates = nearbyStopCandidates(stops, center.first, center.second, 500.0, maxCandidates = 10)
        assertEquals(listOf(1, 3, 2, 5), candidates.map { it.id })

        val capped = nearbyStopCandidates(stops, center.first, center.second, 500.0, maxCandidates = 2)
        assertEquals(listOf(1, 3), capped.map { it.id })
    }

    @Test
    fun stopWalksUseRealDistancesAndDropUnreachableOrAbsurd() {
        val candidates = listOf(stop(1, 0.0, 0.0), stop(2, 0.0, 0.0), stop(3, 0.0, 0.0), stop(4, 0.0, 0.0))
        val distances = listOf(400.0, null, 1200.0, 133.4)
        // speed 4.8 km/h = 1.333... m/s; cap at 1000 m
        val walks = buildStopWalks(candidates, distances, speedMetersPerSecond = 4.8 / 3.6, maxWalkDistanceMeters = 1000.0)

        assertEquals(2, walks.size)
        assertEquals(1, walks[0].stopId)
        assertEquals(300, walks[0].walkSeconds) // 400 m / 1.333 m/s = 300 s
        assertEquals(4, walks[1].stopId)
        assertEquals(101, walks[1].walkSeconds) // 133.4 / 1.333 = 100.05 -> ceil 101
    }

    @Test
    fun tableResponseDecodesWithNullEntries() {
        val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
        val response = json.decodeFromString<OsrmTableResponse>(
            """{"code":"Ok","distances":[[0,751.9,null,489.2]]}"""
        )
        assertEquals("Ok", response.code)
        val row = response.distances.single()
        assertEquals(4, row.size)
        assertEquals(0.0, row[0]!!, 1e-9)
        assertEquals(751.9, row[1]!!, 1e-9)
        assertTrue(row[2] == null)
        assertEquals(489.2, row[3]!!, 1e-9)
    }
}
