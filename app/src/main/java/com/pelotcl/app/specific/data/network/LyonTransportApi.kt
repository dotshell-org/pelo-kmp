package com.pelotcl.app.specific.data.network

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonElement
import com.pelotcl.app.generic.data.models.geojson.FeatureCollection
import com.pelotcl.app.generic.data.models.geojson.Feature
import com.pelotcl.app.generic.data.models.geojson.StopCollection
import com.pelotcl.app.generic.data.models.realtime.alerts.official.TrafficAlertsResponse
import com.pelotcl.app.generic.data.models.lines.MultiLineStringGeometry
import com.pelotcl.app.generic.data.models.lines.TransportLineProperties
import com.pelotcl.app.generic.data.models.realtime.alerts.community.UserStopAlertsResponse
import com.pelotcl.app.generic.data.network.RetrofitInstance
import com.pelotcl.app.generic.data.network.transport.TransportApi
import com.pelotcl.app.generic.data.network.transport.TransportLinesQuery
import com.pelotcl.app.specific.data.mapper.TrafficAlertMapper
import com.pelotcl.app.specific.data.model.LyonTrafficAlertsResponse
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query
import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import java.text.Normalizer

/**
 * Lyon-specific implementation of TransportApi
 * Maps traffic alerts from Lyon payloads; WFS line/stop requests use [LyonTransportLineApi]
 * + [LyonTransportLineApiWrapper] so GeoJSON properties match the Lyon field names.
 */
class LyonTransportApi(private val baseUrl: String) : TransportApi {

    interface LyonTrafficAlertsEndpoint {
        @GET("pelo/v1/traffic/alerts")
        suspend fun getLyonTrafficAlerts(): LyonTrafficAlertsResponse
    }

    interface UserStopAlertsEndpoint {
        @Headers("Cache-Control: no-cache", "Pragma: no-cache")
        @GET("pelo/v1/users-alerts/stops")
        suspend fun getUserStopAlerts(
            @Query("stopIds") stopIds: List<String>,
            @Query("_ts") timestampMs: Long
        ): UserStopAlertsResponse
    }

    /**
     * WFS / GeoJSON lines and stops use the city data host (e.g. Grand Lyon).
     * Traffic alerts are served from the Pelo API on api.dotshell.eu only.
     * Both share the cached OkHttpClient from RetrofitInstance for HTTP cache + connection pool.
     */
    private val linesRetrofit: Retrofit = run {
        val builder = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
        RetrofitInstance.getSharedClient()?.let { builder.client(it) }
        builder.build()
    }

