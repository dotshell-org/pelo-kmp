package eu.dotshell.pelo.generic.data.repository.offline.mapstyle

import eu.dotshell.pelo.generic.data.network.mapstyle.MapStyleCategory
import eu.dotshell.pelo.generic.data.network.mapstyle.MapStyleData
import eu.dotshell.pelo.generic.data.network.mapstyle.MapStyleConfig

/**
 * Compatibility shim over [MapStyleConfig].
 */
object MapStyleCompat {
    /**
     * Compatibility function to replace MapStyle.getByCategory()
     */
    fun getByCategory(category: MapStyleCategory, config: MapStyleConfig): List<MapStyleData> {
        return when (category) {
            MapStyleCategory.STANDARD -> config.getStandardMapStyles()
            MapStyleCategory.SATELLITE -> listOf(config.getSatelliteMapStyle())
        }
    }
}
