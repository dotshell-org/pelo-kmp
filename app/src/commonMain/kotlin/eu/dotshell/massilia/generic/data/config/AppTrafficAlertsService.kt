package eu.dotshell.massilia.generic.data.config

import eu.dotshell.massilia.generic.data.models.realtime.alerts.official.TrafficAlertsResponse
import eu.dotshell.massilia.generic.data.network.TrafficAlertsService
import eu.dotshell.massilia.generic.data.network.transport.TransportApi

class AppTrafficAlertsService(
    private val data: TransportConfigData,
    private val transportApi: TransportApi
) : TrafficAlertsService {
    
    override fun getTrafficAlertsUrl(): String {
        return "${data.trafficAlertsBaseUrl}massilia/v1/traffic/alerts"
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
