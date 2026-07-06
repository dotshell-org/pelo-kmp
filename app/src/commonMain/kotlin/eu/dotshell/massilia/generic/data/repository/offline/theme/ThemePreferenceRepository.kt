package eu.dotshell.massilia.generic.data.repository.offline.theme

import eu.dotshell.massilia.platform.PlatformContext
import eu.dotshell.massilia.platform.Settings

/**
 * User-selectable app theme mode.
 *  - [AUTO]  follow the system dark/light setting
 *  - [LIGHT] force light theme
 *  - [DARK]  force dark theme
 */
enum class ThemeMode { AUTO, LIGHT, DARK }

/**
 * Repository for the persisted app theme preference.
 * Multiplatform: uses the [Settings] abstraction (SharedPreferences / NSUserDefaults),
 * mirroring [eu.dotshell.massilia.generic.data.repository.offline.mapstyle.MapStyleRepository].
 */
class ThemePreferenceRepository(context: PlatformContext) {
    private val settings = Settings(context, "massilia_theme_prefs")

    private val keyThemeMode = "theme_mode"

    fun getThemeMode(): ThemeMode {
        val raw = settings.getString(keyThemeMode, ThemeMode.AUTO.name)
        return ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.AUTO
    }

    fun saveThemeMode(mode: ThemeMode) {
        settings.putString(keyThemeMode, mode.name)
    }
}
