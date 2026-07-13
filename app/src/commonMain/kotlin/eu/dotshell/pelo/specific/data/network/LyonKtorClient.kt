package eu.dotshell.pelo.specific.data.network

import eu.dotshell.pelo.generic.data.models.geojson.Feature
import eu.dotshell.pelo.generic.data.models.geojson.FeatureCollection
import eu.dotshell.pelo.generic.data.models.geojson.StopCollection
import eu.dotshell.pelo.generic.data.models.geojson.StopFeature
import eu.dotshell.pelo.generic.data.models.stops.StopGeometry
import eu.dotshell.pelo.generic.data.models.stops.StopProperties
import eu.dotshell.pelo.generic.data.models.lines.MultiLineStringGeometry
import eu.dotshell.pelo.generic.data.models.lines.TransportLineProperties
import eu.dotshell.pelo.generic.data.models.realtime.alerts.community.UserStopAlertsResponse
import eu.dotshell.pelo.generic.data.models.realtime.alerts.official.TrafficAlertsResponse
import eu.dotshell.pelo.generic.data.network.transport.TransportApi
import eu.dotshell.pelo.generic.data.network.transport.TransportLinesQuery
import eu.dotshell.pelo.platform.Log
import eu.dotshell.pelo.platform.createHttpClientEngine
import eu.dotshell.pelo.specific.data.mapper.StopMapper
import eu.dotshell.pelo.specific.data.mapper.TrafficAlertMapper
import eu.dotshell.pelo.specific.data.mapper.TransportLineMapper
import eu.dotshell.pelo.specific.data.model.LyonFeatureCollection
import eu.dotshell.pelo.specific.data.model.LyonStopCollection
import eu.dotshell.pelo.specific.data.model.LyonTrafficAlertsResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.raptor.data.NetworkLoader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs

import eu.dotshell.pelo.platform.FileSystem

private const val TAG = "LyonKtorClient"

/**
 * Lyon-specific implementation of [TransportApi] using Ktor (KMP-compatible).
 * Replaces Retrofit + OkHttp + Gson. Uses kotlinx.serialization for JSON parsing.
 *
 * Handles:
 * - WFS GeoJSON line geometries (metro, tram, bus, navigone, trambus, RX) via local parsing of lines.bin
 * - WFS GeoJSON transport stops
 * - Traffic alerts (Pelo API)
 * - User stop alerts (karma-based)
 */
