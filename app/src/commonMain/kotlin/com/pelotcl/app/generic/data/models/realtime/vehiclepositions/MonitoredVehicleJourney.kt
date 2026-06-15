package com.pelotcl.app.generic.data.models.realtime.vehiclepositions
import kotlinx.serialization.Serializable

import kotlinx.serialization.SerialName
import com.pelotcl.app.generic.data.models.realtime.vehiclepositions.RefValue
import com.pelotcl.app.generic.data.models.TranslatedString

@Serializable
data class MonitoredVehicleJourney(
    @SerialName("LineRef")
    val lineRef: RefValue? = null,
    @SerialName("DirectionRef")
    val directionRef: RefValue? = null,
    @SerialName("FramedVehicleJourneyRef")
    val framedVehicleJourneyRef: FramedVehicleJourneyRef? = null,
    @SerialName("DestinationRef")
    val destinationRef: RefValue? = null,
    @SerialName("DestinationName")
    val destinationName: List<TranslatedString>? = null,
    @SerialName("Bearing")
    val bearing: Double? = null,
    @SerialName("VehicleLocation")
    val vehicleLocation: VehicleLocation? = null,
    @SerialName("VehicleStatus")
    val vehicleStatus: String? = null
)
