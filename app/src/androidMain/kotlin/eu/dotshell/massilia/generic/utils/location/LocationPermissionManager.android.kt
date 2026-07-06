package eu.dotshell.massilia.generic.utils.location

import eu.dotshell.massilia.platform.PlatformContext

/**
 * Android implementation - permissions are managed by the foreground service,
 * so this is a no-op.
 */
actual object LocationPermissionManager {
    
    actual fun requestNavigationPermissions(context: PlatformContext) {
        // No-op on Android: permissions are handled by NavigationModeForegroundService
        // which requires FOREGROUND_SERVICE and location permissions in AndroidManifest
    }
    
    actual fun hasBackgroundLocationPermission(context: PlatformContext): Boolean {
        // On Android, if we reached this point, the foreground service has location permissions
        return true
    }
}
