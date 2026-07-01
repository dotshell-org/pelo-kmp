package eu.dotshell.pelo.generic.utils.map

import androidx.compose.runtime.Composable
import eu.dotshell.pelo.generic.data.network.mapstyle.MapStyleData
import eu.dotshell.pelo.platform.LocalPlatformContext
import eu.dotshell.pelo.platform.StringProvider

object MapStyleUtils {

    @Composable
    fun mapStyleLabel(style: MapStyleData): String {
        val strings = StringProvider(LocalPlatformContext.current)
        return when (style.key) {
            "positron" -> strings["theme_light"]
            "dark_matter" -> strings["theme_dark"]
            "bright" -> "OSM"
            "liberty" -> "3D"
            "satellite" -> "Satellite"
            else -> style.displayName
        }
    }
}
