package eu.dotshell.pelo

import eu.dotshell.pelo.generic.data.config.AppConfig
import eu.dotshell.pelo.generic.utils.graphics.LineIconResolver
import eu.dotshell.pelo.specific.data.local.LyonLinesParser
import io.raptor.PeriodData
import io.raptor.RaptorLibrary
import io.raptor.data.NetworkLoader
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Headless end-to-end checks of the bundled TCL data and configuration:
 * the raptor network loads, a real journey (Perrache → Charpennes) is
 * computable, every line has its pictogram (or is a known fallback), and
 * config.json deserializes into the AppConfig schema with the TCL values.
 */
class PeloSmokeTest {

    private fun asset(path: String): File {
        val candidates = listOf(
            "src/commonMain/composeResources/files/$path",
            "app/src/commonMain/composeResources/files/$path"
        )
        return candidates.map(::File).firstOrNull(File::exists)
            ?: error("asset $path not found from ${File(".").absolutePath}")
    }

    private fun weekdayLibrary() = RaptorLibrary(
        listOf(
            PeriodData(
                periodId = "school_on_weekdays",
                stopsBytes = asset("raptor/stops_school_on_weekdays.bin").readBytes(),
                routesBytes = asset("raptor/routes_school_on_weekdays.bin").readBytes()
            )
        )
    )

    // ─── Routing ─────────────────────────────────────────────────────────────

    @Test
    fun raptorComputesPerracheToCharpennes() {
        val library = weekdayLibrary()

        val allStops = library.searchStopsByName("")
        assertTrue("weekday period carries the full network (got ${allStops.size} stops)", allStops.size > 5000)

        val origins = allStops.filter { it.name.startsWith("Perrache", true) }
        val destinations = allStops.filter { it.name.startsWith("Charpennes", true) }
        assertTrue("Perrache stops found", origins.isNotEmpty())
        assertTrue("Charpennes stops found", destinations.isNotEmpty())

        val journeys = library.getOptimizedPaths(
            originStopIds = origins.map { it.id },
            destinationStopIds = destinations.map { it.id },
            departureTime = 9 * 3600
        )
        assertTrue("at least one journey found", journeys.isNotEmpty())

        val best = journeys.first()
        val duration = best.last().arrivalTime - best.first().departureTime
        assertTrue("journey shorter than 30 min (got ${duration / 60} min)", duration < 30 * 60)
        assertTrue(
            "one journey rides the metro A or B",
            journeys.any { journey -> journey.any { leg -> !leg.isTransfer && leg.routeName in setOf("A", "B") } }
        )
    }

    @Test
    fun bundledStopsCarryTclFareZones() {
        // The RST3 stops binary carries a per-stop TCL fare zone (1..5 or "Zone Externe"),
        // threaded through to the line-details bracket UI. Guard the domain and coverage.
        val stops = NetworkLoader.loadStops(asset("raptor/stops_school_on_weekdays.bin").readBytes())
        assertTrue("full network loaded (got ${stops.size})", stops.size > 5000)

        val validZones = setOf("1", "2", "3", "4", "5", "Zone Externe")
        val zoned = stops.count { it.zone != null }
        stops.forEach { stop ->
            val z = stop.zone
            assertTrue("stop ${stop.id} has an unexpected zone '$z'", z == null || z in validZones)
        }
        assertTrue(
            "at least 90% of stops carry a fare zone (got $zoned/${stops.size})",
            zoned >= stops.size * 9 / 10
        )
    }

    @Test
    fun raptorRoutesFromCoordinatePointWithWalkLegs() {
        // raptor-kmp 1.8: itineraries from an arbitrary coordinate (geocoded address / GPS).
        // Walk legs use stop index -1 and must carry both endpoint coordinates.
        val library = weekdayLibrary()
        val destinations = library.searchStopsByName("").filter { it.name.startsWith("Charpennes", true) }
        assertTrue("Charpennes stops found", destinations.isNotEmpty())

        val journeys = library.getOptimizedPaths(
            origin = io.raptor.Location.Point(45.7578, 4.8320), // Place Bellecour
            destination = io.raptor.Location.StopIds(destinations.map { it.id }),
            departureTime = 9 * 3600
        )
        assertTrue("at least one journey from the coordinate", journeys.isNotEmpty())

        val transitJourney = journeys.first { legs -> legs.any { !it.isTransfer } }
        val access = transitJourney.first()
        assertEquals(io.raptor.core.LegType.WALK_ACCESS, access.legType)
        assertEquals(-1, access.fromStopIndex)
        assertTrue(
            "the walk leg carries both endpoint coordinates",
            access.fromLat != null && access.fromLon != null && access.toLat != null && access.toLon != null
        )
        assertTrue("the ride boards a real stop", transitJourney.first { !it.isTransfer }.fromStopIndex >= 0)
    }

