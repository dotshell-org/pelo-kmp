package com.pelotcl.app.generic.utils.location

import com.pelotcl.app.platform.PlatformContext

/**
 * iOS best-effort stub. A real implementation would wrap CLLocationManager
 * (with Info.plist usage descriptions); deferred until iOS is an active target.
 */
actual class LocationProvider actual constructor(context: PlatformContext) {
    actual suspend fun getLastKnownLocation(): GeoPoint? = null
    actual fun startUpdates(onLocation: (GeoPoint) -> Unit) {}
    actual fun stopUpdates() {}
}
