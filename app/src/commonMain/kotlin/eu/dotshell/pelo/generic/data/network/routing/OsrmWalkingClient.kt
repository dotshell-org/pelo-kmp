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
        httpClient.get("$BASE_URL$fromLon,$fromLat;$toLon,$toLat") {
            parameter("overview", "full")
            parameter("geometries", "geojson")
            parameter("steps", "false")
        }.body()

    companion object {
        private const val BASE_URL = "https://routing.openstreetmap.de/routed-foot/route/v1/foot/"
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
