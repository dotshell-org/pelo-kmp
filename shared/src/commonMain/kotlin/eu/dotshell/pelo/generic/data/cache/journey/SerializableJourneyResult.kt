package eu.dotshell.pelo.generic.data.cache.journey

import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.JourneyResult
import kotlinx.serialization.Serializable

/**
 * Serializable version of JourneyResult for JSON storage
 */
@Serializable
data class SerializableJourneyResult(
    val departureTime: Int,
    val arrivalTime: Int,
    val legs: List<SerializableJourneyLeg>
) {
    fun toJourneyResult() = JourneyResult(
        departureTime = departureTime,
        arrivalTime = arrivalTime,
        legs = legs.map { it.toJourneyLeg() }
    )

    companion object {
        fun fromJourneyResult(result: JourneyResult) = SerializableJourneyResult(
            departureTime = result.departureTime,
            arrivalTime = result.arrivalTime,
            legs = result.legs.map { SerializableJourneyLeg.fromJourneyLeg(it) }
        )
    }
}
