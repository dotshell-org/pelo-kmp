package eu.dotshell.pelo.generic.utils.date

import kotlinx.datetime.LocalDate

/**
 * Strategy for calculating public holidays in a specific region
 */
interface PublicHolidayStrategy {
    /**
     * Check if a given date is a public holiday
     */
    fun isPublicHoliday(date: LocalDate): Boolean
}
