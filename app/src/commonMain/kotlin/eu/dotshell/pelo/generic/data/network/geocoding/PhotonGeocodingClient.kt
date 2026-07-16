package eu.dotshell.pelo.generic.data.network.geocoding

import eu.dotshell.pelo.platform.createHttpClientEngine
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Thin client for the public Photon geocoder (OSM-based, no API key), used for
 * address/POI typeahead. Results are location-biased towards the network's area.
 * https://photon.komoot.io
 */
class PhotonGeocodingClient {

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

    suspend fun search(query: String, limit: Int = 8): PhotonResponse =
        httpClient.get(BASE_URL) {
            parameter("q", query)
            parameter("lang", "fr")
            parameter("limit", limit)
            parameter("lat", BIAS_LAT)
            parameter("lon", BIAS_LON)
        }.body()

    companion object {
        private const val BASE_URL = "https://photon.komoot.io/api/"

        // Location bias: results near Lyon rank first (hard filtering happens in the repository)
        private const val BIAS_LAT = 45.75
        private const val BIAS_LON = 4.85
    }
}

@Serializable
data class PhotonResponse(
    val features: List<PhotonFeature> = emptyList()
)

@Serializable
data class PhotonFeature(
    val geometry: PhotonGeometry = PhotonGeometry(),
    val properties: PhotonProperties = PhotonProperties()
)

@Serializable
data class PhotonGeometry(
    // GeoJSON order: [lon, lat]
    val coordinates: List<Double> = emptyList()
)

@Serializable
data class PhotonProperties(
    val name: String? = null,
    val housenumber: String? = null,
    val street: String? = null,
    val postcode: String? = null,
    val city: String? = null,
    @SerialName("osm_key") val osmKey: String? = null,
    @SerialName("osm_value") val osmValue: String? = null
)
