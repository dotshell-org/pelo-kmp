package eu.dotshell.massilia.generic.data.cache

import eu.dotshell.massilia.generic.data.repository.itinerary.itinerary.IntermediateStop
import kotlinx.serialization.Serializable

@Serializable
data class SerializableIntermediateStop(
    val stopName: String,
    val arrivalTime: Int,
    val lat: Double = 0.0,
    val lon: Double = 0.0
) {
    fun toIntermediateStop() = IntermediateStop(
        stopName = stopName,
        arrivalTime = arrivalTime,
        lat = lat,
        lon = lon
    )

    companion object {
        fun fromIntermediateStop(stop: IntermediateStop) = SerializableIntermediateStop(
            stopName = stop.stopName,
            arrivalTime = stop.arrivalTime,
            lat = stop.lat,
            lon = stop.lon
        )
    }
}
