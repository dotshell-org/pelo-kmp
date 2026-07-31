package eu.dotshell.pelo.generic.data.repository.api

import eu.dotshell.pelo.generic.data.models.geojson.Feature
import eu.dotshell.pelo.generic.data.models.geojson.StopFeature
import eu.dotshell.pelo.generic.data.models.realtime.alerts.official.TrafficAlert

interface OfflineRepository {
    suspend fun loadStops(): List<StopFeature>?
    suspend fun saveStops(stops: List<StopFeature>)
    suspend fun clearStopsCache()
    suspend fun loadAllLines(): List<Feature>
    suspend fun loadTrafficAlerts(): List<TrafficAlert>?
    suspend fun saveTrafficAlerts(alerts: List<TrafficAlert>)
}
