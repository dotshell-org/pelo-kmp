package eu.dotshell.pelo.generic.data.repository.itinerary.itinerary

/**
 * Data class for stop with coordinates (used for coordinate-based matching)
 */
data class RaptorStopWithCoords(
    val id: Int,
    val name: String,
    val lat: Double,
    val lon: Double
)
