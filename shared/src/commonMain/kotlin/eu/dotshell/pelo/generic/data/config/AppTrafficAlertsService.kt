package eu.dotshell.pelo.generic.data.config

import eu.dotshell.pelo.generic.data.models.realtime.alerts.official.TrafficAlertsResponse
import eu.dotshell.pelo.generic.data.network.TrafficAlertsService
import eu.dotshell.pelo.generic.data.network.transport.TransportApi

class AppTrafficAlertsService(
    private val data: TransportConfigData,
    private val transportApi: TransportApi
) : TrafficAlertsService {
    
    override fun getTrafficAlertsUrl(): String {
        return "${data.trafficAlertsBaseUrl}pelo/v1/traffic/alerts"
    }
    
    override suspend fun getTrafficAlerts(): TrafficAlertsResponse {
        return transportApi.getTrafficAlerts()
    }
    
    override suspend fun getTrafficAlertsByLine(lineName: String): TrafficAlertsResponse {
        val allAlerts = getTrafficAlerts()
        return allAlerts.copy(
            alerts = allAlerts.alerts.filter { alert ->
                alert.lineName.equals(lineName, ignoreCase = true) ||
                alert.lineCode.equals(lineName, ignoreCase = true)
            }
        )
    }
}
