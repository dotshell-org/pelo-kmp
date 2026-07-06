package eu.dotshell.massilia.generic.utils.date

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlin.concurrent.Volatile

/**
 * Implementation of PublicHolidayStrategy for France
 */
class FrenchPublicHolidayStrategy : PublicHolidayStrategy {

    // Copy-on-write cache: reads are lock-free and never hit a HashMap mid-resize.
    // isPublicHoliday is reachable from concurrent itinerary calculations, and a plain
    // HashMap.getOrPut there could throw ConcurrentModificationException. A racing double
    // compute for the same year just recomputes once — cheap and harmless.
    @Volatile
    private var holidaysByYear: Map<Int, Set<LocalDate>> = emptyMap()

    override fun isPublicHoliday(date: LocalDate): Boolean {
        holidaysByYear[date.year]?.let { return date in it }
        val holidays = computeHolidays(date.year)
        holidaysByYear = holidaysByYear + (date.year to holidays)
        return date in holidays
    }

    private fun computeHolidays(year: Int): Set<LocalDate> {
        val easterDate = calculateEasterDate(year)
        return hashSetOf(
            LocalDate(year, Month.JANUARY, 1),
            LocalDate(year, Month.MAY, 1),
            LocalDate(year, Month.MAY, 8),
            LocalDate(year, Month.JULY, 14),
            LocalDate(year, Month.AUGUST, 15),
            LocalDate(year, Month.NOVEMBER, 1),
            LocalDate(year, Month.NOVEMBER, 11),
            LocalDate(year, Month.DECEMBER, 25),
            easterDate.plus(1, DateTimeUnit.DAY),   // Easter Monday
            easterDate.plus(39, DateTimeUnit.DAY),  // Ascension Day
            easterDate.plus(50, DateTimeUnit.DAY)   // Whit Monday
        )
    }

    /**
     * Calculate Easter date for a given year using the Computus algorithm
     */
    private fun calculateEasterDate(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1

        return LocalDate(year, month, day)
    }
}
