package eu.dotshell.massilia.generic.data.network

import eu.dotshell.massilia.generic.data.models.realtime.vehiclepositions.SimpleVehiclePosition
import kotlinx.coroutines.flow.Flow

/**
 * Service interface for vehicle positions
 * Provides functions to get real-time vehicle positions
 */
interface VehiclePositionsService {
    
    /**
     * Get the SSE endpoint URL for vehicle positions stream
     * @return URL string for the SSE endpoint
     */
    fun getVehiclePositionsStreamUrl(): String
    
    /**
     * Get all vehicle positions as a flow
     * @return Flow of Result containing list of vehicle positions
     */
    fun streamAllVehiclePositions(): Flow<Result<List<SimpleVehiclePosition>>>
    
    /**
     * Get vehicle positions for a specific line
     * @param lineName The line name to filter by
     * @return Flow of Result containing list of vehicle positions for the line
     */
    fun streamVehiclePositionsByLine(lineName: String): Flow<Result<List<SimpleVehiclePosition>>>
    
    /**
     * Get vehicle positions for strong lines only
     * @return Flow of Result containing list of vehicle positions for strong lines
     */
    fun streamStrongLinesVehiclePositions(): Flow<Result<List<SimpleVehiclePosition>>>
}
