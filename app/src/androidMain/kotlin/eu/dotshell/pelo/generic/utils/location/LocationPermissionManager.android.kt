package eu.dotshell.pelo.generic.utils.location

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import eu.dotshell.pelo.platform.PlatformContext

/**
 * Android implementation. The runtime prompt belongs to MainActivity; this only reports what has
 * actually been granted — the previous version answered "yes" unconditionally, which meant the
 * caller could not tell a granted permission from a denied one.
 */
actual object LocationPermissionManager {

    actual fun requestNavigationPermissions(context: PlatformContext) {
        // No-op: MainActivity owns the runtime prompt, and NavigationModeForegroundService
        // (foregroundServiceType="location") covers background access once started in foreground.
    }

    actual fun hasForegroundLocationPermission(context: PlatformContext): Boolean =
        isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            isGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    actual fun hasBackgroundLocationPermission(context: PlatformContext): Boolean =
        hasForegroundLocationPermission(context)

    private fun isGranted(context: PlatformContext, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
