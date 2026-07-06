package eu.dotshell.massilia.specific.data.network

import eu.dotshell.massilia.generic.data.models.geojson.Feature
import eu.dotshell.massilia.generic.data.models.geojson.FeatureCollection
import eu.dotshell.massilia.generic.data.models.geojson.StopCollection
import eu.dotshell.massilia.generic.data.models.geojson.StopFeature
import eu.dotshell.massilia.generic.data.models.lines.MultiLineStringGeometry
import eu.dotshell.massilia.generic.data.models.lines.TransportLineProperties
import eu.dotshell.massilia.generic.data.models.realtime.alerts.official.TrafficAlertsResponse
import eu.dotshell.massilia.generic.data.models.stops.StopGeometry
import eu.dotshell.massilia.generic.data.models.stops.StopProperties
import eu.dotshell.massilia.generic.data.network.transport.TransportApi
import eu.dotshell.massilia.generic.data.network.transport.TransportLinesQuery
import eu.dotshell.massilia.generic.service.TransportServiceProvider
import eu.dotshell.massilia.platform.FileSystem
import eu.dotshell.massilia.platform.Log
import eu.dotshell.massilia.platform.PlatformContext
import eu.dotshell.massilia.platform.ioDispatcher
import eu.dotshell.massilia.specific.data.local.RtmLine
import eu.dotshell.massilia.specific.data.local.RtmLinesParser
import io.raptor.data.NetworkLoader
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "RtmLocalClient"

/**
 * Marseille RTM implementation of [TransportApi], fully backed by bundled assets —
 * no network calls.
 *
 * - Line geometries and colors come from `raptor/lines.bin` (RLN2, from the RTM GTFS shapes).
 * - Stops come from the weekday raptor bins; the "desserte" string is synthesized in the
 *   same `"<line>:A,<line>:R"` format that [eu.dotshell.massilia.generic.data.repository.itinerary.itinerary.RaptorRepository.getDesserteForStop]
 *   produces, so [eu.dotshell.massilia.generic.utils.graphics.LineIconResolver.parseDesserte] works unchanged.
 * - Traffic alerts have no RTM backend: the response is always empty.
 */
class RtmLocalClient(context: PlatformContext) : TransportApi {

    private val fileSystem = FileSystem(context)
    private val mutex = Mutex()

    @Volatile
    private var cachedLineFeatures: List<Feature>? = null

    @Volatile
    private var cachedStops: StopCollection? = null

    // ─── TransportApi ────────────────────────────────────────────────────────

    override suspend fun getLines(query: TransportLinesQuery): FeatureCollection {
        val features = lineFeatures()
        val rules = TransportServiceProvider.getTransportLineRules()

        val selected: List<Feature> = when (query) {
            is TransportLinesQuery.StrongLines ->
                features.filter { rules.isStrongLine(it.properties.lineName) }

            is TransportLinesQuery.LineByName -> {
                val requested = query.lineName.trim()
                val normalized = requested.lowercase()
                if (normalized == "navigone" || normalized == "vaporetto" || rules.isNavigoneLine(requested)) {
                    features.filter { it.properties.transportType == TYPE_NAVIGONE }
                } else {
                    val target = rules.normalizeForComparison(rules.canonicalRouteName(requested))
                    features.filter { rules.normalizeForComparison(it.properties.lineName) == target }
                }
            }

            is TransportLinesQuery.BusPage -> {
                features
                    .filter { !rules.isStrongLine(it.properties.lineName) }
                    .sortedBy { it.properties.lineName }
                    .drop(query.startIndex)
                    .take(query.count)
            }
        }

        return FeatureCollection(
            type = "FeatureCollection",
            features = selected,
            totalFeatures = selected.size,
            numberMatched = selected.size,
            numberReturned = selected.size
        )
    }

    override suspend fun getTransportStops(): StopCollection {
        return cachedStops ?: mutex.withLock {
            cachedStops ?: buildStops().also { cachedStops = it }
        }
    }

    override suspend fun getTrafficAlerts(): TrafficAlertsResponse {
        // No RTM real-time backend: always empty.
        return TrafficAlertsResponse(
            success = true,
            alerts = emptyList(),
            timestamp = "",
            lastUpdated = ""
        )
    }

    // ─── Lines from lines.bin ─────────────────────────────────────────────────

