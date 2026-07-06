package eu.dotshell.massilia.generic.data.repository.api

import eu.dotshell.massilia.generic.data.models.realtime.alerts.official.TrafficAlert

interface TrafficAlertsRepository {
    suspend fun getTrafficAlerts(): Result<List<TrafficAlert>>
}
