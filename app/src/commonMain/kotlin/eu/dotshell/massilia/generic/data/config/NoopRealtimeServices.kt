package eu.dotshell.massilia.generic.data.config

import eu.dotshell.massilia.generic.data.models.realtime.alerts.official.TrafficAlertsResponse
import eu.dotshell.massilia.generic.data.models.realtime.vehiclepositions.SimpleVehiclePosition
import eu.dotshell.massilia.generic.data.network.TrafficAlertsService
import eu.dotshell.massilia.generic.data.network.VehiclePositionsService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * No-op implementations used when the network has no real-time backend
 * (see [RealtimeConfigData]). Consumers see "no alerts" / "no vehicles"
 * instead of network errors.
 */

class NoopTrafficAlertsService : TrafficAlertsService {

    override fun getTrafficAlertsUrl(): String = ""

    override suspend fun getTrafficAlerts(): TrafficAlertsResponse =
        TrafficAlertsResponse(success = true, alerts = emptyList(), timestamp = "", lastUpdated = "")

    override suspend fun getTrafficAlertsByLine(lineName: String): TrafficAlertsResponse =
        getTrafficAlerts()
}

class NoopVehiclePositionsService : VehiclePositionsService {

    override fun getVehiclePositionsStreamUrl(): String = ""

    override fun streamAllVehiclePositions(): Flow<Result<List<SimpleVehiclePosition>>> = emptyFlow()

    override fun streamVehiclePositionsByLine(lineName: String): Flow<Result<List<SimpleVehiclePosition>>> = emptyFlow()

    override fun streamStrongLinesVehiclePositions(): Flow<Result<List<SimpleVehiclePosition>>> = emptyFlow()
}
