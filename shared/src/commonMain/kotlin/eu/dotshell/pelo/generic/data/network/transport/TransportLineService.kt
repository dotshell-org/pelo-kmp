package eu.dotshell.pelo.generic.data.network.transport

import eu.dotshell.pelo.generic.data.models.geojson.FeatureCollection
import eu.dotshell.pelo.generic.data.models.geojson.StopCollection

/**
 * Service interface for transport line operations
 * Provides functions to get line data and stop information
 */
interface TransportLineService {
    
    /**
     * Get all metro lines
     * @return FeatureCollection containing metro line data
     */
    suspend fun getMetroLines(): FeatureCollection
    
    /**
     * Get all tram lines
     * @return FeatureCollection containing tram line data
     */
    suspend fun getTramLines(): FeatureCollection
    
    /**
     * Get all bus lines
     * @return FeatureCollection containing bus line data
     */
    suspend fun getBusLines(): FeatureCollection
    
    /**
     * Get bus line by name
     * @param lineName The name of the bus line to retrieve
     * @return FeatureCollection containing the specific bus line data
     */
    suspend fun getBusLineByName(lineName: String): FeatureCollection
    
    /**
     * Get all navigone (river shuttle) lines
     * @return FeatureCollection containing navigone line data
     */
    suspend fun getNavigoneLines(): FeatureCollection
    
    /**
     * Get all trambus lines
     * @return FeatureCollection containing trambus line data
     */
    suspend fun getTrambusLines(): FeatureCollection
    
    /**
     * Get all transport stops
     * @return StopCollection containing all stop data
     */
    suspend fun getTransportStops(): StopCollection
    
    /**
     * Get all strong lines (metro, tram, major lines)
     * @return FeatureCollection containing strong line data
     */
    suspend fun getStrongLines(): FeatureCollection
    
    /**
     * Get line geometry by line name
     * @param lineName The name of the line to get geometry for
     * @return FeatureCollection containing the line geometry
     */
    suspend fun getLineGeometry(lineName: String): FeatureCollection
    
    /**
     * Get all lines for a specific transport type
     * @param type The transport type (metro, tram, bus, etc.)
     * @return FeatureCollection containing lines of the specified type
     */
    suspend fun getLinesByType(type: String): FeatureCollection
}
