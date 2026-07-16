package eu.dotshell.pelo

import eu.dotshell.pelo.generic.data.repository.offline.search.SearchHistoryItem
import eu.dotshell.pelo.generic.data.repository.offline.search.SearchType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the search-history serialization compatibility around the ADDRESS entries
 * (coordinates persisted so addresses re-select without re-geocoding).
 */
class SearchHistoryItemTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun legacyEntriesWithoutCoordinateFieldsStillDecode() {
        val legacy = """
            [{"query":"Bellecour","type":"STOP","lines":["A","D"],"timestamp":1720000000000},
             {"query":"C3","type":"LINE","timestamp":1720000000001}]
        """.trimIndent()
        val items = json.decodeFromString<List<SearchHistoryItem>>(legacy)

        assertEquals(2, items.size)
        assertEquals(SearchType.STOP, items[0].type)
        assertNull(items[0].lat)
        assertNull(items[0].detail)
    }

    @Test
    fun addressEntryRoundTripsWithCoordinates() {
        val address = SearchHistoryItem(
            query = "Carrefour Part-Dieu",
            type = SearchType.ADDRESS,
            lat = 45.7601,
            lon = 4.8557,
            detail = "Rue du Docteur Bouchut, 69003 Lyon"
        )
        val decoded = json.decodeFromString<SearchHistoryItem>(json.encodeToString(SearchHistoryItem.serializer(), address))

        assertEquals(address.query, decoded.query)
        assertEquals(SearchType.ADDRESS, decoded.type)
        assertEquals(45.7601, decoded.lat!!, 1e-9)
        assertEquals(4.8557, decoded.lon!!, 1e-9)
        assertEquals(address.detail, decoded.detail)
    }
}
