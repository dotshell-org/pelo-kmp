package eu.dotshell.massilia.generic.data.repository.online

import eu.dotshell.massilia.generic.data.models.realtime.vehiclepositions.SimpleVehiclePosition
import eu.dotshell.massilia.generic.data.network.VehiclePositionsService
import kotlinx.coroutines.flow.Flow

/**
 * Repository for fetching real-time vehicle positions
 * Uses VehiclePositionsService for city-specific implementations
 */
class VehiclePositionsRepository(
    private val vehiclePositionsService: VehiclePositionsService
) {

    /**
     * Streams all vehicle positions from the service.
     */
    fun streamAllVehiclePositions(): Flow<Result<List<SimpleVehiclePosition>>> {
        return vehiclePositionsService.streamAllVehiclePositions()
    }

}
