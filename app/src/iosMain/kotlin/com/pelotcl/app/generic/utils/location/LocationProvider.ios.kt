@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.pelotcl.app.generic.utils.location

import com.pelotcl.app.platform.PlatformContext
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLLocationAccuracyNearestTenMeters
import platform.darwin.NSObject

/**
 * iOS actual backed by CLLocationManager. Requires `NSLocationWhenInUseUsageDescription`
 * in Info.plist (set by the iosApp project). When-in-use authorization is requested on
 * construction; callers should still hold permission before relying on updates.
 */
actual class LocationProvider actual constructor(context: PlatformContext) {

    private var onLocation: ((GeoPoint) -> Unit)? = null

    private val manager = CLLocationManager()

    private val locationDelegate = object : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
            val location = didUpdateLocations.lastOrNull() as? CLLocation ?: return
            onLocation?.invoke(location.toGeoPoint())
        }
    }

    init {
        manager.delegate = locationDelegate
        manager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
        manager.requestWhenInUseAuthorization()
    }

    actual suspend fun getLastKnownLocation(): GeoPoint? = manager.location?.toGeoPoint()

    actual fun startUpdates(onLocation: (GeoPoint) -> Unit) {
        this.onLocation = onLocation
        manager.startUpdatingLocation()
    }

    actual fun stopUpdates() {
        manager.stopUpdatingLocation()
        onLocation = null
    }
}

private fun CLLocation.toGeoPoint(): GeoPoint = coordinate.useContents {
    GeoPoint(latitude = latitude, longitude = longitude)
}
