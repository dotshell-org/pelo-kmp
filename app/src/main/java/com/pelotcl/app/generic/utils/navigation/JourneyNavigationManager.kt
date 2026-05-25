package com.pelotcl.app.generic.utils.navigation

import com.pelotcl.app.generic.data.models.itinerary.JourneyStopCandidate
import com.pelotcl.app.generic.data.models.itinerary.LegStopPosition
import com.pelotcl.app.generic.data.models.navigation.NavigationAlertPrompt
import com.pelotcl.app.generic.data.models.navigation.NavigationAlertPromptKind
import com.pelotcl.app.generic.data.models.navigation.NavigationKeyStopDeadline
import com.pelotcl.app.generic.data.models.navigation.NavigationKeyStopType
import com.pelotcl.app.generic.data.models.realtime.alerts.community.UserStopAlertsResponse
import com.pelotcl.app.generic.data.repository.itinerary.itinerary.JourneyLeg
import com.pelotcl.app.generic.data.repository.itinerary.itinerary.JourneyResult
import com.pelotcl.app.generic.ui.screens.plan.AlertType
import com.pelotcl.app.generic.ui.screens.plan.LATE_TRANSFER_RECALC_THRESHOLD_SECONDS
import com.pelotcl.app.generic.ui.screens.plan.NAV_ALERT_APPROACH_DISTANCE_METERS
import com.pelotcl.app.generic.ui.screens.plan.NAV_ALERT_APPROACH_TIME_SECONDS
import com.pelotcl.app.generic.ui.viewmodel.TransportViewModel
import com.pelotcl.app.generic.utils.geo.GeometryUtils
import org.maplibre.android.geometry.LatLng

object JourneyNavigationManager {

    suspend fun buildNavigationPathPoints(
        journey: JourneyResult,
        viewModel: TransportViewModel
    ): List<LatLng> {
        val points = mutableListOf<LatLng>()

        fun appendPointIfDistinct(point: LatLng) {
            val last = points.lastOrNull()
            if (last == null ||
                last.latitude != point.latitude ||
                last.longitude != point.longitude
            ) {
                points.add(point)
            }
        }

        fun appendFallbackLegPoints(leg: JourneyLeg) {
            if (isValidJourneyCoordinate(leg.fromLat, leg.fromLon)) {
                appendPointIfDistinct(LatLng(leg.fromLat, leg.fromLon))
            }
            leg.intermediateStops.forEach { stop ->
                if (isValidJourneyCoordinate(stop.lat, stop.lon)) {
                    appendPointIfDistinct(LatLng(stop.lat, stop.lon))
                }
            }
            if (isValidJourneyCoordinate(leg.toLat, leg.toLon)) {
                appendPointIfDistinct(LatLng(leg.toLat, leg.toLon))
            }
        }

        journey.legs.filterNot { it.isWalking }.forEach { leg ->
            val routeName = leg.routeName?.takeIf { it.isNotBlank() }
            if (routeName == null) {
                appendFallbackLegPoints(leg)
                return@forEach
            }

            val sectionPoints = runCatching {
                val lines = viewModel.transportRepository
                    .getLineByName(routeName)
                    .getOrElse { emptyList() }
                if (lines.isEmpty()) return@runCatching emptyList<LatLng>()

                val sectionedLines = viewModel.sectionLinesBetweenStops(
                    lines = lines,
                    startStopId = leg.fromStopId,
                    endStopId = leg.toStopId,
                    leg = leg
                )

                val coordinates = sectionedLines
                    .firstOrNull()
                    ?.multiLineStringGeometry
                    ?.coordinates
                    ?.firstOrNull()
                    .orEmpty()

                coordinates.mapNotNull { coord ->
                    if (coord.size < 2) return@mapNotNull null
                    val lon = coord[0]
                    val lat = coord[1]
                    if (!isValidJourneyCoordinate(lat, lon)) return@mapNotNull null
                    LatLng(lat, lon)
                }
            }.getOrElse { emptyList() }

            if (sectionPoints.size >= 2) {
                sectionPoints.forEach(::appendPointIfDistinct)
            } else {
                appendFallbackLegPoints(leg)
            }
        }

        return points
    }

