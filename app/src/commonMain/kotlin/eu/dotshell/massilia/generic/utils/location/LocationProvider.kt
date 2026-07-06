package eu.dotshell.massilia.generic.utils.location

import eu.dotshell.massilia.platform.PlatformContext

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

    /** Start receiving continuous location updates. */
    fun startUpdates(onLocation: (GeoPoint) -> Unit)

    /** Stop receiving location updates. */
    fun stopUpdates()
}