    private val trafficAlertsRetrofit: Retrofit = run {
        val builder = Retrofit.Builder()
            .baseUrl(TRAFFIC_ALERTS_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
        RetrofitInstance.getSharedClient()?.let { builder.client(it) }
        builder.build()
    }

    private val lyonTrafficApi: LyonTrafficAlertsEndpoint =
        trafficAlertsRetrofit.create(LyonTrafficAlertsEndpoint::class.java)

    private val userStopAlertsApi: UserStopAlertsEndpoint =
        trafficAlertsRetrofit.create(UserStopAlertsEndpoint::class.java)

    private val lyonLineApi: LyonTransportLineApi =
        linesRetrofit.create(LyonTransportLineApi::class.java)

    private val lineApiWrapper = LyonTransportLineApiWrapper(lyonLineApi)

    override suspend fun getTrafficAlerts(): TrafficAlertsResponse {
        val lyonResponse = lyonTrafficApi.getLyonTrafficAlerts()
        return TrafficAlertMapper.mapResponseToGeneric(lyonResponse)
    }

    /**
     * Fetch user stop alerts (karma-based) for the specified stops
     * @param stopIds List of stop IDs to check for alerts
     * @return Map of stopId to StopAlertsStatus containing alerts
     */
    suspend fun getUserStopAlerts(stopIds: List<String>): UserStopAlertsResponse {
        return if (stopIds.isEmpty()) {
            emptyMap()
        } else {
            userStopAlertsApi.getUserStopAlerts(
                stopIds = stopIds,
                timestampMs = System.currentTimeMillis()
            )
        }
    }

    override suspend fun getLines(query: TransportLinesQuery): FeatureCollection {
        return when (query) {
            TransportLinesQuery.StrongLines -> fetchStrongLines()
            is TransportLinesQuery.LineByName -> fetchLineByName(query.lineName)
            is TransportLinesQuery.BusPage -> {
                lineApiWrapper.getBusLines(
                    SERVICE,
                    VERSION,
                    REQUEST,
                    TYPENAME_BUS,
                    OUTPUT_FORMAT,
                    SRSNAME_4171,
                    query.startIndex,
                    SORT_BY,
                    query.count,
                    null
                )
            }
        }
    }

    override suspend fun getTransportStops(): StopCollection {
        return lineApiWrapper.getTransportStops(
            SERVICE,
            VERSION,
            REQUEST,
            TYPENAME_STOPS,
            OUTPUT_FORMAT,
            SRSNAME_4171,
            START_INDEX,
            SORT_BY,
            COUNT_STOPS
        )
    }

    private suspend fun fetchStrongLines(): FeatureCollection = supervisorScope {
        val metroDeferred = async {
            runCatching {
                lineApiWrapper.getMetroLines(
                    SERVICE, VERSION, REQUEST, TYPENAME_METRO,
                    OUTPUT_FORMAT, SRSNAME_4171, START_INDEX, SORT_BY,
                    COUNT_METRO_TRAM_NAVIGONE
                ).features
            }.getOrElse { e ->
                Log.w("LyonTransportApi", "fetchStrongLines: metro failed: ${e.message}")
                emptyList()
            }
        }

        val tramDeferred = async {
            runCatching {
                lineApiWrapper.getTramLines(
                    SERVICE, VERSION, REQUEST, TYPENAME_TRAM,
                    OUTPUT_FORMAT, SRSNAME_4171, START_INDEX, SORT_BY,
                    COUNT_METRO_TRAM_NAVIGONE
                ).features
            }.getOrElse { e ->
                Log.w("LyonTransportApi", "fetchStrongLines: tram failed: ${e.message}")
                emptyList()
            }
        }

        val navigoneDeferred = async {
            runCatching {
                lineApiWrapper.getNavigoneLines(
                    SERVICE, VERSION, REQUEST, TYPENAME_NAVIGONE,
                    OUTPUT_FORMAT, SRSNAME_4171, START_INDEX, SORT_BY,
                    COUNT_METRO_TRAM_NAVIGONE
                ).features
            }.getOrElse { e ->
                Log.w("LyonTransportApi", "fetchStrongLines: navigone failed: ${e.message}")
                emptyList()
            }
        }

        val trambusDeferred = async {
            runCatching {
                lineApiWrapper.getTrambusLines(
                    SERVICE, VERSION, REQUEST, TYPENAME_BUS,
                    OUTPUT_FORMAT, SRSNAME_4171, START_INDEX, SORT_BY,
                    COUNT_TRAMBUS_LINES, TRAMBUS_CQL_FILTER
                ).features
            }.getOrElse { e ->
                Log.w("LyonTransportApi", "fetchStrongLines: trambus failed: ${e.message}")
                emptyList()
            }
        }

        val rxDeferred = async {
            runCatching { fetchRhonexpressFeatures() }.getOrElse { e ->
                Log.w("LyonTransportApi", "fetchStrongLines: rhonexpress failed: ${e.message}")
                emptyList()
            }
        }

        val allFeatures = metroDeferred.await() + tramDeferred.await() +
            navigoneDeferred.await() + trambusDeferred.await() + rxDeferred.await()

        if (allFeatures.isEmpty()) {
            throw IllegalStateException("All strong line requests failed")
        }

        val uniqueLines = allFeatures
            .groupBy { it.properties.traceCode }
            .map { (_, features) -> features.first() }

        FeatureCollection(
            type = "FeatureCollection",
            features = uniqueLines,
            totalFeatures = uniqueLines.size,
            numberMatched = uniqueLines.size,
            numberReturned = uniqueLines.size
        )
    }

    private suspend fun fetchLineByName(lineName: String): FeatureCollection {
        val requested = lineName.trim()
        if (requested.isEmpty()) return FeatureCollection(features = emptyList())

        fun normalizeToken(raw: String): String {
            return Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)
                .replace("\\p{Mn}+".toRegex(), "")
                .uppercase()
        }

        val normalized = normalizeToken(requested)

        // Alias handling (Rhônexpress).
        val isRxRequest = normalized == "RX" ||
            normalized.contains("RHONEXPRESS") ||
            normalized.contains("RHONEXPRES")
        val isMetroOrFunicularRequest = normalized in setOf("A", "B", "C", "D", "F1", "F2")
        val isTramRequest = normalized.matches(Regex("^T\\d{1,2}[A-Z]?$"))
        val isNavigoneRequest = normalized.startsWith("NAV")

        val features = when {
            isRxRequest -> fetchRhonexpressFeatures()
            isMetroOrFunicularRequest -> {
                lineApiWrapper.getMetroLines(
                    SERVICE,
                    VERSION,
                    REQUEST,
                    TYPENAME_METRO,
                    OUTPUT_FORMAT,
                    SRSNAME_4171,
                    START_INDEX,
                    SORT_BY,
                    COUNT_METRO_TRAM_NAVIGONE
                ).features.filter { normalizeToken(it.properties.lineName) == normalized }
            }
            isTramRequest -> {
                lineApiWrapper.getTramLines(
                    SERVICE,
                    VERSION,
                    REQUEST,
                    TYPENAME_TRAM,
                    OUTPUT_FORMAT,
                    SRSNAME_4171,
                    START_INDEX,
                    SORT_BY,
                    COUNT_METRO_TRAM_NAVIGONE
                ).features.filter { normalizeToken(it.properties.lineName) == normalized }
            }
            isNavigoneRequest -> {
                lineApiWrapper.getNavigoneLines(
                    SERVICE,
                    VERSION,
                    REQUEST,
                    TYPENAME_NAVIGONE,
                    OUTPUT_FORMAT,
                    SRSNAME_4171,
                    START_INDEX,
                    SORT_BY,
                    COUNT_METRO_TRAM_NAVIGONE
                ).features.filter { normalizeToken(it.properties.lineName) == normalized }
            }
            else -> {
                val escapedAlias = normalized.replace("'", "''")
                val cqlFilter = "ligne = '$escapedAlias'"
                lineApiWrapper.getBusLineByName(
                    SERVICE,
                    VERSION,
                    REQUEST,
                    TYPENAME_BUS,
                    OUTPUT_FORMAT,
                    SRSNAME_4171,
                    SORT_BY,
                    BUS_LINE_BY_NAME_COUNT,
                    cqlFilter
                ).features
            }
        }

        val unique = features
            .groupBy { it.properties.traceCode }
            .map { (_, group) -> group.first() }

        return FeatureCollection(
            type = "FeatureCollection",
            features = unique,
            totalFeatures = unique.size,
            numberMatched = unique.size,
            numberReturned = unique.size
        )
    }

