package com.pelotcl.app.generic.data.models.realtime.vehiclepositions
import kotlinx.serialization.Serializable

import kotlinx.serialization.SerialName

@Serializable
data class VehicleLocation(
    @SerialName("Longitude")
    val longitude: Double? = null,
    @SerialName("Latitude")
    val latitude: Double? = null)
