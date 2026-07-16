package eu.dotshell.pelo.generic.data.repository.itinerary.itinerary

import io.raptor.core.JourneyLeg as RaptorLeg
import io.raptor.core.LegType
import io.raptor.model.Stop

/**
 * Maps raptor-library journeys to app [JourneyResult]s. Shared by the forward and arrive-by
 * paths of RaptorRepository (previously two duplicated loops).
 *
 * Stop index -1 marks a coordinate endpoint (walk leg from/to an address or GPS point,
 * raptor-kmp 2.0+): it is resolved from the leg's own coordinates and the query labels.
 * Any other index that fails to resolve still invalidates the whole journey, as before.
 */
internal fun mapLibraryJourneys(
    journeys: List<List<RaptorLeg>>,
    stopIndex: Map<Int, Stop>,
    originLabel: String? = null,
    destinationLabel: String? = null
): List<JourneyResult> {
    val results = ArrayList<JourneyResult>(journeys.size)

    for (legs in journeys) {
        if (legs.isEmpty()) continue

        val journeyLegs = ArrayList<JourneyLeg>(legs.size)
        var hasInvalidLeg = false

        for (leg in legs) {
            // A -1 "from" is always the query origin; a -1 "to" always the destination
            val from = resolveLegEndpoint(stopIndex, leg.fromStopIndex, leg.fromLat, leg.fromLon, originLabel)
            val to = resolveLegEndpoint(stopIndex, leg.toStopIndex, leg.toLat, leg.toLon, destinationLabel)

            if (from == null || to == null) {
                hasInvalidLeg = true
                break
            }

            // Map intermediate stops using explicit for loop
            val intermediateIndices = leg.intermediateStopIndices
            val intermediateTimes = leg.intermediateArrivalTimes
            val intermediateStops = ArrayList<IntermediateStop>(intermediateIndices.size)

            for (idx in intermediateIndices.indices) {
                val stop = stopIndex[intermediateIndices[idx]]
                val arrivalTime =
                    if (idx < intermediateTimes.size) intermediateTimes[idx] else null
                if (stop != null && arrivalTime != null) {
                    intermediateStops.add(
                        IntermediateStop(
                            stopName = stop.name,
                            arrivalTime = arrivalTime,
                            lat = stop.lat,
                            lon = stop.lon
                        )
                    )
                }
            }

            journeyLegs.add(
                JourneyLeg(
                    fromStopId = from.stopId,
                    fromStopName = from.name,
                    fromLat = from.lat,
                    fromLon = from.lon,
                    toStopId = to.stopId,
                    toStopName = to.name,
                    toLat = to.lat,
                    toLon = to.lon,
                    departureTime = leg.departureTime,
                    arrivalTime = leg.arrivalTime,
                    routeName = leg.routeName,
                    routeColor = null, // Library doesn't provide color
                    isWalking = leg.isTransfer,
                    direction = leg.direction,
                    intermediateStops = intermediateStops,
                    legKind = leg.legType.toJourneyLegKind()
                )
            )
        }

        // Skip this journey if any leg was invalid
        if (hasInvalidLeg || journeyLegs.isEmpty()) continue

        results.add(
            JourneyResult(
                departureTime = legs.first().departureTime,
                arrivalTime = legs.last().arrivalTime,
                legs = journeyLegs
            )
        )
    }

    return results
}

private class LegEndpoint(val stopId: String, val name: String, val lat: Double, val lon: Double)

/**
 * Resolves one end of a leg: a real stop for indices >= 0 (null if unknown), or the leg's own
 * coordinates + the query label for the -1 coordinate sentinel (null if the coordinates are
 * missing, which the library contract forbids — defensive).
 */
private fun resolveLegEndpoint(
    stopIndex: Map<Int, Stop>,
    index: Int,
    legLat: Double?,
    legLon: Double?,
    coordinateLabel: String?
): LegEndpoint? {
    if (index >= 0) {
        val stop = stopIndex[index] ?: return null
        return LegEndpoint(stop.id.toString(), stop.name, stop.lat, stop.lon)
    }
    if (legLat == null || legLon == null) return null
    return LegEndpoint("-1", coordinateLabel ?: "", legLat, legLon)
}

private fun LegType.toJourneyLegKind(): JourneyLegKind = when (this) {
    LegType.TRANSIT -> JourneyLegKind.TRANSIT
    LegType.TRANSFER -> JourneyLegKind.TRANSFER
    LegType.WALK_ACCESS -> JourneyLegKind.WALK_ACCESS
    LegType.WALK_EGRESS -> JourneyLegKind.WALK_EGRESS
    LegType.WALK_DIRECT -> JourneyLegKind.WALK_DIRECT
}
