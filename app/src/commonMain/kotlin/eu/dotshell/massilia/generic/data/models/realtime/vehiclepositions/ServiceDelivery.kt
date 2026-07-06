package eu.dotshell.massilia.generic.data.models.realtime.vehiclepositions
import kotlinx.serialization.Serializable

import kotlinx.serialization.SerialName

@Serializable
data class ServiceDelivery(
    @SerialName("ResponseTimestamp")
    val responseTimestamp: String? = null,
    @SerialName("ProducerRef")
    val producerRef: RefValue? = null,
    @SerialName("ResponseMessageIdentifier")
    val responseMessageIdentifier: RefValue? = null,
    @SerialName("MoreData")
    val moreData: Boolean? = null,
    @SerialName("VehicleMonitoringDelivery")
    val vehicleMonitoringDelivery: List<VehicleMonitoringDelivery>? = null)