class LyonKtorClient(
    private val baseUrl: String,
    private val fileSystem: FileSystem
) : TransportApi, eu.dotshell.pelo.generic.data.network.UserStopAlertsApi {

    private val allLinesCache: FeatureCollection by lazy {
        Log.d(TAG, "Loading lines.bin from composeResources...")
        val bytes = fileSystem.readAssetBytes("lyon/lines.bin")
        Log.d(TAG, "lines.bin size: ${bytes.size} bytes")
        val lines = eu.dotshell.pelo.specific.data.local.LyonLinesParser.parse(bytes)
        val features = lines.flatMap { line ->
            line.paths.mapIndexed { index, path -> line.toFeature(index, path) }
        }
        Log.d(TAG, "Parsed ${lines.size} lines (${features.size} trace variants) from lines.bin")
        FeatureCollection(
            type = "FeatureCollection",
            features = features,
            totalFeatures = features.size,
            numberMatched = features.size,
            numberReturned = features.size
        )
    }

    private fun eu.dotshell.pelo.specific.data.local.LyonLine.toFeature(
        pathIndex: Int,
        path: eu.dotshell.pelo.specific.data.local.LyonLinePath
    ): Feature {
        // Historical WFS type codes, preserved because every filter below
        // (and the strong/bus splits) matches on them.
        val typeCode = when {
            name.equals("RX", ignoreCase = true) -> "TRAM"
            gtfsRouteType == 1 -> "MET"
            gtfsRouteType == 0 -> "TRA"
            gtfsRouteType == 7 -> "FUN"
            gtfsRouteType == 4 -> "BAT"
            else -> "BUS"
        }
        return Feature(
            type = "Feature",
            id = "lyon_${name}_$pathIndex",
            multiLineStringGeometry = MultiLineStringGeometry(
                type = "MultiLineString",
                coordinates = listOf(path.points)
            ),
            geometryName = null,
            properties = TransportLineProperties(
                lineName = name,
                traceCode = "$name-$pathIndex",
                lineId = name,
                traceType = typeCode,
                traceName = name,
                direction = if (path.directionId == 0) "Aller" else "Retour",
                transportType = typeCode,
                lineTypeCode = typeCode,
                lineTypeName = when (typeCode) {
                    "MET" -> "Métro"
                    "TRA", "TRAM" -> "Tramway"
                    "FUN" -> "Funiculaire"
                    "BAT" -> "Navette fluviale"
                    else -> "Bus"
                },
                gid = idInternal,
                color = colorHex.takeIf { it.isNotBlank() }?.let { "#$it" }
            ),
            bbox = null
        )
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val stopsMutex = Mutex()
    private var cachedStops: StopCollection? = null

    private val httpClient = HttpClient(createHttpClientEngine()) {
        install(ContentNegotiation) {
            json(json)
        }
        install(Logging) {
            level = LogLevel.NONE
            logger = object : Logger {
                override fun log(message: String) {
                    Log.d(TAG, message)
                }
            }
        }
    }

    // ─── TransportApi ────────────────────────────────────────────────────────

    override suspend fun getLines(query: TransportLinesQuery): FeatureCollection {
        return when (query) {
            is TransportLinesQuery.StrongLines -> fetchStrongLines()
            is TransportLinesQuery.LineByName  -> fetchLineByName(query.lineName)
            is TransportLinesQuery.BusPage     -> fetchBusPage(query.startIndex, query.count)
        }
    }

    override suspend fun getTransportStops(): StopCollection {
        return cachedStops ?: stopsMutex.withLock {
            cachedStops ?: buildStops().also { cachedStops = it }
        }
    }

    private suspend fun buildStops(): StopCollection = withContext(eu.dotshell.pelo.platform.ioDispatcher) {
        val stops = NetworkLoader.loadStops(fileSystem.readAssetBytes("raptor/stops_school_on_weekdays.bin"))
        val routes = NetworkLoader.loadRoutes(fileSystem.readAssetBytes("raptor/routes_school_on_weekdays.bin"))
        val routesById = routes.groupBy { it.id }
        Log.i(TAG, "Built stop collection: ${stops.size} stops, ${routes.size} route variants from raptor bin")

        val features = stops.map { stop ->
            // Match same desserte convention
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
                id = "tcl_stop_${stop.id}",
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

    override suspend fun getTrafficAlerts(): TrafficAlertsResponse {
        val lyon = httpClient.get("${TRAFFIC_ALERTS_BASE_URL}pelo/v1/traffic/alerts")
            .body<LyonTrafficAlertsResponse>()
        return TrafficAlertMapper.mapResponseToGeneric(lyon)
    }

    // ─── User stop alerts (UserStopAlertsApi) ─────────────────────────────────

    override suspend fun getUserStopAlerts(stopIds: List<String>): UserStopAlertsResponse {
        if (stopIds.isEmpty()) return emptyMap()
        val timestampMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        return httpClient.get("${TRAFFIC_ALERTS_BASE_URL}pelo/v1/users-alerts/stops") {
            header(HttpHeaders.CacheControl, "no-cache")
            header("Pragma", "no-cache")
            stopIds.forEach { parameter("stopIds", it) }
            parameter("_ts", timestampMs)
        }.body()
    }

    // ─── Line fetching helpers ────────────────────────────────────────────────

    private fun fetchStrongLines(): FeatureCollection {
        val rules = eu.dotshell.pelo.generic.service.TransportServiceProvider.getTransportLineRules()
        val strongFeatures = allLinesCache.features.filter { feat ->
            val type = feat.properties.transportType
            val name = feat.properties.lineName
            val normalized = normalizeToken(name)
            type == "MET" || type == "FUN" || type == "TRA" || rules.isNavigoneLine(name) || name == "RX"
        }
        Log.d(TAG, "fetchStrongLines returning ${strongFeatures.size} features")
        return FeatureCollection(
            type = "FeatureCollection",
            features = strongFeatures,
            totalFeatures = strongFeatures.size,
            numberMatched = strongFeatures.size,
            numberReturned = strongFeatures.size
        )
    }

    private fun fetchLineByName(lineName: String): FeatureCollection {
        val rules = eu.dotshell.pelo.generic.service.TransportServiceProvider.getTransportLineRules()
        val normalized = normalizeToken(lineName)
        val isMetroRequest    = normalized in listOf("a", "b", "c", "d")
        val isTramRequest     = normalized.startsWith("t") && normalized.length <= 3 && !normalized.startsWith("tb")
        val isNavigoneRequest = normalized == "vaporetto" || normalized == "navigone" || rules.isNavigoneLine(lineName)

        val features = allLinesCache.features.filter { feat ->
            val featName = feat.properties.lineName
            val featType = feat.properties.transportType
            when {
                lineName == "RX" -> featName == "RX"
                isMetroRequest -> (featType == "MET" || featType == "FUN") && normalizeToken(featName) == normalized
                isTramRequest -> featType == "TRA" && normalizeToken(featName) == normalized
                isNavigoneRequest -> (featType == "BAT" || rules.normalizeForComparison(featName) == rules.normalizeForComparison(lineName)) || (normalized == "vaporetto" && featName == "Vaporetto")
                else -> {
                    val canonical = rules.canonicalRouteName(lineName)
                    val escapedAlias = normalizeToken(canonical).replace("'", "''")
                    normalizeToken(featName).replace("'", "''") == escapedAlias
                }
            }
        }

        val unique = features
            .groupBy { it.properties.traceCode }
            .map { (_, group) -> group.first() }

        Log.d(TAG, "fetchLineByName($lineName) returning ${unique.size} features")

        return FeatureCollection(
            type = "FeatureCollection",
            features = unique,
            totalFeatures = unique.size,
            numberMatched = unique.size,
            numberReturned = unique.size
        )
    }

    private fun fetchBusPage(startIndex: Int, count: Int): FeatureCollection {
        val busFeatures = allLinesCache.features.filter { it.properties.transportType == "BUS" }
            .drop(startIndex)
            .take(count)
        return FeatureCollection(
            type = "FeatureCollection",
            features = busFeatures,
            totalFeatures = busFeatures.size,
            numberMatched = busFeatures.size,
            numberReturned = busFeatures.size
        )
    }

    // ─── WFS GeoJSON helpers ──────────────────────────────────────────────────



    /** Generic WFS GET → deserialize into [T]. */
    private suspend inline fun <reified T> wfsGet(
        baseUrl: String,
        typename: String,
        srsName: String,
        count: Int,
        startIndex: Int = 0,
        cqlFilter: String? = null
    ): T {
        return httpClient.get("$baseUrl/geoserver/sytral/ows") {
            parameter("SERVICE", SERVICE)
            parameter("VERSION", VERSION)
            parameter("request", REQUEST)
            parameter("typename", typename)
            parameter("outputFormat", OUTPUT_FORMAT)
            parameter("SRSNAME", srsName)
            parameter("startIndex", startIndex)
            parameter("sortby", SORT_BY)
            parameter("count", count)
            if (cqlFilter != null) parameter("cql_filter", cqlFilter)
        }.body()
    }



    // ─── Text normalization ───────────────────────────────────────────────────

    private fun normalizeToken(token: String): String =
        token.lowercase().trim().let { s ->
            // Remove accents via basic ASCII folding (KMP-safe, no java.text.Normalizer)
            buildString {
                for (c in s) {
                    append(ACCENT_MAP[c] ?: c)
                }
            }
        }

    companion object {
        private const val TRAFFIC_ALERTS_BASE_URL = "https://api.dotshell.eu/"

        private const val SERVICE = "WFS"
        private const val VERSION = "2.0.0"
        private const val REQUEST = "GetFeature"
        private const val OUTPUT_FORMAT = "application/json"
        private const val SORT_BY = "gid"
        private const val START_INDEX = 0

        private const val SRSNAME_4171 = "EPSG:4171"
        private const val SRSNAME_4326 = "EPSG:4326"

        private const val COUNT_METRO_TRAM_NAVIGONE = 1000
        private const val COUNT_STOPS = 10000

        private const val TYPENAME_METRO    = "sytral:tcl_sytral.tcllignemf_2_0_0"
        private const val TYPENAME_TRAM     = "sytral:tcl_sytral.tcllignetram_2_0_0"
        private const val TYPENAME_BUS      = "sytral:tcl_sytral.tcllignebus_2_0_0"
        private const val TYPENAME_NAVIGONE = "sytral:tcl_sytral.tcllignefluv"
        private const val TYPENAME_STOPS    = "sytral:tcl_sytral.tclarret"
        private const val TYPENAME_RX_LINES = "sytral:rx_rhonexpress.rxligne_2_0_0"

        private const val COUNT_RX_LINES = 1000
        private const val BUS_LINE_BY_NAME_COUNT = 200

        /** Simple accent folding map — avoids java.text.Normalizer (not in KMP stdlib). */
        private val ACCENT_MAP = mapOf(
            'à' to 'a', 'â' to 'a', 'ä' to 'a', 'á' to 'a', 'ã' to 'a',
            'è' to 'e', 'ê' to 'e', 'ë' to 'e', 'é' to 'e',
            'î' to 'i', 'ï' to 'i', 'í' to 'i', 'ì' to 'i',
            'ô' to 'o', 'ö' to 'o', 'ó' to 'o', 'ò' to 'o', 'õ' to 'o',
            'ù' to 'u', 'û' to 'u', 'ü' to 'u', 'ú' to 'u',
            'ç' to 'c', 'ñ' to 'n'
        )
    }
}
