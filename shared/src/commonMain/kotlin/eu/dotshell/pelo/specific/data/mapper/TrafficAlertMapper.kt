package eu.dotshell.pelo.specific.data.mapper

import eu.dotshell.pelo.generic.data.models.realtime.alerts.official.TrafficAlert
import eu.dotshell.pelo.generic.data.models.realtime.alerts.official.TrafficAlertsResponse
import eu.dotshell.pelo.specific.data.model.LyonTrafficAlert
import eu.dotshell.pelo.specific.data.model.LyonTrafficAlertsResponse

/**
 * Mapper to convert between Lyon-specific traffic alert models and generic models
 */
object TrafficAlertMapper {

    /**
     * Convert a Lyon-specific traffic alert to a generic traffic alert
     */
    fun mapToGeneric(lyonAlert: LyonTrafficAlert): TrafficAlert {
        return TrafficAlert(
            cause = lyonAlert.cause,
            startDate = lyonAlert.startDate,
            endDate = lyonAlert.endDate,
            lastUpdate = lyonAlert.lastUpdate,
            lineCode = lyonAlert.lineCode,
            lineName = lyonAlert.lineName,
            objectList = lyonAlert.objectList,
            message = lyonAlert.message,
            mode = lyonAlert.mode,
            alertNumber = lyonAlert.alertNumber,
            severityLevel = lyonAlert.severityLevel,
            title = lyonAlert.title,
            alertType = lyonAlert.alertType,
            objectType = lyonAlert.objectType,
            severityType = lyonAlert.severityType
        )
    }

    /**
     * Convert a list of Lyon-specific traffic alerts to generic traffic alerts
     */
    fun mapToGenericList(lyonAlerts: List<LyonTrafficAlert>): List<TrafficAlert> {
        return lyonAlerts.map { mapToGeneric(it) }
    }

    /**
     * Convert a Lyon-specific traffic alerts response to a generic response
     */
    fun mapResponseToGeneric(lyonResponse: LyonTrafficAlertsResponse): TrafficAlertsResponse {
        return TrafficAlertsResponse(
            success = lyonResponse.success,
            alerts = mapToGenericList(lyonResponse.alerts),
            timestamp = lyonResponse.timestamp,
            lastUpdated = lyonResponse.lastUpdated
        )
    }

}
