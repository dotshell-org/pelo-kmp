package eu.dotshell.pelo.generic.data.config

import eu.dotshell.pelo.generic.data.network.mapstyle.MapStyleCategory
import eu.dotshell.pelo.generic.data.network.mapstyle.MapStyleConfig
import eu.dotshell.pelo.generic.data.network.mapstyle.MapStyleData

class AppMapStyleConfig(private val data: MapStylesData) : MapStyleConfig {

    private val standardStyles: List<MapStyleData> = data.standard.map { entry ->
        MapStyleData(
            key = entry.key,
            displayName = entry.displayName,
            styleUrl = entry.styleUrl,
            category = MapStyleCategory.STANDARD
        )
    }

    private val satelliteStyle: MapStyleData = MapStyleData(
        key = data.satellite.key,
        displayName = data.satellite.displayName,
        styleUrl = data.satellite.styleUrl,
        category = MapStyleCategory.SATELLITE
    )

    private val stylesByKey: Map<String, MapStyleData> =
        (standardStyles + satelliteStyle).associateBy { it.key }

    private val defaultStyle: MapStyleData =
        standardStyles.firstOrNull { it.key == data.defaultKey } ?: standardStyles.first()

    override fun getStandardMapStyles(): List<MapStyleData> = standardStyles

    override fun getSatelliteMapStyle(): MapStyleData = satelliteStyle

    override fun getMapStyleByKey(key: String): MapStyleData? = stylesByKey[key]

    override fun getDefaultMapStyle(): MapStyleData = defaultStyle
}
