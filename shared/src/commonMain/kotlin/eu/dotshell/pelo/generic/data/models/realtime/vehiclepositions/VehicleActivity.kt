package eu.dotshell.pelo.generic.data.models.realtime.vehiclepositions
import kotlinx.serialization.Serializable

import kotlinx.serialization.SerialName
import eu.dotshell.pelo.generic.data.models.realtime.vehiclepositions.RefValue

@Serializable
data class VehicleActivity(
    @SerialName("ValidUntilTime")
    val validUntilTime: String? = null,
    @SerialName("VehicleMonitoringRef")
    val vehicleMonitoringRef: RefValue? = null,
    @SerialName("MonitoredVehicleJourney")
    val monitoredVehicleJourney: MonitoredVehicleJourney? = null)
