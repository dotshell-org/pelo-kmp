package eu.dotshell.pelo.generic.ui.viewmodel

import eu.dotshell.pelo.generic.data.models.geojson.StopFeature
import eu.dotshell.pelo.generic.data.network.transport.TransportLineRules
import eu.dotshell.pelo.generic.utils.graphics.LineIconResolver

fun parseTimeToMinutes(rawTime: String): Int? {
    val clean = if (rawTime.count { it == ':' } >= 2) rawTime.substringBeforeLast(":") else rawTime
    val parts = clean.split(":")
    if (parts.size < 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (minute !in 0..59) return null
    return (hour * 60) + minute
}

fun pickNextDeparture(schedules: List<String>, currentMinutes: Int): String? {
    val unique = schedules.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    if (unique.isEmpty()) return null
    return unique.firstOrNull { time ->
        val minutes = parseTimeToMinutes(time) ?: return@firstOrNull false
        minutes >= currentMinutes
    } ?: unique.first()
}

fun normalizeStopName(stopName: String): String {
    return stopName
        .trim()
        .replace(Regex("\\s+"), " ")
        .uppercase()
}

fun parseLineCodesFromDesserte(desserte: String): List<String> {
    return LineIconResolver.parseDesserte(desserte)
}

fun parseAlertTokens(raw: String, lineRules: TransportLineRules): Set<String> {
    return raw
        .split(',', ';', '|', ' ', ':', '/', '-', '\n', '\t')
        .map { lineRules.normalizeAlertToken(it) }
        .filter { lineRules.isLikelyLineToken(it) }
        .toSet()
}

fun parseLineMentionsFromText(raw: String, lineRules: TransportLineRules): Set<String> {
    if (raw.isBlank()) return emptySet()
    val matchedSegments = Regex("(?i)\\blignes?\\b([^.!?\\n\\r]*)")
        .findAll(raw)
        .map { match -> match.groupValues.getOrNull(1).orEmpty() }
        .toList()
    return matchedSegments
        .flatMap { segment -> parseAlertTokens(segment, lineRules) }
        .toSet()
}

fun findStopByCoordinates(
    stops: List<StopFeature>,
    targetLat: Double,
    targetLon: Double,
    thresholdDistance: Double = 0.0002
): StopFeature? {
    var closestStop: StopFeature? = null
    var minDistance = Double.MAX_VALUE

    for (stop in stops) {
        val stopCoord = stop.geometry.coordinates
        if (stopCoord.size < 2) continue

        val stopLon = stopCoord[0]
        val stopLat = stopCoord[1]

        val latDiff = targetLat - stopLat
        val lonDiff = targetLon - stopLon
        val distanceSq = latDiff * latDiff + lonDiff * lonDiff

        if (distanceSq < minDistance) {
            minDistance = distanceSq
            closestStop = stop
        }
    }

    if (closestStop != null && minDistance < thresholdDistance * thresholdDistance) {
        return closestStop
    }
    return null
}

data class StopDeparturePreview(
    val lineName: String,
    val directionId: Int,
    val directionName: String,
    val nextDeparture: String
)

/**
 * Where a stop sits on a line's polyline: the index of the nearest vertex, and how far away it is
 * in metres.
 *
 * The distance is the point. A line name can resolve to several traces — directions, variants,
 * partial services — and only some of them serve a given stop. Accepting the nearest vertex at any
 * distance means an unrelated variant still yields an index, and slicing between two such indices
 * produces an arbitrary chunk of a trace that goes somewhere else entirely.
 *
 * Coordinates are GeoJSON order, [lon, lat].
 */
fun nearestVertexOnLine(
    linePoints: List<List<Double>>,
    stopCoordinate: List<Double>,
): LineVertexMatch? {
    if (linePoints.isEmpty() || stopCoordinate.size < 2) return null

    var bestIndex = -1
    var bestSquaredMeters = Double.MAX_VALUE
    for (index in linePoints.indices) {
        val point = linePoints[index]
        if (point.size < 2) continue
        val squared = eu.dotshell.pelo.generic.utils.geo.GeometryUtils.squaredMeters(
            lat1 = stopCoordinate[1],
            lon1 = stopCoordinate[0],
            lat2 = point[1],
            lon2 = point[0],
        )
        if (squared < bestSquaredMeters) {
            bestSquaredMeters = squared
            bestIndex = index
        }
    }
    if (bestIndex == -1) return null
    return LineVertexMatch(index = bestIndex, distanceMeters = kotlin.math.sqrt(bestSquaredMeters))
}

data class LineVertexMatch(val index: Int, val distanceMeters: Double)

/**
 * How far a stop may sit from a line's polyline and still be considered served by it.
 *
 * Generous on purpose: platforms sit off the centreline, and some traces are coarsely sampled.
 * Beyond this the variant simply does not go there.
 */
const val MAX_STOP_TO_LINE_METERS = 200.0
