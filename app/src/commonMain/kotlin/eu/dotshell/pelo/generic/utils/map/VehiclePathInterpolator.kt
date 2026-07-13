package eu.dotshell.pelo.generic.utils.map

import eu.dotshell.pelo.generic.data.config.LineSpeedBaselineData
import eu.dotshell.pelo.generic.data.models.realtime.vehiclepositions.SimpleVehiclePosition
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Glides live vehicle markers along their line's trace between two feed ticks
 * (the GTFS-RT feed refreshes every ~10 s, so raw updates jump).
 *
 * A straight-line interpolation cuts corners — vehicles crossing buildings —
 * and orthogonally projecting each *intermediate* point can snap to the wrong
 * branch on hairpin sections. So the two endpoints are projected on the trace
 * once, and the marker follows the trace between the two curvilinear
 * abscissas: the robust variant of the same idea. Vehicles too far from any
 * trace (deviation, unmapped detour) fall back to the straight segment.
 *
 * @param traces line name (uppercase) -> list of paths, each path being a
 *   list of `[lon, lat]` coordinates (GeoJSON order).
 * @param speedBaseline measured per-line speeds and per-direction progression
 *   signs (tools/build_vehicle_speed_baseline.py): vehicles seen for the
 *   first time dead-reckon along their trace at the line's commercial speed
 *   instead of standing still until the second tick.
 */