    private suspend fun lineFeatures(): List<Feature> {
        return cachedLineFeatures ?: mutex.withLock {
            cachedLineFeatures ?: buildLineFeatures().also { cachedLineFeatures = it }
        }
    }

    private suspend fun buildLineFeatures(): List<Feature> = withContext(ioDispatcher) {
        val lines = RtmLinesParser.parse(fileSystem.readAssetBytes(LINES_ASSET))
        Log.i(TAG, "Parsed ${lines.size} lines from $LINES_ASSET")
        lines.flatMap { line -> line.paths.mapIndexed { index, path -> line.toFeature(index, path.points, path.directionId) } }
    }

    private fun RtmLine.toFeature(pathIndex: Int, points: List<List<Double>>, directionId: Int): Feature {
        val type = transportTypeOf(gtfsRouteType)
        return Feature(
            type = "Feature",
            id = "rtm_${name}_$pathIndex",
            multiLineStringGeometry = MultiLineStringGeometry(
                type = "MultiLineString",
                coordinates = listOf(points)
            ),
            geometryName = null,
            properties = TransportLineProperties(
                lineName = name,
                traceCode = "$name-$pathIndex",
                lineId = name,
                traceType = type,
                traceName = name,
                direction = if (directionId == 0) "ALLER" else "RETOUR",
                transportType = type,
                lineTypeCode = type,
                lineTypeName = lineTypeNameOf(type),
                gid = idInternal,
                color = colorHex.takeIf { it.isNotBlank() }?.let { "#$it" }
            ),
            bbox = null
        )
    }

    // ─── Stops from the raptor bins ───────────────────────────────────────────

    private suspend fun buildStops(): StopCollection = withContext(ioDispatcher) {
        val stops = NetworkLoader.loadStops(fileSystem.readAssetBytes(STOPS_ASSET))
        val routes = NetworkLoader.loadRoutes(fileSystem.readAssetBytes(ROUTES_ASSET))
        val routesById = routes.groupBy { it.id }
        Log.i(TAG, "Built stop collection: ${stops.size} stops, ${routes.size} route variants")

        val features = stops.map { stop ->
            // Same variant→direction convention as RaptorRepository.getDesserteForStop:
            // first distinct stop pattern of a route id is "A" (aller), the others "R".
            val desserte = stop.routeIds
                .flatMap { routeId ->
                    routesById[routeId].orEmpty()
                        .distinctBy { it.stopIds.joinToString(",") }
                        .mapIndexed { index, route -> "${route.name}:${if (index == 0) "A" else "R"}" }
                }
                .distinct()
                .joinToString(",")

            StopFeature(
                type = "Feature",
                id = "rtm_stop_${stop.id}",
                geometry = StopGeometry(
                    type = "Point",
                    coordinates = listOf(stop.lon, stop.lat)
                ),
                properties = StopProperties(
                    id = stop.id,
                    nom = stop.name,
                    desserte = desserte,
                    gid = stop.id
                )
            )
        }

        StopCollection(
            type = "FeatureCollection",
            features = features,
            totalFeatures = features.size,
            numberMatched = features.size,
            numberReturned = features.size
        )
    }

    companion object {
        private const val LINES_ASSET = "raptor/lines.bin"

        // The weekday period is a superset of stops/lines for stop discovery.
        private const val STOPS_ASSET = "raptor/stops_school_on_weekdays.bin"
        private const val ROUTES_ASSET = "raptor/routes_school_on_weekdays.bin"

        private const val TYPE_METRO = "METRO"
        private const val TYPE_TRAM = "TRAM"
        private const val TYPE_NAVIGONE = "NAVIGONE"
        private const val TYPE_BUS = "BUS"

        /** Maps a raw GTFS route_type to the transportType strings the generic layer filters on. */
        private fun transportTypeOf(gtfsRouteType: Int): String = when (gtfsRouteType) {
            1 -> TYPE_METRO
            0 -> TYPE_TRAM
            4 -> TYPE_NAVIGONE
            else -> TYPE_BUS
        }

        private fun lineTypeNameOf(type: String): String = when (type) {
            TYPE_METRO -> "Métro"
            TYPE_TRAM -> "Tramway"
            TYPE_NAVIGONE -> "Navette maritime"
            else -> "Bus"
        }
    }
}
