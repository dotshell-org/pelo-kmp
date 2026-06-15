package com.pelotcl.app.generic.data.models.realtime.vehiclepositions
import kotlinx.serialization.Serializable

import kotlinx.serialization.SerialName

@Serializable
data class SiriData(
    @SerialName("Siri")
    val siri: Siri? = null)
