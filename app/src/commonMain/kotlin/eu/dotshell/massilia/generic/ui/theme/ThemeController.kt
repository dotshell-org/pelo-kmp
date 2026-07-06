package eu.dotshell.massilia.generic.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import eu.dotshell.massilia.generic.data.repository.offline.theme.ThemeMode

/**
 * Holds the current app [ThemeMode] and a setter to change it.
 *
 * Provided via [LocalThemeController] in `App()` — above [MassiliaTheme] — so that any
 * screen (e.g. the theme settings screen) can read/update the mode, and changing it
 * recomposes the theme wrapper.
 */
class ThemeController(
    val themeMode: ThemeMode,
    val setThemeMode: (ThemeMode) -> Unit,
)

val LocalThemeController = staticCompositionLocalOf<ThemeController> {
    error("LocalThemeController not provided")
}
