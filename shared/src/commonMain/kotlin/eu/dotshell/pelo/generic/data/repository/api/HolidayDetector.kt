package eu.dotshell.pelo.generic.data.repository.api

import kotlinx.datetime.LocalDate

interface HolidayDetector {
    fun isSchoolHoliday(date: LocalDate): Boolean
    fun isPublicHoliday(date: LocalDate): Boolean
}
