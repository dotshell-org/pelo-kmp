package eu.dotshell.massilia.generic.data.repository.itinerary.holiday

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data classes for parsing holidays.json
 */
@Serializable
internal data class HolidaysData(
    @SerialName("school_year")
    val schoolYear: String,
    val location: HolidayLocation,
    val holidays: List<HolidayEntry>
)
