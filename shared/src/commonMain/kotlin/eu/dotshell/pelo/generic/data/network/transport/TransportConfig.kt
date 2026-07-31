package eu.dotshell.pelo.generic.data.network.transport

/**
 * Base configuration for a transport implementation
 * Each city must provide its own implementation
 */
interface TransportConfig {

    /**
     * Base URL of the API
     */
    val baseUrl: String

    /**
     * Name of the city/network
     */
    val networkName: String

    /**
     * Covered region/zone
     */
    val region: String

    /**
     * Organizing authority (e.g., SYTRAL for Lyon)
     */
    val organizingAuthority: String

    /**
     * Data source
     */
    val dataSource: String

    /**
     * Data source URL
     */
    val dataSourceUrl: String

    /**
     * Data license
     */
    val dataLicense: String

    /**
     * Bounding box of the region (minLat, minLon, maxLat, maxLon)
     * For offline map
     */
    val regionBounds: DoubleArray

    /**
     * Zoom levels for offline map
     */
    val offlineMapZoomRange: IntRange

    /**
     * School holidays file
     */
    val schoolHolidaysFile: String

    /**
     * Primary color of the network (for theme)
     */
    val primaryColor: String

    /**
     * Secondary color of the network
     */
    val secondaryColor: String
}
