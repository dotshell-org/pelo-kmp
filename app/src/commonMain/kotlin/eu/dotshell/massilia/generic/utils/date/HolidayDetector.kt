package eu.dotshell.massilia.generic.utils.date

import eu.dotshell.massilia.generic.data.repository.itinerary.holiday.HolidaysData
import eu.dotshell.massilia.platform.FileSystem
import eu.dotshell.massilia.platform.PlatformContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.json.Json

/**
 * Generic school holiday detector.
 * Loads holiday periods from a bundled JSON asset via the cross-platform
 * [FileSystem] abstraction (was Android `Context.assets`).
 */
class HolidayDetector(
    context: PlatformContext,
    private val holidayFileName: String,
    private val publicHolidayStrategy: PublicHolidayStrategy? = null
) {
    private val fileSystem = FileSystem(context)
    private val schoolHolidays: List<HolidayPeriod> = loadSchoolHolidays()

    private fun loadSchoolHolidays(): List<HolidayPeriod> {
        return try {
            val json = fileSystem.readAsset(holidayFileName)
            val jsonConfig = Json { ignoreUnknownKeys = true }
            val holidaysData = jsonConfig.decodeFromString<HolidaysData>(json)
            holidaysData.holidays.mapNotNull { holiday ->
                val startDate = try {
                    LocalDate.parse(holiday.startDateInclusive)
                } catch (e: Exception) {
                    null
                }
                val endDate = try {
                    holiday.endDateInclusive?.let {
                        LocalDate.parse(it)
                    }
                } catch (e: Exception) {
                    null
                }

                if (startDate != null) {
                    HolidayPeriod(
                        name = holiday.name,
                        startDate = startDate,
                        endDate = endDate ?: startDate.plus(2, DateTimeUnit.MONTH)
                    )
                } else null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Check if a given date is a school holiday
     */
    fun isSchoolHoliday(date: LocalDate): Boolean {
        return schoolHolidays.any { period ->
            date >= period.startDate && date <= period.endDate
        }
    }

    /**
     * Check if a given date is a public holiday
     */
    fun isPublicHoliday(date: LocalDate): Boolean {
        if (publicHolidayStrategy == null) return false
        return publicHolidayStrategy.isPublicHoliday(date)
    }

    data class HolidayPeriod(
        val name: String,
        val startDate: LocalDate,
        val endDate: LocalDate
    )
}
