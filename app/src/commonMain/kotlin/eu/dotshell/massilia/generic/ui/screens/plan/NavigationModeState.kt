package eu.dotshell.massilia.generic.ui.screens.plan

import eu.dotshell.massilia.generic.data.repository.itinerary.itinerary.JourneyLeg
import eu.dotshell.massilia.generic.data.repository.itinerary.itinerary.JourneyResult
import eu.dotshell.massilia.generic.utils.geo.GeometryUtils
import eu.dotshell.massilia.generic.utils.location.GeoPoint

private const val DAY_SECONDS = 24 * 3600
private const val LONG_TRANSFER_THRESHOLD_SECONDS = 10 * 60

data class NavigationModeUiState(
    val currentLeg: JourneyLeg?,
    val nextLeg: JourneyLeg?,
    val upcomingLeg: JourneyLeg?,
    val displayedLeg: JourneyLeg?,
    val shouldChangeLine: Boolean,
    val actionText: String,
    val directionText: String,
    val remainingTimeText: String,
    val arrivalTimeText: String,
    val isFinished: Boolean
)

fun buildNavigationModeUiState(
    journey: JourneyResult,
    nowSeconds: Int,
    userLocation: GeoPoint?
): NavigationModeUiState {
    val (currentLeg, nextLeg) = getCurrentAndNextNavigationLeg(journey, nowSeconds, userLocation)
    val remainingSeconds = computeRemainingJourneySeconds(journey, nowSeconds)

    if (currentLeg == null) {
        return NavigationModeUiState(
            currentLeg = null,
            nextLeg = null,
            upcomingLeg = null,
            displayedLeg = null,
            shouldChangeLine = false,
            actionText = "Trajet en cours",
            directionText = "",
            remainingTimeText = formatRemainingTime(journey.departureTime, journey.arrivalTime, nowSeconds),
            arrivalTimeText = journey.formatArrivalTime(),
            isFinished = remainingSeconds <= 0
        )
    }

    val reference = journey.departureTime
    val nowNormalized = normalizeTimeAroundReference(nowSeconds, reference)
    val legDepartureNormalized = normalizeTimeAroundReference(currentLeg.departureTime, reference)
    val isWaitingForVehicle = nowNormalized < legDepartureNormalized
    val hasCorrespondence = nextLeg != null
    val transferWaitSeconds = if (nextLeg != null) {
        computeTransferWaitSeconds(currentLeg, nextLeg, reference)
    } else {
        0
    }
    val shouldSplitTransferInstructions = transferWaitSeconds > LONG_TRANSFER_THRESHOLD_SECONDS
    val shouldChangeLine = !isWaitingForVehicle &&
            hasCorrespondence &&
            !shouldSplitTransferInstructions &&
            isAtCurrentLegTransferStop(journey, currentLeg, userLocation)
    val displayedLeg = if (shouldChangeLine && nextLeg != null) nextLeg else currentLeg
    val upcomingOffset = if (shouldChangeLine) 2 else 1
    val upcomingLeg = findUpcomingNonWalkingLeg(journey, currentLeg, upcomingOffset)
    val remainingStops = computeRemainingStopsOnLeg(currentLeg, userLocation)

    val actionText = if (isWaitingForVehicle) {
        val remainingBeforeDeparture = formatDurationUntil(nowNormalized, legDepartureNormalized)
        "Dans $remainingBeforeDeparture, monter a ${currentLeg.fromStopName}"
    } else {
        val targetStopName = currentLeg.toStopName.ifBlank { "l'arret suivant" }
        val actionVerb = if (shouldChangeLine) {
            "changer de ligne a $targetStopName"
        } else {
            "descendre a $targetStopName"
        }
        if (remainingStops <= 0) {
            "Au prochain arret, $actionVerb"
        } else {
            val stopWord = if (remainingStops == 1) "arret" else "arrets"
            "Dans $remainingStops $stopWord, $actionVerb"
        }
    }

    val direction = displayedLeg.direction?.takeIf { it.isNotBlank() } ?: "?"
    return NavigationModeUiState(
        currentLeg = currentLeg,
        nextLeg = nextLeg,
        upcomingLeg = upcomingLeg,
        displayedLeg = displayedLeg,
        shouldChangeLine = shouldChangeLine,
        actionText = actionText,
        directionText = "Direction $direction",
        remainingTimeText = formatRemainingTime(journey.departureTime, journey.arrivalTime, nowSeconds),
        arrivalTimeText = journey.formatArrivalTime(),
        isFinished = remainingSeconds <= 0
    )
}

