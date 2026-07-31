package eu.dotshell.pelo.generic.data.repository.itinerary.itinerary

/**
 * Total walking distance of a journey, in meters. Legs carry no distance, so it is derived from
 * each walking leg's duration and the walking speed used to route it (already detour-adjusted, so
 * this is the real distance on the ground). Used to cap the "closest stop, then walk" fallback.
 */
fun journeyWalkingMeters(journey: JourneyResult, metersPerSecond: Double): Double =
    journey.legs
        .filter { it.isWalking }
        .sumOf { (it.arrivalTime - it.departureTime).toDouble() } * metersPerSecond
