package eu.dotshell.massilia.generic.data.cache.journey

import eu.dotshell.massilia.generic.data.cache.SerializableIntermediateStop
import eu.dotshell.massilia.generic.data.repository.itinerary.itinerary.JourneyLeg
import kotlinx.serialization.Serializable

@Serializable
data class SerializableJourneyLeg(
    val fromStopId: String,
    val fromStopName: String,
    val fromLat: Double = 0.0,
    val fromLon: Double = 0.0,
    val toStopId: String,
    val toStopName: String,
    val toLat: Double = 0.0,
    val toLon: Double = 0.0,
    val departureTime: Int,
    val arrivalTime: Int,
    val routeName: String?,
    val routeColor: String?,
    val isWalking: Boolean,
    val direction: String?,
    val intermediateStops: List<SerializableIntermediateStop>
) {
    fun toJourneyLeg() = JourneyLeg(
        fromStopId = fromStopId,
        fromStopName = fromStopName,
        fromLat = fromLat,
        fromLon = fromLon,
        toStopId = toStopId,
        toStopName = toStopName,
        toLat = toLat,
        toLon = toLon,
        departureTime = departureTime,
        arrivalTime = arrivalTime,
        routeName = routeName,
        routeColor = routeColor,
        isWalking = isWalking,
        direction = direction,
        intermediateStops = intermediateStops.map { it.toIntermediateStop() }
    )

    companion object {
        fun fromJourneyLeg(leg: JourneyLeg) = SerializableJourneyLeg(
            fromStopId = leg.fromStopId,
            fromStopName = leg.fromStopName,
            fromLat = leg.fromLat,
            fromLon = leg.fromLon,
            toStopId = leg.toStopId,
            toStopName = leg.toStopName,
            toLat = leg.toLat,
            toLon = leg.toLon,
            departureTime = leg.departureTime,
            arrivalTime = leg.arrivalTime,
            routeName = leg.routeName,
            routeColor = leg.routeColor,
            isWalking = leg.isWalking,
            direction = leg.direction,
            intermediateStops = leg.intermediateStops.map {
                SerializableIntermediateStop.Companion.fromIntermediateStop(
                    it
                )
            }
        )
    }
}