class VehiclePathInterpolator(
    traces: Map<String, List<List<List<Double>>>>,
    private val speedBaseline: Map<String, LineSpeedBaselineData> = emptyMap()
) {

    private val polylinesByLine: Map<String, List<Polyline>> =
        traces.entries.associate { (name, paths) ->
            name.trim().uppercase() to paths.filter { it.size >= 2 }.map { Polyline(it) }
        }

    /** Precomputed interpolation for one vehicle between two ticks. */
    sealed interface Plan {
        /** Position at [fraction] in 0..1 as (latitude, longitude). */
        fun at(fraction: Double): Pair<Double, Double>
    }

    private class Static(private val lat: Double, private val lon: Double) : Plan {
        override fun at(fraction: Double) = lat to lon
    }

    private class Linear(
        private val fromLat: Double, private val fromLon: Double,
        private val toLat: Double, private val toLon: Double
    ) : Plan {
        override fun at(fraction: Double) = Pair(
            fromLat + (toLat - fromLat) * fraction,
            fromLon + (toLon - fromLon) * fraction
        )
    }

    private class AlongPath(
        private val polyline: Polyline,
        private val s0: Double,
        private val s1: Double
    ) : Plan {
        override fun at(fraction: Double) = polyline.pointAt(s0 + (s1 - s0) * fraction)
    }

    fun plan(from: SimpleVehiclePosition?, to: SimpleVehiclePosition): Plan {
        if (from == null) return firstAppearancePlan(to)
        if (from.latitude == to.latitude && from.longitude == to.longitude) {
            return Static(to.latitude, to.longitude)
        }
        val linear = Linear(from.latitude, from.longitude, to.latitude, to.longitude)
        val polylines = polylinesByLine[to.lineName.trim().uppercase()] ?: return linear

        // Anchor on the destination's best projection, then take the START
        // projection nearest to it IN ABSCISSA among all close-enough
        // candidates: on overlapping passes or branch joins, the purely
        // nearest projection can sit kilometers away along the path, which
        // made vehicles race down their line. Prefer the interpretation
        // where the vehicle moved least.
        var bestPolyline: Polyline? = null
        var bestS0 = 0.0
        var bestS1 = 0.0
        var bestGlide = Double.MAX_VALUE
        for (polyline in polylines) {
            val p1 = polyline.project(to.longitude, to.latitude)
            if (p1.distanceMeters > MAX_SNAP_METERS) continue
            val s0 = polyline.projectNearestAbscissa(
                from.longitude, from.latitude, p1.abscissa, MAX_SNAP_METERS
            ) ?: continue
            val glide = abs(p1.abscissa - s0)
            if (glide < bestGlide) {
                bestGlide = glide
                bestPolyline = polyline
                bestS0 = s0
                bestS1 = p1.abscissa
            }
        }
        val polyline = bestPolyline ?: return linear

        // Plausibility: a vehicle covers at most ~2 km per feed tick, and a
        // path much longer than the straight segment means the projections
        // landed on the wrong branch — glide straight instead.
        val straightMeters = straightDistanceMeters(from, to)
        if (bestGlide > MAX_GLIDE_METERS || bestGlide > max(4.0 * straightMeters, 400.0)) return linear
        if (bestS0 == bestS1) return Static(to.latitude, to.longitude)
        return AlongPath(polyline, bestS0, bestS1)
    }

    /**
     * A vehicle seen for the first time (live just enabled, or pulling out)
     * has no previous position: dead-reckon it forward along its trace at
     * the line's measured commercial speed until the next tick corrects it.
     * Requires a measured progression sign for (direction, path) — otherwise
     * the marker stays put rather than risking a backwards glide.
     */
    private fun firstAppearancePlan(to: SimpleVehiclePosition): Plan {
        val static = Static(to.latitude, to.longitude)
        val line = to.lineName.trim().uppercase()
        val baseline = speedBaseline[line] ?: return static
        val direction = to.direction?.trim().takeUnless { it.isNullOrEmpty() } ?: return static
        val polylines = polylinesByLine[line] ?: return static

        var bestIndex = -1
        var bestAbscissa = 0.0
        var bestDistance = Double.MAX_VALUE
        polylines.forEachIndexed { index, polyline ->
            val projection = polyline.project(to.longitude, to.latitude)
            if (projection.distanceMeters < bestDistance) {
                bestDistance = projection.distanceMeters
                bestIndex = index
                bestAbscissa = projection.abscissa
            }
        }
        if (bestIndex < 0 || bestDistance > MAX_SNAP_METERS) return static
        val sign = baseline.signs[direction]?.get(bestIndex.toString()) ?: return static
        if (sign == 0) return static
        // pointAt clamps: a vehicle reaching the terminus simply stops there
        val target = bestAbscissa + sign * baseline.speedMps * DEAD_RECKON_SECONDS
        return AlongPath(polylines[bestIndex], bestAbscissa, target)
    }

    private fun straightDistanceMeters(from: SimpleVehiclePosition, to: SimpleVehiclePosition): Double {
        val metersPerDegLon = METERS_PER_DEG_LAT * cos(from.latitude * PI / 180.0)
        val dx = (to.longitude - from.longitude) * metersPerDegLon
        val dy = (to.latitude - from.latitude) * METERS_PER_DEG_LAT
        return sqrt(dx * dx + dy * dy)
    }

    private class Projection(val abscissa: Double, val distanceMeters: Double)

    /**
     * A path in a local equirectangular metric (meters), with cumulative
     * lengths for O(log n) point-at and O(n) projection.
     */
    private class Polyline(path: List<List<Double>>) {
        private val metersPerDegLon: Double = METERS_PER_DEG_LAT * cos(path.first()[1] * PI / 180.0)
        private val xs = DoubleArray(path.size)
        private val ys = DoubleArray(path.size)
        private val cumulative = DoubleArray(path.size)

        init {
            for (i in path.indices) {
                xs[i] = path[i][0] * metersPerDegLon
                ys[i] = path[i][1] * METERS_PER_DEG_LAT
                if (i > 0) {
                    val dx = xs[i] - xs[i - 1]
                    val dy = ys[i] - ys[i - 1]
                    cumulative[i] = cumulative[i - 1] + sqrt(dx * dx + dy * dy)
                }
            }
        }

        fun project(lon: Double, lat: Double): Projection {
            val px = lon * metersPerDegLon
            val py = lat * METERS_PER_DEG_LAT
            var bestDistSq = Double.MAX_VALUE
            var bestS = 0.0
            for (i in 0 until xs.size - 1) {
                val ax = xs[i]; val ay = ys[i]
                val bx = xs[i + 1]; val by = ys[i + 1]
                val abx = bx - ax; val aby = by - ay
                val lengthSq = abx * abx + aby * aby
                val t = if (lengthSq == 0.0) 0.0
                else (((px - ax) * abx + (py - ay) * aby) / lengthSq).coerceIn(0.0, 1.0)
                val cx = ax + abx * t; val cy = ay + aby * t
                val dx = px - cx; val dy = py - cy
                val distSq = dx * dx + dy * dy
                if (distSq < bestDistSq) {
                    bestDistSq = distSq
                    bestS = cumulative[i] + sqrt(lengthSq) * t
                }
            }
            return Projection(bestS, sqrt(bestDistSq))
        }

        /**
         * Among every projection closer than [maxMeters], returns the abscissa
         * nearest to [referenceAbscissa] — disambiguates overlapping passes.
         */
        fun projectNearestAbscissa(
            lon: Double,
            lat: Double,
            referenceAbscissa: Double,
            maxMeters: Double
        ): Double? {
            val px = lon * metersPerDegLon
            val py = lat * METERS_PER_DEG_LAT
            val maxDistSq = maxMeters * maxMeters
            var bestS: Double? = null
            var bestDelta = Double.MAX_VALUE
            for (i in 0 until xs.size - 1) {
                val ax = xs[i]; val ay = ys[i]
                val bx = xs[i + 1]; val by = ys[i + 1]
                val abx = bx - ax; val aby = by - ay
                val lengthSq = abx * abx + aby * aby
                val t = if (lengthSq == 0.0) 0.0
                else (((px - ax) * abx + (py - ay) * aby) / lengthSq).coerceIn(0.0, 1.0)
                val cx = ax + abx * t; val cy = ay + aby * t
                val dx = px - cx; val dy = py - cy
                if (dx * dx + dy * dy > maxDistSq) continue
                val s = cumulative[i] + sqrt(lengthSq) * t
                val delta = abs(s - referenceAbscissa)
                if (delta < bestDelta) {
                    bestDelta = delta
                    bestS = s
                }
            }
            return bestS
        }

        /** Point at curvilinear abscissa [s] (clamped) as (latitude, longitude). */
        fun pointAt(s: Double): Pair<Double, Double> {
            val clamped = s.coerceIn(0.0, cumulative.last())
            var index = cumulative.asList().binarySearch { it.compareTo(clamped) }
            if (index < 0) index = (-index - 1) - 1
            index = index.coerceIn(0, xs.size - 2)
            val segmentLength = cumulative[index + 1] - cumulative[index]
            val t = if (segmentLength == 0.0) 0.0 else (clamped - cumulative[index]) / segmentLength
            val x = xs[index] + (xs[index + 1] - xs[index]) * t
            val y = ys[index] + (ys[index + 1] - ys[index]) * t
            return Pair(y / METERS_PER_DEG_LAT, x / metersPerDegLon)
        }
    }

    companion object {
        private const val METERS_PER_DEG_LAT = 111_132.0

        // Beyond this distance from every path of its line, a vehicle is
        // considered off-route (deviation) and glides on a straight segment.
        private const val MAX_SNAP_METERS = 120.0

        // No vehicle covers more than ~2 km between two feed ticks (1 min);
        // a longer along-path glide means a mis-projection.
        private const val MAX_GLIDE_METERS = 2_000.0

        // Matches the glide duration in App.kt: fraction 1.0 = 55 s of travel.
        private const val DEAD_RECKON_SECONDS = 55.0
    }
}
