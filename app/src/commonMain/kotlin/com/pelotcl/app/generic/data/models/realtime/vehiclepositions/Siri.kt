package com.pelotcl.app.generic.data.models.realtime.vehiclepositions
import kotlinx.serialization.Serializable

import kotlinx.serialization.SerialName
import com.pelotcl.app.generic.data.models.realtime.vehiclepositions.ServiceDelivery

@Serializable
data class Siri(
    @SerialName("ServiceDelivery")
    val serviceDelivery: ServiceDelivery? = null)
