package eu.dotshell.pelo.generic.data.repository.itinerary.itinerary

import androidx.compose.runtime.Immutable

/**
 * Data class representing a journey result
 *
 * @Immutable because `legs` is a read-only List, which the compiler cannot prove is not a
 * MutableList someone else still holds — so without this the whole type is inferred unstable, and
 * so is every composable taking one. It is the item type of the itinerary list, so that mattered.
 * The promise holds: every property is a val, the derived members read only those vals, and the
 * legs list is built once by RaptorRepository and handed over. Do not add a getter here that reads
 * a clock.
 */
@Immutable
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