    fun buildNavigationAlertPrompt(
        alerts: UserStopAlertsResponse,
        stopName: String?
    ): NavigationAlertPrompt? {
        val status = stopName?.let(alerts::get) ?: return null

        val highKarmaAlert = status.karmaAtOrAboveThreshold.maxByOrNull { it.karma }
        if (highKarmaAlert != null) {
            return NavigationAlertPrompt(
                kind = NavigationAlertPromptKind.HIGH_KARMA_STILL_THERE,
                alertTypeId = highKarmaAlert.type.ifBlank { AlertType.CROWDING.id }
            )
        }

        val lowKarmaAlert = status.karmaBelowThreshold.maxByOrNull { it.karma }
        if (lowKarmaAlert != null) {
            return NavigationAlertPrompt(
                kind = NavigationAlertPromptKind.LOW_KARMA_CONFIRM,
                alertTypeId = lowKarmaAlert.type.ifBlank { AlertType.CROWDING.id }
            )
        }

        return null
    }

    fun buildNavigationAlertQuestion(prompt: NavigationAlertPrompt): String {
        val alertLabel = findAlertTypeLabel(prompt.alertTypeId)
        return when (prompt.kind) {
            NavigationAlertPromptKind.LOW_KARMA_CONFIRM -> "$alertLabel bien là ?"
            NavigationAlertPromptKind.HIGH_KARMA_STILL_THERE -> "$alertLabel toujours là ?"
        }
    }

    fun buildJourneyAlertSessionKey(journey: JourneyResult): String = buildString {
        append(journey.departureTime)
        append('_')
        append(journey.arrivalTime)
        append('_')
        append(journey.legs.joinToString(separator = "|") { leg ->
            "${leg.fromStopId}>${leg.toStopId}>${leg.departureTime}>${leg.arrivalTime}>${leg.routeName ?: ""}"
        })
    }

    fun buildNavigationKeyStopDeadlines(journey: JourneyResult): List<NavigationKeyStopDeadline> {
        val nonWalkingLegs = journey.legs.filterNot { it.isWalking }
        if (nonWalkingLegs.isEmpty()) return emptyList()

        val result = mutableListOf<NavigationKeyStopDeadline>()
        val firstLeg = nonWalkingLegs.first()
        if (isValidJourneyCoordinate(firstLeg.fromLat, firstLeg.fromLon)) {
            result += NavigationKeyStopDeadline(
                stopId = firstLeg.fromStopId,
                stopName = firstLeg.fromStopName,
                lat = firstLeg.fromLat,
                lon = firstLeg.fromLon,
                deadlineSeconds = firstLeg.departureTime,
                type = NavigationKeyStopType.START
            )
        }

        for (index in 0 until nonWalkingLegs.lastIndex) {
            val current = nonWalkingLegs[index]
            val next = nonWalkingLegs[index + 1]
            if (isValidJourneyCoordinate(current.toLat, current.toLon)) {
                result += NavigationKeyStopDeadline(
                    stopId = current.toStopId,
                    stopName = current.toStopName,
                    lat = current.toLat,
                    lon = current.toLon,
                    deadlineSeconds = next.departureTime,
                    type = NavigationKeyStopType.TRANSFER
                )
            }
        }

        val lastLeg = nonWalkingLegs.last()
        if (isValidJourneyCoordinate(lastLeg.toLat, lastLeg.toLon)) {
            result += NavigationKeyStopDeadline(
                stopId = lastLeg.toStopId,
                stopName = lastLeg.toStopName,
                lat = lastLeg.toLat,
                lon = lastLeg.toLon,
                deadlineSeconds = lastLeg.arrivalTime,
                type = NavigationKeyStopType.TERMINUS
            )
        }

        return result
    }

    fun computeRemainingJourneySeconds(
        journey: JourneyResult,
        nowSeconds: Int
    ): Int {
        val reference = journey.departureTime
        val nowNormalized = normalizeTimeAroundReference(nowSeconds, reference)
        val arrivalNormalized = normalizeTimeAroundReference(journey.arrivalTime, reference)
        return (arrivalNormalized - nowNormalized).coerceAtLeast(0)
    }

