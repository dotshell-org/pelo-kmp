package eu.dotshell.pelo.generic.data.models.realtime.vehiclepositions

import androidx.compose.runtime.Immutable

/**
 * Simplified vehicle position for UI display
 */
@Immutable
data class SimpleVehiclePosition(
    val vehicleId: String,
    val lineName: String,
    val latitude: Double,
    val longitude: Double,
    val bearing: Double?,
    val destinationName: String?,
    val direction: String?
)
