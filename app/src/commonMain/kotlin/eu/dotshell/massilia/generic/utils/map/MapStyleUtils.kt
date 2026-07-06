package eu.dotshell.massilia.generic.utils.map

import androidx.compose.runtime.Composable
import eu.dotshell.massilia.generic.data.network.mapstyle.MapStyleConfig
import eu.dotshell.massilia.generic.data.network.mapstyle.MapStyleData
import eu.dotshell.massilia.platform.LocalPlatformContext
import eu.dotshell.massilia.platform.StringProvider

object MapStyleUtils {

    /** The light basemap of the theme-adaptive "Standard" pair. */
    const val LIGHT_STANDARD_KEY = "positron"
    /** The dark basemap of the theme-adaptive "Standard" pair. */
    const val DARK_STANDARD_KEY = "dark_matter"

    /** True if [key] belongs to the theme-adaptive "Standard" basemap pair. */
    fun isAdaptiveStandardKey(key: String): Boolean =
        key == LIGHT_STANDARD_KEY || key == DARK_STANDARD_KEY

    /**
     * Resolves the concrete basemap to display. The merged "Standard" entry follows the app
     * theme (light -> positron, dark -> dark_matter); any other style is returned unchanged.
     */
    fun resolveForTheme(style: MapStyleData, darkTheme: Boolean, config: MapStyleConfig): MapStyleData {
        if (!isAdaptiveStandardKey(style.key)) return style
        val key = if (darkTheme) DARK_STANDARD_KEY else LIGHT_STANDARD_KEY
        return config.getMapStyleByKey(key) ?: style
    }

    @Composable
    fun mapStyleLabel(style: MapStyleData): String {
        val strings = StringProvider(LocalPlatformContext.current)
        return when (style.key) {
            // Light and dark are merged into a single theme-adaptive entry.
            LIGHT_STANDARD_KEY, DARK_STANDARD_KEY -> strings["map_style_standard"]
            "bright" -> "OSM"
            "liberty" -> "3D"
            "satellite" -> "Satellite"
            else -> style.displayName
        }
    }
}
