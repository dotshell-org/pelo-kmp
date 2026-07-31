package eu.dotshell.pelo.generic.data.repository.itinerary.holiday

import kotlinx.serialization.Serializable

@Serializable
internal data class HolidayLocation(
    val department: String,
    val academy: String,
    val zone: String
)
