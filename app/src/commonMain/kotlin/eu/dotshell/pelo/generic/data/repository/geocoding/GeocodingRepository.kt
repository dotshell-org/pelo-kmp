package eu.dotshell.pelo.generic.data.repository.geocoding

import eu.dotshell.pelo.generic.data.models.search.AddressSearchResult
import eu.dotshell.pelo.generic.data.network.geocoding.PhotonFeature
import eu.dotshell.pelo.generic.data.network.geocoding.PhotonGeocodingClient
import eu.dotshell.pelo.platform.Log
import eu.dotshell.pelo.platform.ioDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.Volatile
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Address/POI search backed by the public Photon geocoder.
 *
 * Failures (offline, timeouts, rate limiting) degrade silently to an empty list: the address
 * section simply doesn't appear in the search UI while stop search keeps working. No retry —
 * for typeahead the next debounced keystroke supersedes a failed query anyway.
 */
class GeocodingRepository private constructor() {

    private val client = PhotonGeocodingClient()

    suspend fun searchAddresses(query: String, limit: Int = DEFAULT_LIMIT): List<AddressSearchResult> {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) return emptyList()

        return withContext(ioDispatcher) {
            try {
                withTimeout(REQUEST_TIMEOUT_MS) {
                    // Fetch a few extra: the region filter may drop some hits
                    val response = client.search(trimmed, limit = FETCH_LIMIT)
                    mapPhotonFeatures(response.features, limit)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Address search failed: ${e.message}")
                emptyList()
            }
        }
    }

    companion object {
        private const val TAG = "GeocodingRepository"
        private const val MIN_QUERY_LENGTH = 3
        private const val DEFAULT_LIMIT = 6
        private const val FETCH_LIMIT = 8
        private const val REQUEST_TIMEOUT_MS = 5_000L

        @Volatile
        private var INSTANCE: GeocodingRepository? = null

        fun getInstance(): GeocodingRepository {
            return INSTANCE ?: GeocodingRepository().also { INSTANCE = it }
        }
    }
}

// ─── Pure mapping helpers (unit-tested without HTTP) ─────────────────────────

private const val LYON_LAT = 45.7578
private const val LYON_LON = 4.8320
private const val MAX_DISTANCE_FROM_LYON_KM = 80.0
private const val KM_PER_DEGREE_LAT = 111.32

/**
 * Maps raw Photon features to display results: region-filtered, deduplicated,
 * Photon relevance order preserved.
 */
internal fun mapPhotonFeatures(features: List<PhotonFeature>, limit: Int): List<AddressSearchResult> =
    features.asSequence()
        .mapNotNull(::photonFeatureToResult)
        .distinctBy { "${it.label}|${it.detail}" }
        .take(limit)
        .toList()

/**
 * Builds one display result, or null when the feature is unusable (no coordinates, nothing
 * to label it with, or outside the network's region).
 * Label: POI name if present, else "housenumber street"; detail: the rest of the address.
 */
internal fun photonFeatureToResult(feature: PhotonFeature): AddressSearchResult? {
    val coordinates = feature.geometry.coordinates
    if (coordinates.size < 2) return null
    val lon = coordinates[0]
    val lat = coordinates[1]
    if (!isNearLyon(lat, lon)) return null

    val properties = feature.properties
    val streetLine = listOfNotNull(properties.housenumber, properties.street)
        .joinToString(" ").ifBlank { null }
    val label = properties.name ?: streetLine ?: return null
    val cityLine = listOfNotNull(properties.postcode, properties.city)
        .joinToString(" ").ifBlank { null }
    val detail = listOfNotNull(streetLine.takeIf { properties.name != null }, cityLine)
        .joinToString(", ").ifBlank { null }

    return AddressSearchResult(label = label, detail = detail, lat = lat, lon = lon)
}

/**
 * Keeps results within reach of the transit network (equirectangular approximation,
 * same cos-scaling as RaptorRepository.findNearestStops).
 */
internal fun isNearLyon(lat: Double, lon: Double): Boolean {
    val dLat = (lat - LYON_LAT) * KM_PER_DEGREE_LAT
    val dLon = (lon - LYON_LON) * KM_PER_DEGREE_LAT * cos(LYON_LAT * PI / 180.0)
    return sqrt(dLat * dLat + dLon * dLon) <= MAX_DISTANCE_FROM_LYON_KM
}
