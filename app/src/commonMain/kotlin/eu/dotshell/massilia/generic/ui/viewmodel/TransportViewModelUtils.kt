package eu.dotshell.massilia.generic.ui.viewmodel

import eu.dotshell.massilia.generic.data.models.geojson.StopFeature
import eu.dotshell.massilia.generic.data.network.transport.TransportLineRules
import eu.dotshell.massilia.generic.utils.graphics.LineIconResolver

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
