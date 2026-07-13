package eu.dotshell.pelo.generic.data.repository.itinerary.itinerary

/**
 * Data class representing an intermediate stop
 */
data class IntermediateStop(
    val stopName: String,
    val arrivalTime: Int,
    val lat: Double = 0.0,
    val lon: Double = 0.0
) {
    fun formatArrivalTime(): String {
        // GTFS service-day hours can exceed 24 on night runs ("25:30" = 01:30)
        val hours = (arrivalTime / 3600) % 24
        val minutes = (arrivalTime % 3600) / 60
        return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"
    }
}
