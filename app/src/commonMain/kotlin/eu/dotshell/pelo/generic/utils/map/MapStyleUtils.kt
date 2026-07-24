package eu.dotshell.pelo.generic.utils.map

import androidx.compose.runtime.Composable
import eu.dotshell.pelo.generic.data.network.mapstyle.MapStyleConfig
import eu.dotshell.pelo.generic.data.network.mapstyle.MapStyleData
import eu.dotshell.pelo.platform.LocalPlatformContext
import eu.dotshell.pelo.platform.StringProvider

object MapStyleUtils {

    /**
     * A basemap the user picks once and that then follows the app theme: [lightKey] renders in
     * light mode, [darkKey] in dark mode. Only [lightKey] is ever persisted or offered in the
     * picker — see [canonicalKey].
     */
    data class AdaptivePair(val lightKey: String, val darkKey: String)

    /** OSM Bright and the dark cut derived from it. The app's default basemap. */
    val STANDARD = AdaptivePair(lightKey = "bright", darkKey = "bright_dark")

    /** OSM Liberty (extruded buildings) and the dark cut derived from it. */
    val THREE_D = AdaptivePair(lightKey = "liberty", darkKey = "liberty_dark")

    private val PAIRS = listOf(STANDARD, THREE_D)

    /** The pair [key] belongs to, or null for a standalone style such as Satellite. */
    fun pairOf(key: String): AdaptivePair? =
        PAIRS.firstOrNull { key == it.lightKey || key == it.darkKey }

    /**
     * The key that identifies a basemap independently of the theme — the light half of its pair.
     * Persisted preferences, offline download bookkeeping and picker selection all key on this,
     * so switching theme never looks like switching basemap.
     */
    fun canonicalKey(key: String): String = pairOf(key)?.lightKey ?: key

    /** True when [key] is a dark half, i.e. a style the picker should never list on its own. */
    fun isDarkHalf(key: String): Boolean = PAIRS.any { key == it.darkKey }

    /**
     * Resolves the concrete basemap to display: a paired style follows the app theme, anything
     * else is returned unchanged. Falls back to [style] if the paired entry is missing from config.
     */
    fun resolveForTheme(style: MapStyleData, darkTheme: Boolean, config: MapStyleConfig): MapStyleData {
        val pair = pairOf(style.key) ?: return style
        val key = if (darkTheme) pair.darkKey else pair.lightKey
        return config.getMapStyleByKey(key) ?: style
    }

    @Composable
    fun mapStyleLabel(style: MapStyleData): String {
        val strings = StringProvider(LocalPlatformContext.current)
        return when (canonicalKey(style.key)) {
            STANDARD.lightKey -> strings["map_style_standard"]
            THREE_D.lightKey -> "3D"
            "satellite" -> "Satellite"
            else -> style.displayName
        }
    }
}
