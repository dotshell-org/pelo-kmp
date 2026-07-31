package eu.dotshell.pelo.generic.data.network.mapstyle

/**
 * Configuration interface for map styles
 * Provides functions to get available map styles and their URLs
 */
interface MapStyleConfig {
    
    /**
     * Get all available standard map styles
     * @return List of MapStyleData objects representing standard styles
     */
    fun getStandardMapStyles(): List<MapStyleData>
    
    /**
     * Get satellite map style
     * @return MapStyleData object for satellite style
     */
    fun getSatelliteMapStyle(): MapStyleData
    
    /**
     * Get map style by key
     * @param key The style key
     * @return MapStyleData object or null if not found
     */
    fun getMapStyleByKey(key: String): MapStyleData?
    
    /**
     * Get default map style
     * @return Default MapStyleData object
     */
    fun getDefaultMapStyle(): MapStyleData
}
