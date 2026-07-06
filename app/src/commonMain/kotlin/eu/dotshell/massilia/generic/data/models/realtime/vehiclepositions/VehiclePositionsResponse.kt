package eu.dotshell.massilia.generic.data.models.realtime.vehiclepositions

import kotlinx.serialization.Serializable

/**
 * Response wrapper for the SIRI-lite vehicle monitoring API
 */
@Serializable
data class VehiclePositionsResponse(
    val success: Boolean,
    val data: SiriData? = null)