    private suspend fun fetchRhonexpressFeatures(): List<Feature> {
        val raw = lyonLineApi.getSpecialLineRaw(
            SERVICE,
            VERSION,
            REQUEST,
            TYPENAME_RX_LINES,
            OUTPUT_FORMAT,
            SRSNAME_4326,
            START_INDEX,
            SORT_BY,
            COUNT_RX_LINES
        )
        return mapJsonToFeatures(raw)
    }

    private fun mapJsonToFeatures(json: JsonObject): List<Feature> {
        val featuresArray: JsonArray = when {
            json.has("features") -> json.getAsJsonArray("features")
            json.has("featureMember") -> json.getAsJsonArray("featureMember")
            json.has("featureMembers") -> json.getAsJsonArray("featureMembers")
            else -> return emptyList()
        }

        fun parsePosition(pos: JsonElement): List<Double>? {
            if (!pos.isJsonArray) return null
            val arr = pos.asJsonArray
            if (arr.size() < 2) return null
            return listOf(arr[0].asDouble, arr[1].asDouble)
        }

        fun parseCoordinatesAsMultiLines(coords: JsonElement): List<List<List<Double>>>? {
            if (!coords.isJsonArray) return null
            val outer = coords.asJsonArray
            if (outer.size() == 0) return null

            val first = outer[0]
            if (!first.isJsonArray) return null
            val firstArr = first.asJsonArray
            if (firstArr.size() == 0) return null
            val firstInner = firstArr[0]

            val isMultiLine = firstInner != null && firstInner.isJsonArray

            return if (isMultiLine) {
                val lines = outer.mapNotNull { lineEl ->
                    if (!lineEl.isJsonArray) return@mapNotNull null
                    val lineArr = lineEl.asJsonArray
                    val points = lineArr.mapNotNull { parsePosition(it) }
                    if (points.isEmpty()) null else points
                }
                if (lines.isEmpty()) null else lines
            } else {
                val points = outer.mapNotNull { parsePosition(it) }
                if (points.isEmpty()) null else listOf(points)
            }
        }

        val result = mutableListOf<Feature>()

        for (featureElement in featuresArray) {
            val featureObject = featureElement.asJsonObject
            val id = featureObject.get("id")?.asString ?: "rx-${System.nanoTime()}"

            val properties = featureObject.getAsJsonObject("properties") ?: featureObject
            val gid =
                properties.get("gid")?.asInt
                    ?: properties.get("GID")?.asInt
                    ?: kotlin.math.abs(id.hashCode())

            val geometryObject = featureObject
                .getAsJsonObject("geometry")
                ?: featureObject.getAsJsonObject("the_geom")
                ?: featureObject.getAsJsonObject("geom")
                ?: continue

            val coordinatesElement = geometryObject.get("coordinates") ?: continue
            val coordinates = parseCoordinatesAsMultiLines(coordinatesElement) ?: continue

            result.add(
                Feature(
                    type = "Feature",
                    id = "rx_$id",
                    multiLineStringGeometry = MultiLineStringGeometry(
                        type = "MultiLineString",
                        coordinates = coordinates
                    ),
                    geometryName = null,
                    properties = TransportLineProperties(
                        lineName = "RX",
                        traceCode = "RX-$gid",
                        lineId = "RX",
                        traceType = "",
                        traceName = "Rhônexpress",
                        direction = "ALLER",
                        origin = "Gare Part-Dieu Villette",
                        destination = "Aéroport St Exupéry -RX",
                        originName = "Gare Part-Dieu Villette",
                        destinationName = "Aéroport St Exupéry -RX",
                        transportType = "TRAM",
                        startDate = "",
                        endDate = null,
                        lineTypeCode = "TRAM",
                        lineTypeName = "Tramway",
                        lastUpdate = "",
                        lastUpdateFme = "",
                        gid = gid,
                        color = "#E30613"
                    ),
                    bbox = null
                )
            )
        }

        return result
    }

