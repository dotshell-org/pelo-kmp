package eu.dotshell.massilia.generic.utils.location

import eu.dotshell.massilia.platform.PlatformContext

/**
 * iOS implementation - requests Always authorization for navigation mode
 * to enable background location updates.
 */
actual object LocationPermissionManager {
    
    private var locationProvider: LocationProvider? = null
    
    actual fun requestNavigationPermissions(context: PlatformContext) {
        if (locationProvider == null) {
            locationProvider = LocationProvider(context)
        }
        locationProvider?.requestAlwaysAuthorization()
    }
    
    actual fun hasBackgroundLocationPermission(context: PlatformContext): Boolean {
        // On iOS, we can't easily check the authorization status without more bindings
        // For now, assume we have permission if we requested it
        return true
    }
}
