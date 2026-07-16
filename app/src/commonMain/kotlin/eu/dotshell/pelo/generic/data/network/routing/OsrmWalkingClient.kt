package eu.dotshell.pelo.generic.data.network.routing

import eu.dotshell.pelo.platform.createHttpClientEngine
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Thin client for the public OSRM pedestrian router (FOSSGIS instance, the one behind
 * openstreetmap.org), used to draw walk legs along the actual street network.
 * https://routing.openstreetmap.de
 */
class OsrmWalkingClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val httpClient = HttpClient(createHttpClientEngine()) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    suspend fun route(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): OsrmRouteResponse =
        httpClient.get("$ROUTE_URL$fromLon,$fromLat;$toLon,$toLat") {
            parameter("overview", "full")
            parameter("geometries", "geojson")
            parameter("steps", "false")
        }.body()

    /**
     * One-to-many street distances (meters) from a source point to [targets] (lat/lon pairs),
     * in a single request via the OSRM table service.
     */
    suspend fun table(fromLat: Double, fromLon: Double, targets: List<Pair<Double, Double>>): OsrmTableResponse {
        val coords = buildString {
            append(fromLon).append(',').append(fromLat)
            for ((lat, lon) in targets) {
                append(';').append(lon).append(',').append(lat)
            }
        }
        return httpClient.get("$TABLE_URL$coords") {
            parameter("sources", "0")
            parameter("annotations", "distance")
        }.body()
    }

    companion object {
        private const val ROUTE_URL = "https://routing.openstreetmap.de/routed-foot/route/v1/foot/"
        private const val TABLE_URL = "https://routing.openstreetmap.de/routed-foot/table/v1/foot/"
    }
}

@Serializable
data class OsrmRouteResponse(
    val code: String? = null,
    val routes: List<OsrmRoute> = emptyList()
)

@Serializable
data class OsrmRoute(
    val geometry: OsrmGeometry = OsrmGeometry(),
    val distance: Double = 0.0,
    val duration: Double = 0.0
)

@Serializable
data class OsrmGeometry(
    // GeoJSON order: [lon, lat]
    val coordinates: List<List<Double>> = emptyList()
)

@Serializable
data class OsrmTableResponse(
    val code: String? = null,
    // distances[0] = row for the single source: [0.0, dToTarget1, dToTarget2, ...];
    // null entries mean the target is unreachable on foot
    val distances: List<List<Double?>> = emptyList()
)
