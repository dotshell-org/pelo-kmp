package eu.dotshell.pelo.generic.utils.location

import eu.dotshell.pelo.platform.PlatformContext

/** A simple latitude/longitude pair, decoupled from any map SDK type. */
data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * Cross-platform device location access.
 * Android wraps the fused location provider; iOS wraps CLLocationManager
 * (currently a best-effort stub). Callers must hold location permission.
 */
expect class LocationProvider(context: PlatformContext) {

    /** Last known location (fast, system-cached), or null if unavailable. */
    suspend fun getLastKnownLocation(): GeoPoint?

    /**
     * Start receiving continuous location updates.
     *
     * [intervalMillis] is the desired cadence. Navigation wants roughly a fix a second so the
     * camera tracks smoothly; browsing the map is happy with far less and much cheaper.
     */
    fun startUpdates(intervalMillis: Long = 5_000L, onLocation: (GeoPoint) -> Unit)

    /** Stop receiving location updates. */
    fun stopUpdates()

    /**
     * Switch the stream between browsing and navigation grade. On iOS this raises the accuracy
     * and, with Always authorization, keeps fixes coming while backgrounded; on Android the
     * foreground service covers that, so it is a no-op.
     */
    fun setNavigationMode(enabled: Boolean)
}
