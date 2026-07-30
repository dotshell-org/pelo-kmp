package eu.dotshell.pelo.generic.data.repository.itinerary.itinerary

import kotlinx.serialization.Serializable

/**
 * Kind of journey leg. TRANSIT and TRANSFER cover classic stop-to-stop journeys; the WALK_*
 * kinds come from coordinate-based queries (address/POI or GPS endpoints, raptor-kmp 2.0+).
 */
@Serializable
enum class JourneyLegKind {
    TRANSIT,
    TRANSFER,
    WALK_ACCESS,
    WALK_EGRESS,
    WALK_DIRECT
}

/**
 * Data class representing a leg of a journey.
 * A coordinate endpoint (walk leg from/to an address or GPS point) uses stopId "-1";
 * its lat/lon then come from the query point rather than a network stop.
 */
data class JourneyLeg(
    val fromStopId: String,
    val fromStopName: String,
    val fromLat: Double,
    val fromLon: Double,
    val toStopId: String,
    val toStopName: String,
    val toLat: Double,
    val toLon: Double,
    val departureTime: Int,
    val arrivalTime: Int,
    val routeName: String?,
    val routeColor: String?,
    val isWalking: Boolean,
    val direction: String? = null,
    val intermediateStops: List<IntermediateStop> = emptyList(),
    val legKind: JourneyLegKind = if (isWalking) JourneyLegKind.TRANSFER else JourneyLegKind.TRANSIT
) {
    val durationMinutes: Int
        get() = (arrivalTime - departureTime) / 60

    fun formatDepartureTime(): String = formatTime(departureTime)
    fun formatArrivalTime(): String = formatTime(arrivalTime)

    private fun formatTime(seconds: Int): String {
        // GTFS service-day hours can exceed 24 on night runs ("25:30" = 01:30)
        val hours = (seconds / 3600) % 24
        val minutes = (seconds % 3600) / 60
        return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"
    }
}
