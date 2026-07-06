package eu.dotshell.massilia.generic.data.repository.offline.mapstyle

import eu.dotshell.massilia.generic.data.network.mapstyle.MapStyleCategory
import eu.dotshell.massilia.generic.data.network.mapstyle.MapStyleData
import eu.dotshell.massilia.generic.data.network.mapstyle.MapStyleConfig

/**
 * Object containing predefined map styles for compatibility
 */
object MapStyleCompat {
    val POSITRON = MapStyleData(
        key = "positron",
        displayName = "Clair",
        styleUrl = "https://tiles.openfreemap.org/styles/positron",
        category = MapStyleCategory.STANDARD
    )

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
