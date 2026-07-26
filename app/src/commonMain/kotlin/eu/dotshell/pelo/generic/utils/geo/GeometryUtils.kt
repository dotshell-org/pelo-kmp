package eu.dotshell.pelo.generic.utils.geo

import eu.dotshell.pelo.generic.data.models.geojson.StopFeature
import eu.dotshell.pelo.generic.utils.location.GeoPoint
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

    /**
     * Squared distance in degrees². Only valid to compare points that share a bearing, because
     * a degree of longitude is shorter than a degree of latitude everywhere but the equator —
     * at Lyon's latitude it is worth ~0.70 of one, so east–west gaps come out ~43% too large.
     * Prefer [squaredMeters] for anything that ranks candidates lying in different directions.
     */
    fun squaredDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = lat1 - lat2
        val dLon = lon1 - lon2
        return dLat * dLat + dLon * dLon
    }

    /**
     * Squared distance in metres², on an equirectangular projection anchored at [lat1]. Cheap
     * enough for nearest-neighbour scans (no trigonometry per candidate if the caller hoists
     * [metersPerDegreeLongitude]) and, unlike [squaredDistance], directionally unbiased.
     */
    fun squaredMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dy = (lat1 - lat2) * METERS_PER_DEGREE_LATITUDE
        val dx = (lon1 - lon2) * metersPerDegreeLongitude(lat1)
        return (dx * dx) + (dy * dy)
    }

    /** Length of one degree of longitude at [latitude], in metres. */
    fun metersPerDegreeLongitude(latitude: Double): Double =
        METERS_PER_DEGREE_LATITUDE * cos(latitude.toRadians())

    /**
     * Signed smallest rotation from [from] to [to], in ]-180, 180]. Used to keep a heading
     * continuous across the 360°/0° seam: interpolating 350° → 10° the naive way spins the
     * map almost a full turn the wrong way.
     */
    fun shortestAngleDelta(from: Double, to: Double): Double {
        var delta = (to - from) % 360.0
        if (delta > 180.0) delta -= 360.0
        if (delta <= -180.0) delta += 360.0
        return delta
    }

    /**
     * The path segment the user is travelling along, as (projected position, look-ahead point).
     *
     * [maxSnapMeters] caps how far off the path a fix may be and still snap to it; beyond that
     * there is no meaningful "along the route" direction to report and the result is null rather
     * than an arbitrary far-away segment.
     */
    fun findNavigationAxisSegment(
        userLocation: GeoPoint,
        pathPoints: List<GeoPoint>,
        maxSnapMeters: Double = DEFAULT_PATH_SNAP_METERS
    ): Pair<GeoPoint, GeoPoint>? {
        if (pathPoints.size < 2) return null

        // Work in metres on a local planar frame so the projection is not skewed by the
        // latitude-dependent length of a degree of longitude.
        val lonScale = metersPerDegreeLongitude(userLocation.latitude)
        val latScale = METERS_PER_DEGREE_LATITUDE

        var bestDistanceSq = Double.MAX_VALUE
        var bestProjectedPoint: GeoPoint? = null
        var bestNextPoint: GeoPoint? = null

        for (index in 0 until pathPoints.lastIndex) {
            val start = pathPoints[index]
            val end = pathPoints[index + 1]
            val dx = (end.longitude - start.longitude) * lonScale
            val dy = (end.latitude - start.latitude) * latScale
            val lengthSq = (dx * dx) + (dy * dy)
            if (lengthSq <= 1e-6) continue

            val ux = (userLocation.longitude - start.longitude) * lonScale
            val uy = (userLocation.latitude - start.latitude) * latScale
            val t = ((ux * dx) + (uy * dy)) / lengthSq
            val clampedT = t.coerceIn(0.0, 1.0)
            val projLon = start.longitude + (clampedT * (end.longitude - start.longitude))
            val projLat = start.latitude + (clampedT * (end.latitude - start.latitude))

            val distanceSq = squaredMeters(
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

        if (bestDistanceSq > maxSnapMeters * maxSnapMeters) return null
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
                val distance = squaredMeters(
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

    private const val METERS_PER_DEGREE_LATITUDE = 111_320.0
    private const val DEFAULT_PATH_SNAP_METERS = 250.0
}