fun computeRemainingJourneySeconds(journey: JourneyResult, nowSeconds: Int): Int {
    val reference = journey.departureTime
    val nowNormalized = normalizeTimeAroundReference(nowSeconds, reference)
    val arrivalNormalized = normalizeTimeAroundReference(journey.arrivalTime, reference)
    return (arrivalNormalized - nowNormalized).coerceAtLeast(0)
}

private fun normalizeTimeAroundReference(timeSeconds: Int, referenceSeconds: Int): Int {
    var normalized = timeSeconds
    while (normalized < referenceSeconds - DAY_SECONDS / 2) normalized += DAY_SECONDS
    while (normalized > referenceSeconds + DAY_SECONDS / 2) normalized -= DAY_SECONDS
    return normalized
}

private fun getCurrentAndNextNavigationLeg(
    journey: JourneyResult,
    nowSeconds: Int,
    userLocation: GeoPoint?
): Pair<JourneyLeg?, JourneyLeg?> {
    val nonWalkingLegs = journey.legs.filterNot { it.isWalking }
    if (nonWalkingLegs.isEmpty()) return null to null

    val reference = journey.departureTime
    val now = normalizeTimeAroundReference(nowSeconds, reference)
    val normalizedLegs = nonWalkingLegs.map { leg ->
        val dep = normalizeTimeAroundReference(leg.departureTime, reference)
        val arr = normalizeTimeAroundReference(leg.arrivalTime, reference)
        dep to arr
    }

    var currentIndex = normalizedLegs.indexOfFirst { (dep, arr) -> now in dep..arr }
    if (currentIndex == -1) {
        currentIndex = normalizedLegs.indexOfFirst { (dep, _) -> now < dep }
    }
    if (currentIndex == -1) {
        currentIndex = nonWalkingLegs.lastIndex
    }

    val nearestStop = findNearestJourneyStopCandidate(journey, userLocation)
    if (nearestStop != null) {
        val maxLegIndexByLocation =
            if (nearestStop.isLegEnd && nearestStop.legIndex < nonWalkingLegs.lastIndex) {
                nearestStop.legIndex + 1
            } else {
                nearestStop.legIndex
            }
        currentIndex = currentIndex.coerceAtMost(maxLegIndexByLocation)
    }

    val currentLeg = nonWalkingLegs.getOrNull(currentIndex)
    val nextLeg = nonWalkingLegs.drop(currentIndex + 1).firstOrNull()
    return currentLeg to nextLeg
}

private fun formatRemainingTime(
    departureTimeSeconds: Int,
    arrivalTimeSeconds: Int,
    nowSeconds: Int
): String {
    val fullTripSeconds = if (arrivalTimeSeconds >= departureTimeSeconds) {
        arrivalTimeSeconds - departureTimeSeconds
    } else {
        arrivalTimeSeconds + DAY_SECONDS - departureTimeSeconds
    }

    val elapsedSinceDeparture = if (nowSeconds >= departureTimeSeconds) {
        nowSeconds - departureTimeSeconds
    } else {
        nowSeconds + DAY_SECONDS - departureTimeSeconds
    }

    val remainingSeconds = if (elapsedSinceDeparture in 0..fullTripSeconds) {
        fullTripSeconds - elapsedSinceDeparture
    } else {
        fullTripSeconds
    }

    val remainingMinutes = (remainingSeconds / 60).coerceAtLeast(0)
    return if (remainingMinutes < 60) {
        "$remainingMinutes min"
    } else {
        "${remainingMinutes / 60}h${(remainingMinutes % 60).toString().padStart(2, '0')}"
    }
}

private fun formatDurationUntil(
    nowNormalizedSeconds: Int,
    targetNormalizedSeconds: Int
): String {
    val remainingSeconds = (targetNormalizedSeconds - nowNormalizedSeconds).coerceAtLeast(0)
    if (remainingSeconds < 60) return "moins d'1 min"

    val remainingMinutes = remainingSeconds / 60
    return if (remainingMinutes < 60) {
        "$remainingMinutes min"
    } else {
        "${remainingMinutes / 60}h${(remainingMinutes % 60).toString().padStart(2, '0')}"
    }
}

private fun computeTransferWaitSeconds(
    currentLeg: JourneyLeg,
    nextLeg: JourneyLeg,
    journeyReferenceSeconds: Int
): Int {
    val currentArrivalNormalized =
        normalizeTimeAroundReference(currentLeg.arrivalTime, journeyReferenceSeconds)
    var nextDepartureNormalized =
        normalizeTimeAroundReference(nextLeg.departureTime, journeyReferenceSeconds)
    while (nextDepartureNormalized < currentArrivalNormalized) {
        nextDepartureNormalized += DAY_SECONDS
    }
    return (nextDepartureNormalized - currentArrivalNormalized).coerceAtLeast(0)
}

