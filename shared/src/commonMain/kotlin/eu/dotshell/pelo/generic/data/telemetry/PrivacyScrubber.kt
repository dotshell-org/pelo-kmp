package eu.dotshell.pelo.generic.data.telemetry

/**
 * Pure-Kotlin geographic privacy bucketing.
 *
 * We use a [geohash](https://en.wikipedia.org/wiki/Geohash) encoding rather than Uber's H3
 * because the official H3 Java library ships native binaries that don't target Android ABIs.
 * For the privacy goal of this codebase (k-anonymity of free-text addresses) geohash is
 * functionally equivalent: a deterministic, irreversible-by-design bucketing where many
 * distinct addresses collide into the same cell.
 *
 * Bucket sizes for the precisions we expose:
 *  - precision 6 → ~1.2 km × ~0.6 km (≈ 460 m on average at Lyon's latitude) ← default
 *  - precision 7 → ~150 m × ~150 m (precise, not used for telemetry — kept for future)
 *
 * The bucket label uses the standard base-32 alphabet (excludes ambiguous chars: a/i/l/o).
 */
object PrivacyScrubber {

    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

    /**
     * Default precision used for telemetry payloads. precision 6 buckets ≈ 600 m, which exceeds
     * the typical accuracy of consumer GPS while keeping enough granularity to study
     * neighborhood-level demand patterns.
     */
    const val DEFAULT_PRECISION: Int = 6

    /**
     * Convert a latitude/longitude pair to a geohash of the given [precision].
     *
     * @throws IllegalArgumentException if precision is outside [1, 12].
     */
    fun geohash(lat: Double, lng: Double, precision: Int = DEFAULT_PRECISION): String {
        require(precision in 1..12) { "Geohash precision must be in [1, 12], got $precision" }
        require(lat in -90.0..90.0) { "Latitude out of range: $lat" }
        require(lng in -180.0..180.0) { "Longitude out of range: $lng" }

        var minLat = -90.0
        var maxLat = 90.0
        var minLng = -180.0
        var maxLng = 180.0

        val result = StringBuilder(precision)
        var bits = 0
        var ch = 0
        var even = true

        while (result.length < precision) {
            if (even) {
                val mid = (minLng + maxLng) / 2.0
                if (lng >= mid) {
                    ch = (ch shl 1) or 1
                    minLng = mid
                } else {
                    ch = ch shl 1
                    maxLng = mid
                }
            } else {
                val mid = (minLat + maxLat) / 2.0
                if (lat >= mid) {
                    ch = (ch shl 1) or 1
                    minLat = mid
                } else {
                    ch = ch shl 1
                    maxLat = mid
                }
            }
            even = !even
            bits++
            if (bits == 5) {
                result.append(BASE32[ch])
                bits = 0
                ch = 0
            }
        }
        return result.toString()
    }

    /**
     * Helper for `PlaceRef.h3 = …` style construction. Despite the field name being `h3` on
     * the wire (kept for forward-compatibility with a future H3 migration), the value is a
     * geohash today.
     */
    fun bucket(lat: Double, lng: Double): String = geohash(lat, lng)
}
