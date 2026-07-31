package eu.dotshell.pelo.generic.data.repository.api

import eu.dotshell.pelo.generic.data.models.realtime.alerts.official.TrafficAlert

interface TrafficAlertsRepository {
    suspend fun getTrafficAlerts(): Result<List<TrafficAlert>>
}
