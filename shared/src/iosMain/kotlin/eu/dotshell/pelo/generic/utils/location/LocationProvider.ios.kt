@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package eu.dotshell.pelo.generic.utils.location

import eu.dotshell.pelo.platform.PlatformContext
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLLocationAccuracyBestForNavigation
import platform.CoreLocation.kCLLocationAccuracyNearestTenMeters
import platform.CoreLocation.CLActivityTypeOther
import platform.CoreLocation.CLActivityTypeOtherNavigation
import platform.darwin.NSObject

/**
 * iOS actual backed by CLLocationManager. Requires `NSLocationWhenInUseUsageDescription` (and,
 * for navigation, `NSLocationAlwaysAndWhenInUseUsageDescription` plus the `location` background
 * mode) in Info.plist.
 *
 * Navigation-grade settings are applied to *this* manager — the one actually delivering fixes.
 * They used to be requested on a throwaway second CLLocationManager owned by
 * [LocationPermissionManager], so the accuracy upgrade never reached the running stream.
 */
actual class LocationProvider actual constructor(context: PlatformContext) {

    private var onLocation: ((GeoPoint) -> Unit)? = null
    private var isNavigating = false

    private val manager = CLLocationManager()

    private val locationDelegate = object : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
            val location = didUpdateLocations.lastOrNull() as? CLLocation ?: return
            onLocation?.invoke(location.toGeoPoint())
        }

        override fun locationManager(
            manager: CLLocationManager,
            didChangeAuthorizationStatus: CLAuthorizationStatus
        ) {
            applyMode()
        }
    }

    init {
        manager.delegate = locationDelegate
        manager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
        manager.requestWhenInUseAuthorization()
    }

    actual suspend fun getLastKnownLocation(): GeoPoint? = manager.location?.toGeoPoint()

    actual fun startUpdates(intervalMillis: Long, onLocation: (GeoPoint) -> Unit) {
        this.onLocation = onLocation
        // CoreLocation has no update interval; it throttles by distance instead. A metre of
        // movement at walking pace lands around one callback a second, which is what the
        // navigation camera wants; browsing gets a coarser filter and a cheaper stream.
        manager.distanceFilter = if (intervalMillis <= 2_000L) 1.0 else 10.0
        applyMode()
        manager.startUpdatingLocation()
    }

    actual fun stopUpdates() {
        manager.stopUpdatingLocation()
        onLocation = null
    }

    /**
     * Switch the stream between browsing and navigation. Navigation asks for Always
     * authorization and, once granted, keeps updates flowing with the app backgrounded — the
     * iOS counterpart of Android's foreground service.
     */
    actual fun setNavigationMode(enabled: Boolean) {
        if (isNavigating == enabled) return
        isNavigating = enabled
        if (enabled) manager.requestAlwaysAuthorization()
        applyMode()
    }

    private fun applyMode() {
        manager.desiredAccuracy = if (isNavigating) {
            kCLLocationAccuracyBestForNavigation
        } else {
            kCLLocationAccuracyNearestTenMeters
        }
        manager.activityType = if (isNavigating) CLActivityTypeOtherNavigation else CLActivityTypeOther
        // Setting this without the `location` UIBackgroundMode raises at runtime, and it is only
        // honoured under Always authorization — so gate on both conditions actually holding.
        val allowsBackground = isNavigating &&
            manager.authorizationStatus == kCLAuthorizationStatusAuthorizedAlways
        manager.allowsBackgroundLocationUpdates = allowsBackground
        manager.pausesLocationUpdatesAutomatically = !isNavigating
    }
}

private fun CLLocation.toGeoPoint(): GeoPoint = coordinate.useContents {
    GeoPoint(latitude = latitude, longitude = longitude)
}
