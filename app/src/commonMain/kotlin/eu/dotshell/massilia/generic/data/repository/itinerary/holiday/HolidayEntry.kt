package eu.dotshell.massilia.generic.data.repository.itinerary.holiday

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class HolidayEntry(
    val name: String,
    @SerialName("start_date_inclusive")
    val startDateInclusive: String,
    @SerialName("end_date_inclusive")
    val endDateInclusive: String?,
    @SerialName("school_resumes")
    val schoolResumes: String
)
