package eu.dotshell.massilia.generic.utils.geo

import eu.dotshell.massilia.generic.data.models.geojson.StopFeature
import eu.dotshell.massilia.generic.utils.location.GeoPoint
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure geometry/time helpers, decoupled from any map SDK. Uses the neutral [GeoPoint]
 * lat/lng carrier (not `org.maplibre.android.geometry.LatLng`) and `kotlin.math` so it
 * compiles on every target.
 */
object GeometryUtils {

    private fun Double.toRadians(): Double = this * PI / 180.0
    private fun Double.toDegrees(): Double = this * 180.0 / PI

    fun currentTimeInSeconds(): Int {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return now.hour * 3600 + now.minute * 60 + now.second
    }

    fun computeBearingDegrees(from: GeoPoint, to: GeoPoint): Double {
        val fromLat = from.latitude.toRadians()
        val fromLon = from.longitude.toRadians()
        val toLat = to.latitude.toRadians()
        val toLon = to.longitude.toRadians()
        val dLon = toLon - fromLon

        val y = sin(dLon) * cos(toLat)
        val x = cos(fromLat) * sin(toLat) - sin(fromLat) * cos(toLat) * cos(dLon)
        val bearing = atan2(y, x).toDegrees()
        return (bearing + 360.0) % 360.0
    }

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6_371_000.0
        val dLat = (lat2 - lat1).toRadians()
        val dLon = (lon2 - lon1).toRadians()
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1.toRadians()) *
                cos(lat2.toRadians()) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    fun squaredDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = lat1 - lat2
        val dLon = lon1 - lon2
        return dLat * dLat + dLon * dLon
    }

    fun findNavigationAxisSegment(
        userLocation: GeoPoint,
        pathPoints: List<GeoPoint>
    ): Pair<GeoPoint, GeoPoint>? {
        if (pathPoints.size < 2) return null

        var bestDistanceSq = Double.MAX_VALUE
        var bestProjectedPoint: GeoPoint? = null
        var bestNextPoint: GeoPoint? = null

        for (index in 0 until pathPoints.lastIndex) {
            val start = pathPoints[index]
            val end = pathPoints[index + 1]
            val dx = end.longitude - start.longitude
            val dy = end.latitude - start.latitude
            val lengthSq = (dx * dx) + (dy * dy)
            if (lengthSq <= 1e-14) continue

            val ux = userLocation.longitude - start.longitude
            val uy = userLocation.latitude - start.latitude
            val t = ((ux * dx) + (uy * dy)) / lengthSq
            val clampedT = t.coerceIn(0.0, 1.0)
            val projLon = start.longitude + (clampedT * dx)
            val projLat = start.latitude + (clampedT * dy)

            val distanceSq = squaredDistance(
                lat1 = userLocation.latitude,
                lon1 = userLocation.longitude,
                lat2 = projLat,
                lon2 = projLon
            )

            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq
                bestProjectedPoint = GeoPoint(projLat, projLon)
                bestNextPoint = if (clampedT >= 0.999 && index + 2 <= pathPoints.lastIndex) {
                    pathPoints[index + 2]
                } else {
                    end
                }
            }
        }

        val from = bestProjectedPoint ?: return null
        val to = bestNextPoint ?: return null
        if (from.latitude == to.latitude && from.longitude == to.longitude) return null
        return from to to
    }

    fun findNearestStopName(userLocation: GeoPoint, stops: List<StopFeature>): String? {
        var nearestName: String? = null
        var nearestDistance = Double.MAX_VALUE

        stops.forEach { stop ->
            val coordinates = stop.geometry.coordinates
            if (coordinates.size >= 2) {
                val lon = coordinates[0]
                val lat = coordinates[1]
                val distance = squaredDistance(
                    lat1 = userLocation.latitude,
                    lon1 = userLocation.longitude,
                    lat2 = lat,
                    lon2 = lon
                )
                if (distance < nearestDistance) {
                    nearestDistance = distance
                    nearestName = stop.properties.nom
                }
            }
        }

        return nearestName
    }
}
