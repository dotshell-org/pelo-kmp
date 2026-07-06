package eu.dotshell.massilia.generic.data.repository.itinerary.holiday

import kotlinx.datetime.LocalDate

/**
 * Internal representation of a holiday period
 */
internal data class HolidayPeriod(
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate
)