    @Test
    fun blockedRouteNamesExcludeTheMetro() {
        val library = weekdayLibrary()
        val allStops = library.searchStopsByName("")
        val origins = allStops.filter { it.name.startsWith("Perrache", true) }
        val destinations = allStops.filter { it.name.startsWith("Charpennes", true) }

        val blocked = setOf("A", "B")
        val journeys = library.getOptimizedPaths(
            originStopIds = origins.map { it.id },
            destinationStopIds = destinations.map { it.id },
            departureTime = 9 * 3600,
            blockedRouteNames = blocked
        )
        assertTrue("journeys still exist with the metro blocked (e.g. via T1)", journeys.isNotEmpty())
        assertTrue(
            "no journey uses a blocked line",
            journeys.none { journey -> journey.any { !it.isTransfer && it.routeName in blocked } }
        )
    }

    @Test
    fun jdFamilyMustBeExpandedIntoRealRouteNames() {
        // The raptor RouteFilter matches route names EXACTLY — blocking the
        // literal "JD*" is a no-op. The Junior Direct toggle therefore expands
        // the family from the real network route names; this pins the premise.
        val routeNames = io.raptor.data.NetworkLoader
            .loadRoutes(asset("raptor/routes_school_on_weekdays.bin").readBytes())
            .map { it.name }
            .toSet()
        val jdLines = routeNames.filter { it.startsWith("JD", ignoreCase = true) }
        assertTrue("the weekday network carries JD school lines (got none)", jdLines.isNotEmpty())
        assertTrue("\"JD*\" is not a real route name", "JD*" !in routeNames)
    }

    // ─── Icons ───────────────────────────────────────────────────────────────

    @Test
    fun everyStructuringLineHasItsPictogram() {
        // The TCL icon set trails the GTFS (JD*/EX/seasonal bus variants have no
        // dedicated pictogram and render as colored fallback badges), so bus
        // coverage is best-effort. The structuring lines however must all have
        // their official pictogram.
        val structuring = setOf("A", "B", "C", "D", "F1", "F2", "RX") + (1..7).map { "T$it" }
        val drawableDirs = listOf(
            "src/commonMain/composeResources/drawable",
            "app/src/commonMain/composeResources/drawable"
        )
        val drawableDir = drawableDirs.map(::File).firstOrNull(File::isDirectory)
            ?: error("drawable dir not found")

        fun hasIcon(name: String) =
            File(drawableDir, LineIconResolver.getDrawableNameForLineName(name) + ".xml").exists()

        val missingCore = structuring.filterNot(::hasIcon)
        assertTrue("structuring lines without pictogram: $missingCore", missingCore.isEmpty())

        val parsed = LyonLinesParser.parse(asset("lyon/lines.bin").readBytes())
        assertTrue("the RLN2 file carries the full network (got ${parsed.size} lines)", parsed.size > 900)
        val allNames = parsed.map { it.name }.distinct().filter { it.isNotBlank() }
        val covered = allNames.count(::hasIcon)
        assertTrue(
            "most lines keep a pictogram ($covered/${allNames.size} covered)",
            covered * 100 >= allNames.size * 70
        )
    }

    // ─── Configuration ───────────────────────────────────────────────────────

    @Test
    fun configJsonMatchesTheTclSchema() {
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
        val config = json.decodeFromString<AppConfig>(asset("config.json").readText())

        assertEquals("Réseau de transport", config.transport.networkName)
        assertEquals(4, config.transport.regionBounds.size)
        assertEquals(11, config.lineColors.rules.size)
        assertTrue("A is a strong line", "A" in config.rules.strongLines)
        assertTrue("RX is a strong line", "RX" in config.rules.strongLines)
        // No realtime{} block in the TCL config: every capability stays enabled by default
        assertTrue(config.realtime.trafficAlertsEnabled)
        assertTrue(config.realtime.vehiclePositionsEnabled)
        assertTrue(config.realtime.userStopAlertsEnabled)
        // The SSE backend needs no line-id mapping nor speed baseline
        assertTrue(config.transport.realtimeLineIds.isEmpty())
        assertTrue(config.transport.vehicleSpeedBaseline.isEmpty())
        assertEquals("lyon-tcl", config.telemetry?.networkCode)

        // Every regex in the rules must compile (a bad one would crash at startup)
        (config.rules.strongLineRegexes + config.rules.lineNameRegexes +
            config.rules.transportTypes.map { it.regex }).forEach { Regex(it) }
    }
}