private data class LegStopPosition(
    val index: Int,
    val lat: Double,
    val lon: Double
)

private data class JourneyStopCandidate(
    val legIndex: Int,
    val isLegEnd: Boolean,
    val lat: Double,
    val lon: Double
)

private fun findNearestJourneyStopCandidate(
    journey: JourneyResult,
    userLocation: GeoPoint?
): JourneyStopCandidate? {
    if (userLocation == null) return null

    val candidates = mutableListOf<JourneyStopCandidate>()
    journey.legs.filterNot { it.isWalking }.forEachIndexed { legIndex, leg ->
        if (isValidJourneyCoordinate(leg.fromLat, leg.fromLon)) {
            candidates += JourneyStopCandidate(legIndex, false, leg.fromLat, leg.fromLon)
        }
        leg.intermediateStops.forEach { stop ->
            if (isValidJourneyCoordinate(stop.lat, stop.lon)) {
                candidates += JourneyStopCandidate(legIndex, false, stop.lat, stop.lon)
            }
        }
        if (isValidJourneyCoordinate(leg.toLat, leg.toLon)) {
            candidates += JourneyStopCandidate(legIndex, true, leg.toLat, leg.toLon)
        }
    }
    if (candidates.isEmpty()) return null

    return candidates.minByOrNull { stop ->
        GeometryUtils.squaredDistance(
            lat1 = userLocation.latitude,
            lon1 = userLocation.longitude,
            lat2 = stop.lat,
            lon2 = stop.lon
        )
    }
}

private fun isSameJourneyLeg(first: JourneyLeg, second: JourneyLeg): Boolean {
    return first.fromStopId == second.fromStopId &&
            first.toStopId == second.toStopId &&
            first.departureTime == second.departureTime &&
            first.arrivalTime == second.arrivalTime &&
            first.routeName == second.routeName
}

private fun isAtCurrentLegTransferStop(
    journey: JourneyResult,
    currentLeg: JourneyLeg,
    userLocation: GeoPoint?
): Boolean {
    val nearestStop = findNearestJourneyStopCandidate(journey, userLocation) ?: return false
    val nonWalkingLegs = journey.legs.filterNot { it.isWalking }
    val currentLegIndex = nonWalkingLegs.indexOfFirst { leg -> isSameJourneyLeg(leg, currentLeg) }
    if (currentLegIndex == -1 || currentLegIndex >= nonWalkingLegs.lastIndex) return false
    return nearestStop.legIndex == currentLegIndex && nearestStop.isLegEnd
}

private fun computeRemainingStopsOnLeg(
    leg: JourneyLeg,
    userLocation: GeoPoint?
): Int {
    val stops = ArrayList<LegStopPosition>(leg.intermediateStops.size + 2)
    stops += LegStopPosition(index = 0, lat = leg.fromLat, lon = leg.fromLon)
    leg.intermediateStops.forEachIndexed { stopIndex, stop ->
        stops += LegStopPosition(index = stopIndex + 1, lat = stop.lat, lon = stop.lon)
    }
    val terminusIndex = stops.size
    stops += LegStopPosition(index = terminusIndex, lat = leg.toLat, lon = leg.toLon)

    val nearestStopIndex = userLocation?.let { location ->
        stops
            .filter { isValidJourneyCoordinate(it.lat, it.lon) }
            .minByOrNull { stop ->
                GeometryUtils.squaredDistance(
                    lat1 = location.latitude,
                    lon1 = location.longitude,
                    lat2 = stop.lat,
                    lon2 = stop.lon
                )
            }?.index
    } ?: 0

    return (terminusIndex - nearestStopIndex).coerceAtLeast(0)
}

private fun findUpcomingNonWalkingLeg(
    journey: JourneyResult,
    currentLeg: JourneyLeg,
    offsetFromCurrent: Int
): JourneyLeg? {
    val nonWalkingLegs = journey.legs.filterNot { it.isWalking }
    val currentIndex = nonWalkingLegs.indexOfFirst { leg -> isSameJourneyLeg(leg, currentLeg) }
    if (currentIndex == -1) return null
    return nonWalkingLegs.getOrNull(currentIndex + offsetFromCurrent)
}

private fun isValidJourneyCoordinate(lat: Double, lon: Double): Boolean {
    return lat in -90.0..90.0 && lon in -180.0..180.0 && (lat != 0.0 || lon != 0.0)
}
