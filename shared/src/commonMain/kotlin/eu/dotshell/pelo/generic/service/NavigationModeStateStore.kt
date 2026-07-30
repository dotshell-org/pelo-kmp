package eu.dotshell.pelo.generic.service

import eu.dotshell.pelo.platform.PlatformContext
import eu.dotshell.pelo.platform.Settings

/**
 * Persists whether navigation mode is currently active, so the foreground service and the UI
 * agree across process restarts. Backed by the cross-platform [Settings] abstraction.
 */
object NavigationModeStateStore {

    private const val PREFS_NAME = "navigation_mode_prefs"
    private const val KEY_NAV_ACTIVE = "navigation_active"

    fun setNavigationActive(context: PlatformContext, active: Boolean) {
        Settings(context, PREFS_NAME).putBoolean(KEY_NAV_ACTIVE, active)
    }

    fun isNavigationActive(context: PlatformContext): Boolean {
        return Settings(context, PREFS_NAME).getBoolean(KEY_NAV_ACTIVE, false)
    }
}
