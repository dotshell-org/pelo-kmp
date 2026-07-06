package eu.dotshell.massilia.generic.data.repository.offline.mapstyle

import eu.dotshell.massilia.generic.data.network.mapstyle.MapStyleConfig
import eu.dotshell.massilia.generic.data.network.mapstyle.MapStyleData
import eu.dotshell.massilia.platform.PlatformContext
import eu.dotshell.massilia.platform.Settings

/**
 * Repository for managing map style preferences.
 * Multiplatform: uses [Settings] abstraction instead of SharedPreferences.
 */
class MapStyleRepository(
    context: PlatformContext,
    private val mapStyleConfig: MapStyleConfig
) {
    private val settings = Settings(context, "massilia_map_prefs")

    private val keyMapStyle = "selected_map_style"

    /**
     * Get the currently selected map style.
     * Defaults to config's default style if no style is saved.
     */
    fun getSelectedStyle(): MapStyleData {
        val styleKey = settings.getString(keyMapStyle, mapStyleConfig.getDefaultMapStyle().key)
        return mapStyleConfig.getMapStyleByKey(styleKey)
            ?: mapStyleConfig.getDefaultMapStyle()
    }

    /**
     * Save the selected map style.
     */
    fun saveSelectedStyle(style: MapStyleData) {
        settings.putString(keyMapStyle, style.key)
    }

}
