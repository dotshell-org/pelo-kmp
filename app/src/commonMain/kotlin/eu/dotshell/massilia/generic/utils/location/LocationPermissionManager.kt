package eu.dotshell.massilia.generic.utils.location

import eu.dotshell.massilia.platform.PlatformContext

/**
 * Cross-platform interface for managing location permissions.
 * On iOS, this handles requesting Always authorization for navigation mode.
 * On Android, permissions are handled by the foreground service.
 */
expect object LocationPermissionManager {
    
    /**
     * Request elevated location permissions if needed for navigation.
     * On iOS, this requests Always authorization.
     * On Android, this is a no-op as permissions are managed by the foreground service.
     */
    fun requestNavigationPermissions(context: PlatformContext)
    
    /**
     * Check if the app has sufficient permissions for background location updates.
     */
    fun hasBackgroundLocationPermission(context: PlatformContext): Boolean
}
