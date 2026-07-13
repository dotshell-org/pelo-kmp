package eu.dotshell.pelo.generic.utils.schedule

import org.junit.Assert.assertEquals
import org.junit.Test

class DepartureManagerTest {

    @Test
    fun `night-run service-day hours wrap to clock time for display`() {
        assertEquals("01:30", DepartureManager.formatDisplayTime("25:30"))
        assertEquals("02:59", DepartureManager.formatDisplayTime("26:59"))
        assertEquals("00:05", DepartureManager.formatDisplayTime("24:05"))
    }

    @Test
    fun `regular times are untouched`() {
        assertEquals("23:59", DepartureManager.formatDisplayTime("23:59"))
        assertEquals("08:15", DepartureManager.formatDisplayTime("08:15"))
        assertEquals("00:00", DepartureManager.formatDisplayTime("00:00"))
    }

    @Test
    fun `seconds are stripped and malformed input passes through`() {
        assertEquals("01:30", DepartureManager.formatDisplayTime("25:30:00"))
        assertEquals("14:05", DepartureManager.formatDisplayTime("14:05:30"))
        assertEquals("bientôt", DepartureManager.formatDisplayTime("bientôt"))
        assertEquals("", DepartureManager.formatDisplayTime(""))
    }

    @Test
    fun `raw countdown math still uses the service-day value`() {
        // "25:30" must stay 25h30 internally (1530 minutes), not 1h30
        assertEquals(1530, DepartureManager.parseDepartureToMinutes("25:30"))
    }
}
