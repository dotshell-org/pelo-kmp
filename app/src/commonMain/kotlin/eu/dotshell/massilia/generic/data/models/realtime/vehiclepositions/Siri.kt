package eu.dotshell.massilia.generic.data.models.realtime.vehiclepositions
import kotlinx.serialization.Serializable

import kotlinx.serialization.SerialName
import eu.dotshell.massilia.generic.data.models.realtime.vehiclepositions.ServiceDelivery

@Serializable
data class Siri(
    @SerialName("ServiceDelivery")
    val serviceDelivery: ServiceDelivery? = null)
