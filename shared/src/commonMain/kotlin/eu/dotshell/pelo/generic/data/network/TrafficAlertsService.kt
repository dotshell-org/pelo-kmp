package eu.dotshell.pelo.generic.data.network

import eu.dotshell.pelo.generic.data.models.realtime.alerts.official.TrafficAlertsResponse

/**
 * Service interface for traffic alerts
 * Provides functions to get traffic alert information
 */
interface TrafficAlertsService {
    
    /**
     * Get the traffic alerts API URL
     * @return URL string for traffic alerts endpoint
     */
    fun getTrafficAlertsUrl(): String
    
    /**
     * Get current traffic alerts
     * @return TrafficAlertsResponse containing alert information
     */
    suspend fun getTrafficAlerts(): TrafficAlertsResponse
    
    /**
     * Get traffic alerts for a specific line
     * @param lineName The line name to filter alerts by
     * @return TrafficAlertsResponse containing filtered alerts
     */
    suspend fun getTrafficAlertsByLine(lineName: String): TrafficAlertsResponse
}
