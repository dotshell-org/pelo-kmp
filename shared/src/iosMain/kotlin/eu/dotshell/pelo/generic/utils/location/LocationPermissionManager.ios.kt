package eu.dotshell.pelo.generic.utils.location

import eu.dotshell.pelo.platform.PlatformContext
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse

/**
 * iOS implementation. Authorization is process-wide, so a single CLLocationManager is enough to
 * prompt for it and to read it back — the running location stream keeps its own manager and
 * configures its own accuracy (see LocationProvider.setNavigationMode).
 */
actual object LocationPermissionManager {

    private val authorizationManager: CLLocationManager by lazy { CLLocationManager() }

    actual fun requestNavigationPermissions(context: PlatformContext) {
        authorizationManager.requestAlwaysAuthorization()
    }

    actual fun hasForegroundLocationPermission(context: PlatformContext): Boolean {
        val status = authorizationManager.authorizationStatus
        return status == kCLAuthorizationStatusAuthorizedWhenInUse ||
            status == kCLAuthorizationStatusAuthorizedAlways
    }

    actual fun hasBackgroundLocationPermission(context: PlatformContext): Boolean =
        authorizationManager.authorizationStatus == kCLAuthorizationStatusAuthorizedAlways
}