    fun computeRemainingStopsOnLeg(
        leg: JourneyLeg,
        userLocation: LatLng?
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

    fun computeTransferWaitSeconds(
        currentLeg: JourneyLeg,
        nextLeg: JourneyLeg,
        journeyReferenceSeconds: Int
    ): Int {
        val currentArrivalNormalized =
            normalizeTimeAroundReference(currentLeg.arrivalTime, journeyReferenceSeconds)
        var nextDepartureNormalized =
            normalizeTimeAroundReference(nextLeg.departureTime, journeyReferenceSeconds)
        while (nextDepartureNormalized < currentArrivalNormalized) {
            nextDepartureNormalized += 24 * 3600
        }
        return (nextDepartureNormalized - currentArrivalNormalized).coerceAtLeast(0)
    }

    fun findApproachingAlertStop(
        journey: JourneyResult,
        currentLeg: JourneyLeg,
        nextLeg: JourneyLeg,
        userLocation: LatLng?,
        nowSeconds: Int
    ): JourneyLeg? {
        val candidateLegs = listOf(currentLeg, nextLeg)
        val nearestByDistance = userLocation?.let { location ->
            candidateLegs
                .minByOrNull { leg ->
                    GeometryUtils.distanceMeters(
                        lat1 = location.latitude,
                        lon1 = location.longitude,
                        lat2 = leg.toLat,
                        lon2 = leg.toLon
                    )
                }
                ?.takeIf { leg ->
                    GeometryUtils.distanceMeters(
                        lat1 = location.latitude,
                        lon1 = location.longitude,
                        lat2 = leg.toLat,
                        lon2 = leg.toLon
                    ) <= NAV_ALERT_APPROACH_DISTANCE_METERS
                }
        }

        val reference = journey.departureTime
        val nowNormalized = normalizeTimeAroundReference(nowSeconds, reference)
        val nearestByTime = candidateLegs
            .minByOrNull { leg ->
                kotlin.math.abs(
                    normalizeTimeAroundReference(leg.arrivalTime, reference) - nowNormalized
                )
            }
            ?.takeIf { leg ->
                kotlin.math.abs(
                    normalizeTimeAroundReference(leg.arrivalTime, reference) - nowNormalized
                ) <= NAV_ALERT_APPROACH_TIME_SECONDS
            }

        return when {
            isAtCurrentLegTransferStop(journey, currentLeg, userLocation) -> currentLeg
            nearestByDistance != null -> nearestByDistance
            nearestByTime != null -> nearestByTime
            else -> null
        }
    }

    fun findOverdueNavigationKeyStop(
        journey: JourneyResult,
        userLocation: LatLng?,
        nowSeconds: Int,
        maxDistanceMeters: Double = NAV_ALERT_APPROACH_DISTANCE_METERS,
        overdueThresholdSeconds: Int = LATE_TRANSFER_RECALC_THRESHOLD_SECONDS
    ): NavigationKeyStopDeadline? {
        val location = userLocation ?: return null
        val keyStops = buildNavigationKeyStopDeadlines(journey)
        if (keyStops.isEmpty()) return null

        val nearest = keyStops.minByOrNull { stop ->
            GeometryUtils.distanceMeters(
                lat1 = location.latitude,
                lon1 = location.longitude,
                lat2 = stop.lat,
                lon2 = stop.lon
            )
        } ?: return null

        val nearestDistance = GeometryUtils.distanceMeters(
            lat1 = location.latitude,
            lon1 = location.longitude,
            lat2 = nearest.lat,
            lon2 = nearest.lon
        )
        if (nearestDistance > maxDistanceMeters) return null

        val reference = journey.departureTime
        val nowNormalized = normalizeTimeAroundReference(nowSeconds, reference)
        var deadlineNormalized = normalizeTimeAroundReference(nearest.deadlineSeconds, reference)
        while (deadlineNormalized < nowNormalized - 12 * 3600) {
            deadlineNormalized += 24 * 3600
        }

        return if (nowNormalized >= deadlineNormalized + overdueThresholdSeconds) {
            nearest
        } else {
            null
        }
    }

    fun findNearestJourneyStopCandidate(
        journey: JourneyResult,
        userLocation: LatLng?
    ): JourneyStopCandidate? {
        if (userLocation == null) return null

        val candidates = mutableListOf<JourneyStopCandidate>()
        journey.legs.filterNot { it.isWalking }.forEachIndexed { legIndex, leg ->
            if (isValidJourneyCoordinate(leg.fromLat, leg.fromLon)) {
                candidates += JourneyStopCandidate(
                    legIndex = legIndex,
                    isLegEnd = false,
                    lat = leg.fromLat,
                    lon = leg.fromLon
                )
            }
            leg.intermediateStops.forEach { stop ->
                if (isValidJourneyCoordinate(stop.lat, stop.lon)) {
                    candidates += JourneyStopCandidate(
                        legIndex = legIndex,
                        isLegEnd = false,
                        lat = stop.lat,
                        lon = stop.lon
                    )
                }
            }
            if (isValidJourneyCoordinate(leg.toLat, leg.toLon)) {
                candidates += JourneyStopCandidate(
                    legIndex = legIndex,
                    isLegEnd = true,
                    lat = leg.toLat,
                    lon = leg.toLon
                )
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

    fun findUpcomingNonWalkingLeg(
        journey: JourneyResult,
        currentLeg: JourneyLeg,
        offsetFromCurrent: Int
    ): JourneyLeg? {
        val nonWalkingLegs = journey.legs.filterNot { it.isWalking }
        val currentIndex = nonWalkingLegs.indexOfFirst { leg -> isSameJourneyLeg(leg, currentLeg) }
        if (currentIndex == -1) return null
        return nonWalkingLegs.getOrNull(currentIndex + offsetFromCurrent)
    }

    fun getCurrentAndNextNavigationLeg(
        journey: JourneyResult,
        nowSeconds: Int,
        userLocation: LatLng?
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

    fun isAtCurrentLegTransferStop(
        journey: JourneyResult,
        currentLeg: JourneyLeg,
        userLocation: LatLng?
    ): Boolean {
        val nearestStop = findNearestJourneyStopCandidate(journey, userLocation) ?: return false
        val nonWalkingLegs = journey.legs.filterNot { it.isWalking }
        val currentLegIndex = nonWalkingLegs.indexOfFirst { leg -> isSameJourneyLeg(leg, currentLeg) }
        if (currentLegIndex == -1 || currentLegIndex >= nonWalkingLegs.lastIndex) return false
        return nearestStop.legIndex == currentLegIndex && nearestStop.isLegEnd
    }

    fun isNearestJourneyStopTerminus(
        journey: JourneyResult,
        userLocation: LatLng?
    ): Boolean {
        if (userLocation == null) return false

        val stops = mutableListOf<LatLng>()
        journey.legs.filterNot { it.isWalking }.forEach { leg ->
            if (isValidJourneyCoordinate(leg.fromLat, leg.fromLon)) {
                stops.add(LatLng(leg.fromLat, leg.fromLon))
            }
            leg.intermediateStops.forEach { stop ->
                if (isValidJourneyCoordinate(stop.lat, stop.lon)) {
                    stops.add(LatLng(stop.lat, stop.lon))
                }
            }
            if (isValidJourneyCoordinate(leg.toLat, leg.toLon)) {
                stops.add(LatLng(leg.toLat, leg.toLon))
            }
        }
        if (stops.isEmpty()) return false

        val nearestIndex = stops.indices.minByOrNull { index ->
            GeometryUtils.squaredDistance(
                lat1 = userLocation.latitude,
                lon1 = userLocation.longitude,
                lat2 = stops[index].latitude,
                lon2 = stops[index].longitude
            )
        } ?: return false

        return nearestIndex == stops.lastIndex
    }

    fun isSameJourneyLeg(first: JourneyLeg, second: JourneyLeg): Boolean {
        return first.fromStopId == second.fromStopId &&
                first.toStopId == second.toStopId &&
                first.departureTime == second.departureTime &&
                first.arrivalTime == second.arrivalTime &&
                first.routeName == second.routeName
    }

    fun isValidJourneyCoordinate(lat: Double, lon: Double): Boolean {
        return lat in -90.0..90.0 && lon in -180.0..180.0 && (lat != 0.0 || lon != 0.0)
    }

    fun findAlertTypeLabel(alertTypeId: String): String {
        return AlertType.entries.firstOrNull { it.id == alertTypeId }?.label ?: "Cette alerte"
    }

    fun normalizeTimeAroundReference(timeSeconds: Int, referenceSeconds: Int): Int {
        val day = 24 * 3600
        var normalized = timeSeconds
        while (normalized < referenceSeconds - day / 2) normalized += day
        while (normalized > referenceSeconds + day / 2) normalized -= day
        return normalized
    }

    fun formatRemainingTime(
        departureTimeSeconds: Int,
        arrivalTimeSeconds: Int,
        nowSeconds: Int
    ): String {
        val secondsInDay = 24 * 3600
        val fullTripSeconds = if (arrivalTimeSeconds >= departureTimeSeconds) {
            arrivalTimeSeconds - departureTimeSeconds
        } else {
            arrivalTimeSeconds + secondsInDay - departureTimeSeconds
        }

        val elapsedSinceDeparture = if (nowSeconds >= departureTimeSeconds) {
            nowSeconds - departureTimeSeconds
        } else {
            nowSeconds + secondsInDay - departureTimeSeconds
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

    fun formatDurationUntil(
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
}
