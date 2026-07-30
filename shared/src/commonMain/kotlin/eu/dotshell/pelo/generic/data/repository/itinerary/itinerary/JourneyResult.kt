package eu.dotshell.pelo.generic.data.repository.itinerary.itinerary

/**
 * Data class representing a journey result
 */
data class JourneyResult(
    val departureTime: Int, // in seconds from midnight
    val arrivalTime: Int, // in seconds from midnight
    val legs: List<JourneyLeg>
) {
    val durationMinutes: Int
        get() = (arrivalTime - departureTime) / 60

    fun formatDepartureTime(): String = formatTime(departureTime)
    fun formatArrivalTime(): String = formatTime(arrivalTime)

    private fun formatTime(seconds: Int): String {
        val hours = (seconds / 3600) % 24
        val minutes = (seconds % 3600) / 60
        return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"
    }

}
