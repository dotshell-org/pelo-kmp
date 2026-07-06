package eu.dotshell.massilia.generic.data.cache

import eu.dotshell.massilia.generic.data.models.geojson.Feature
import eu.dotshell.massilia.generic.data.models.geojson.StopFeature

/**
 * Generic interface for transport data caching
 * Provides methods to cache and retrieve transport line and stop data
 */
interface TransportCache {

    /**
     * Check if any cached data is available (even if expired)
     */
    fun hasAnyCachedData(): Boolean

    /**
     * Check if cache needs refresh (has data but expired)
     */
    fun needsCacheRefresh(): Boolean

    /**
     * Saves metro/funicular lines to cache
     */
    suspend fun saveMetroLines(lines: List<Feature>)

    /**
     * Retrieves metro/funicular lines from cache
     */
    suspend fun getMetroLines(): List<Feature>?

    /**
     * Saves tram lines to cache
     */
    suspend fun saveTramLines(lines: List<Feature>)

    /**
     * Retrieves tram lines from cache
     */
    suspend fun getTramLines(): List<Feature>?

    /**
     * Saves bus lines to cache
     */
    suspend fun saveBusLines(lines: List<Feature>)

    /**
     * Retrieves bus lines from cache
     */
    suspend fun getBusLines(): List<Feature>?

    /**
     * Saves stops to cache
     */
    suspend fun saveStops(stops: List<StopFeature>)

    /**
     * Retrieves stops from cache
     */
    suspend fun getStops(): List<StopFeature>?

    /**
     * Preload cache from disk into memory
     */
    suspend fun preloadFromDisk()
}
