package eu.dotshell.massilia.generic.data.models.realtime.vehiclepositions
import kotlinx.serialization.Serializable

import kotlinx.serialization.SerialName

@Serializable
data class VehicleMonitoringDelivery(
    @SerialName("VehicleActivity")
    val vehicleActivity: List<VehicleActivity>? = null)
