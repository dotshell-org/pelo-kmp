package eu.dotshell.pelo.generic.utils.date

import eu.dotshell.pelo.generic.data.repository.itinerary.holiday.HolidaysData
import eu.dotshell.pelo.platform.FileSystem
import eu.dotshell.pelo.platform.PlatformContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlin.concurrent.Volatile
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
        cachedPeriods[holidayFileName]?.let { return it }
        return try {
            val json = fileSystem.readAsset(holidayFileName)
            val holidaysData = JSON.decodeFromString<HolidaysData>(json)
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
            }.also { cachedPeriods = cachedPeriods + (holidayFileName to it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }

        /**
         * Parsed periods per asset name. The bundled holidays file cannot change while the process
         * runs, and two detectors are built per process (one by RaptorRepository, one by the view
         * model), so without this the asset was read and parsed twice. The Json instance was also
         * being constructed inside the parse rather than reused.
         *
         * Copy-on-write: replaced, never mutated, so a reader cannot see it half-built. A lost
         * race just parses twice, which is what already happened.
         */
        @Volatile
        private var cachedPeriods: Map<String, List<HolidayPeriod>> = emptyMap()
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
