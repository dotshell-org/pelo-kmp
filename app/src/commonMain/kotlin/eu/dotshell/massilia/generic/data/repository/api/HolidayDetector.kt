package eu.dotshell.massilia.generic.data.repository.api

import kotlinx.datetime.LocalDate

interface HolidayDetector {
    fun isSchoolHoliday(date: LocalDate): Boolean
    fun isPublicHoliday(date: LocalDate): Boolean
}
