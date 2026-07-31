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
     * Streams vehicle positions for a single line. With the SIRI backend this
     * is a focused poll of that line only — much cheaper than the full sweep.
     */
    fun streamVehiclePositionsByLine(lineName: String): Flow<Result<List<SimpleVehiclePosition>>> {
        return vehiclePositionsService.streamVehiclePositionsByLine(lineName)
    }

}
