package eu.dotshell.pelo.generic.data.repository.online

import eu.dotshell.pelo.generic.data.models.realtime.vehiclepositions.SimpleVehiclePosition
import eu.dotshell.pelo.generic.data.network.VehiclePositionsService
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

    /**
     * Streams vehicle positions for a single line.
     *
     * Note that this is not a per-line subscription: the SIRI endpoint publishes the whole fleet
     * on one stream, so the service takes that stream and filters it. The saving is in what
     * reaches the UI, not in what crosses the network.
     */
    fun streamVehiclePositionsByLine(lineName: String): Flow<Result<List<SimpleVehiclePosition>>> {
        return vehiclePositionsService.streamVehiclePositionsByLine(lineName)
    }

}