    companion object {
        /** Documented Pelo traffic alerts endpoint host; path is [LyonTrafficAlertsEndpoint]. */
        private const val TRAFFIC_ALERTS_BASE_URL = "https://api.dotshell.eu/"

        // Shared WFS request defaults for Lyon's Sytral GeoServer.
        private const val SERVICE = "WFS"
        private const val VERSION = "2.0.0"
        private const val REQUEST = "GetFeature"
        private const val OUTPUT_FORMAT = "application/json"
        private const val SORT_BY = "gid"
        private const val START_INDEX = 0

        private const val SRSNAME_4171 = "EPSG:4171"
        private const val SRSNAME_4326 = "EPSG:4326"

        private const val COUNT_METRO_TRAM_NAVIGONE = 1000
        private const val COUNT_TRAMBUS_LINES = 1000
        private const val COUNT_STOPS = 10000
        // Bus line pagination uses interface-provided `count` / `startIndex`.

        // Lyon specific layer typenames.
        private const val TYPENAME_METRO = "sytral:tcl_sytral.tcllignemf_2_0_0"
        private const val TYPENAME_TRAM = "sytral:tcl_sytral.tcllignetram_2_0_0"
        private const val TYPENAME_BUS = "sytral:tcl_sytral.tcllignebus_2_0_0"
        private const val TYPENAME_NAVIGONE = "sytral:tcl_sytral.tcllignefluv"
        private const val TYPENAME_STOPS = "sytral:tcl_sytral.tclarret"

        // Trambus subset filter.
        private const val TRAMBUS_CQL_FILTER = "ligne LIKE 'TB%'"

        // Rhônexpress (RX) typname and limits.
        private const val TYPENAME_RX_LINES = "sytral:rx_rhonexpress.rxligne_2_0_0"
        private const val COUNT_RX_LINES = 1000

        // Bus line lookup count for `LineByName`.
        private const val BUS_LINE_BY_NAME_COUNT = 200
    }
}
