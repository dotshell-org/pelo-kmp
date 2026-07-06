@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package eu.dotshell.massilia.generic.utils.location

import eu.dotshell.massilia.platform.PlatformContext
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLLocationAccuracyBestForNavigation
import platform.CoreLocation.kCLLocationAccuracyNearestTenMeters
import platform.darwin.NSObject

/**
 * iOS actual backed by CLLocationManager. Requires both `NSLocationWhenInUseUsageDescription`
 * and `NSLocationAlwaysAndWhenInUseUsageDescription` in Info.plist (set by the iosApp project).
 * When-in-use authorization is requested on construction; callers can request always authorization
 * for navigation mode using requestAlwaysAuthorization().
 */
actual class LocationProvider actual constructor(context: PlatformContext) {

    private var onLocation: ((GeoPoint) -> Unit)? = null
    private var usesAlwaysAuthorization = false

    private val manager = CLLocationManager()

    private val locationDelegate = object : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
            val location = didUpdateLocations.lastOrNull() as? CLLocation ?: return
            onLocation?.invoke(location.toGeoPoint())
        }

        override fun locationManager(manager: CLLocationManager, didChangeAuthorizationStatus: CLAuthorizationStatus) {
            // Update desired accuracy based on authorization level
            manager.desiredAccuracy = if (usesAlwaysAuthorization) {
                kCLLocationAccuracyBestForNavigation
            } else {
                kCLLocationAccuracyNearestTenMeters
            }
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
        manager.desiredAccuracy = if (usesAlwaysAuthorization) {
            kCLLocationAccuracyBestForNavigation
        } else {
            kCLLocationAccuracyNearestTenMeters
        }
        manager.startUpdatingLocation()
    }

    actual fun stopUpdates() {
        manager.stopUpdatingLocation()
        onLocation = null
    }

    /**
     * Request always authorization for background location updates.
     * Required for navigation mode to continue working when app is in background.
     */
    fun requestAlwaysAuthorization() {
        usesAlwaysAuthorization = true
        manager.requestAlwaysAuthorization()
    }
}

private fun CLLocation.toGeoPoint(): GeoPoint = coordinate.useContents {
    GeoPoint(latitude = latitude, longitude = longitude)
}
