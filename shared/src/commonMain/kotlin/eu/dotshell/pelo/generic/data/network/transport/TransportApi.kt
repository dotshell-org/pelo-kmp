package eu.dotshell.pelo.generic.data.network.transport

import eu.dotshell.pelo.generic.data.models.geojson.FeatureCollection
import eu.dotshell.pelo.generic.data.models.geojson.StopCollection
import eu.dotshell.pelo.generic.data.models.realtime.alerts.official.TrafficAlertsResponse

/**
 * Abstract interface for urban transport APIs.
 * Implementations live in `specific` and encapsulate all city/network details.
 */
interface TransportApi {
    /**
     * Single entry point to retrieve line geometries.
     */
    suspend fun getLines(query: TransportLinesQuery): FeatureCollection

    /**
     * Fetches transport stops.
     */
    suspend fun getTransportStops(): StopCollection

    /**
     * Fetches traffic alerts.
     */
    suspend fun getTrafficAlerts(): TrafficAlertsResponse
}
