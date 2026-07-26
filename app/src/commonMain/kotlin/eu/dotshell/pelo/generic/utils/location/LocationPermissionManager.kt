package eu.dotshell.pelo.generic.utils.location

import eu.dotshell.pelo.platform.PlatformContext

/**
 * Cross-platform view of the location authorization the app currently holds.
 */
expect object LocationPermissionManager {

    /**
     * Ask for whatever elevated authorization navigation needs. iOS requests Always (for
     * background updates); on Android the runtime prompt is owned by the host Activity.
     */
    fun requestNavigationPermissions(context: PlatformContext)

    /**
     * Can the app read the device position at all right now? Checked before entering navigation
     * mode — starting guidance without it strands the traveller on a map that never moves.
     */
    fun hasForegroundLocationPermission(context: PlatformContext): Boolean

    /** Can the app keep receiving fixes while backgrounded? */
    fun hasBackgroundLocationPermission(context: PlatformContext): Boolean
}
