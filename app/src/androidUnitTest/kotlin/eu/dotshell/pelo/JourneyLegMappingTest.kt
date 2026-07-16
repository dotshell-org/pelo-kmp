package eu.dotshell.pelo

import eu.dotshell.pelo.generic.data.cache.journey.SerializableJourneyLeg
import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.JourneyLegKind
import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.mapLibraryJourneys
import io.raptor.core.JourneyLeg as RaptorLeg
import io.raptor.core.LegType
import io.raptor.model.Stop
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the raptor->app journey mapping, in particular the -1 coordinate endpoints introduced
 * by raptor-kmp 1.8 (walk legs from/to an address or GPS point) and the legKind cache
 * compatibility. Pure synthetic — no PlatformContext, no .bin assets.
 */
class JourneyLegMappingTest {

    private val stops = mapOf(
        0 to Stop(10, "Alpha", 45.75, 4.85, IntArray(0), emptyList()),
        1 to Stop(11, "Beta", 45.76, 4.86, IntArray(0), emptyList())
    )

    private fun transitLeg(from: Int, to: Int, dep: Int, arr: Int) = RaptorLeg(
        fromStopIndex = from,
        toStopIndex = to,
        departureTime = dep,
        arrivalTime = arr,
        routeName = "T1",
        isTransfer = false
    )

    @Test
    fun coordinateWalkLegsAreMappedWithLabelsNotDropped() {
        val access = RaptorLeg(
            fromStopIndex = -1, toStopIndex = 0, departureTime = 100, arrivalTime = 200,
            routeName = null, isTransfer = true, legType = LegType.WALK_ACCESS,
            fromLat = 45.70, fromLon = 4.80, toLat = 45.75, toLon = 4.85
        )
        val egress = RaptorLeg(
            fromStopIndex = 1, toStopIndex = -1, departureTime = 500, arrivalTime = 600,
            routeName = null, isTransfer = true, legType = LegType.WALK_EGRESS,
            fromLat = 45.76, fromLon = 4.86, toLat = 45.77, toLon = 4.87
        )
        val results = mapLibraryJourneys(
            listOf(listOf(access, transitLeg(0, 1, 250, 500), egress)),
            stops,
            originLabel = "12 rue Test",
            destinationLabel = "Carrefour Part-Dieu"
        )

        assertEquals(1, results.size)
        val legs = results[0].legs
        assertEquals(3, legs.size)

        assertEquals("-1", legs[0].fromStopId)
        assertEquals("12 rue Test", legs[0].fromStopName)
        assertEquals(45.70, legs[0].fromLat, 1e-9)
        assertEquals(4.80, legs[0].fromLon, 1e-9)
        assertEquals("Alpha", legs[0].toStopName)
        assertEquals(JourneyLegKind.WALK_ACCESS, legs[0].legKind)
        assertTrue(legs[0].isWalking)

        assertEquals(JourneyLegKind.TRANSIT, legs[1].legKind)
        assertEquals("T1", legs[1].routeName)

        assertEquals("Carrefour Part-Dieu", legs[2].toStopName)
        assertEquals("-1", legs[2].toStopId)
        assertEquals(45.77, legs[2].toLat, 1e-9)
        assertEquals(JourneyLegKind.WALK_EGRESS, legs[2].legKind)

        assertEquals(100, results[0].departureTime)
        assertEquals(600, results[0].arrivalTime)
    }

    @Test
    fun unknownPositiveIndexStillDropsTheJourney() {
        val results = mapLibraryJourneys(listOf(listOf(transitLeg(0, 99, 100, 200))), stops)
        assertTrue(results.isEmpty())
    }

    @Test
    fun directWalkJourneyMapsBothCoordinateEndpoints() {
        val walk = RaptorLeg(
            fromStopIndex = -1, toStopIndex = -1, departureTime = 0, arrivalTime = 300,
            routeName = null, isTransfer = true, legType = LegType.WALK_DIRECT,
            fromLat = 45.70, fromLon = 4.80, toLat = 45.71, toLon = 4.81
        )
        val results = mapLibraryJourneys(listOf(listOf(walk)), stops, "Départ", "Arrivée")

        assertEquals(1, results.size)
        val leg = results[0].legs.single()
        assertEquals(JourneyLegKind.WALK_DIRECT, leg.legKind)
        assertEquals("Départ", leg.fromStopName)
        assertEquals("Arrivée", leg.toStopName)
        assertTrue(leg.isWalking)
    }

    @Test
    fun coordinateLegWithoutCoordinatesIsInvalid() {
        // The library contract guarantees coordinates on -1 endpoints; defensively a violation
        // must drop the journey like any unresolvable leg, not crash or emit (0,0)
        val broken = RaptorLeg(
            fromStopIndex = -1, toStopIndex = 0, departureTime = 0, arrivalTime = 100,
            routeName = null, isTransfer = true, legType = LegType.WALK_ACCESS
        )
        assertTrue(mapLibraryJourneys(listOf(listOf(broken)), stops).isEmpty())
    }

    @Test
    fun legacyCacheJsonWithoutLegKindDecodesWithDefault() {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val legacyWalking = """
            {"fromStopId":"1","fromStopName":"A","toStopId":"2","toStopName":"B",
             "departureTime":100,"arrivalTime":200,"routeName":null,"routeColor":null,
             "isWalking":true,"direction":null,"intermediateStops":[]}
        """.trimIndent()

        val walking = json.decodeFromString<SerializableJourneyLeg>(legacyWalking)
        assertEquals(JourneyLegKind.TRANSFER, walking.legKind)

        val legacyTransit = legacyWalking
            .replace("\"isWalking\":true", "\"isWalking\":false")
            .replace("\"routeName\":null", "\"routeName\":\"C3\"")
        assertEquals(JourneyLegKind.TRANSIT, json.decodeFromString<SerializableJourneyLeg>(legacyTransit).legKind)

        // Round trip: an explicit legKind survives encode/decode
        val withKind = SerializableJourneyLeg.fromJourneyLeg(
            walking.toJourneyLeg().copy(legKind = JourneyLegKind.WALK_ACCESS)
        )
        val encoded = json.encodeToString(SerializableJourneyLeg.serializer(), withKind)
        assertEquals(JourneyLegKind.WALK_ACCESS, json.decodeFromString<SerializableJourneyLeg>(encoded).legKind)
    }
}
