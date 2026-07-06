package eu.dotshell.massilia.generic.data.models.realtime.vehiclepositions
import kotlinx.serialization.Serializable

import kotlinx.serialization.SerialName
import eu.dotshell.massilia.generic.data.models.realtime.vehiclepositions.RefValue

@Serializable
data class FramedVehicleJourneyRef(
    @SerialName("DataFrameRef")
    val dataFrameRef: RefValue? = null,
    @SerialName("DatedVehicleJourneyRef")
    val datedVehicleJourneyRef: String? = null)
