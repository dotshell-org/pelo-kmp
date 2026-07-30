package eu.dotshell.pelo

import eu.dotshell.pelo.generic.data.network.geocoding.PhotonResponse
import eu.dotshell.pelo.generic.data.repository.geocoding.isNearLyon
import eu.dotshell.pelo.generic.data.repository.geocoding.mapPhotonFeatures
import eu.dotshell.pelo.generic.data.repository.geocoding.photonFeatureToResult
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Photon response mapping: GeoJSON [lon, lat] ordering, label/detail building,
 * region filtering and deduplication — on fixture JSON, no HTTP involved.
 */
class GeocodingRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    // A realistic Photon payload: a POI, a house-number address, an unusable feature,
    // an out-of-region hit (Paris) and a duplicate of the POI.
    private val fixture = """
    {
      "type": "FeatureCollection",
      "features": [
        {
          "type": "Feature",
          "geometry": { "type": "Point", "coordinates": [4.8570, 45.7610] },
          "properties": { "name": "Carrefour Part-Dieu", "street": "Rue du Dr Bouchut",
                          "postcode": "69003", "city": "Lyon", "osm_key": "shop", "osm_value": "supermarket" }
        },
        {
          "type": "Feature",
          "geometry": { "type": "Point", "coordinates": [4.8355, 45.7621] },
          "properties": { "housenumber": "12", "street": "Rue de la République",
                          "postcode": "69002", "city": "Lyon", "osm_key": "place", "osm_value": "house" }
        },
        {
          "type": "Feature",
          "geometry": { "type": "Point", "coordinates": [4.84, 45.75] },
          "properties": { "postcode": "69000", "city": "Lyon" }
        },
        {
          "type": "Feature",
          "geometry": { "type": "Point", "coordinates": [2.2945, 48.8584] },
          "properties": { "name": "Tour Eiffel", "city": "Paris" }
        },
        {
          "type": "Feature",
          "geometry": { "type": "Point", "coordinates": [4.8570, 45.7610] },
          "properties": { "name": "Carrefour Part-Dieu", "street": "Rue du Dr Bouchut",
                          "postcode": "69003", "city": "Lyon" }
        }
      ]
    }
    """.trimIndent()

    @Test
    fun mapsFixtureFilteringAndDeduplicating() {
        val response = json.decodeFromString<PhotonResponse>(fixture)
        assertEquals(5, response.features.size)

        val results = mapPhotonFeatures(response.features, limit = 6)

        // Unusable + Paris + duplicate dropped, Photon order preserved
        assertEquals(2, results.size)

        val poi = results[0]
        assertEquals("Carrefour Part-Dieu", poi.label)
        assertEquals("Rue du Dr Bouchut, 69003 Lyon", poi.detail)
        // GeoJSON order is [lon, lat]
        assertEquals(45.7610, poi.lat, 1e-9)
        assertEquals(4.8570, poi.lon, 1e-9)

        val address = results[1]
        assertEquals("12 Rue de la République", address.label)
        assertEquals("69002 Lyon", address.detail)
    }

    @Test
    fun limitIsApplied() {
        val response = json.decodeFromString<PhotonResponse>(fixture)
        val results = mapPhotonFeatures(response.features, limit = 1)
        assertEquals(1, results.size)
        assertEquals("Carrefour Part-Dieu", results[0].label)
    }

    @Test
    fun featureWithoutNameOrStreetIsDropped() {
        val response = json.decodeFromString<PhotonResponse>(fixture)
        assertNull(photonFeatureToResult(response.features[2]))
    }

    @Test
    fun featureWithoutCoordinatesIsDropped() {
        val broken = json.decodeFromString<PhotonResponse>(
            """{"features":[{"geometry":{"coordinates":[]},"properties":{"name":"Nowhere"}}]}"""
        )
        assertNull(photonFeatureToResult(broken.features[0]))
    }

    @Test
    fun regionFilterKeepsLyonAreaOnly() {
        assertTrue("Lyon center", isNearLyon(45.7578, 4.8320))
        assertTrue("Villefranche-sur-Saône (~30 km)", isNearLyon(45.9860, 4.7190))
        assertFalse("Paris (~390 km)", isNearLyon(48.8584, 2.2945))
        assertFalse("Marseille (~280 km)", isNearLyon(43.2965, 5.3698))
    }
}
