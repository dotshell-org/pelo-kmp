package eu.dotshell.pelo

import eu.dotshell.pelo.generic.data.dataset.DataFreshness
import eu.dotshell.pelo.generic.data.dataset.DatasetFreshness
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DatasetFreshnessTest {

    private val today = LocalDate(2026, 7, 20)

    @Test
    fun `a fresh TCL window reads as valid`() {
        // The real shipped dataset: generated 2026-07-20, valid to 2026-11-17.
        assertEquals(
            DataFreshness.VALID,
            DatasetFreshness.classify(LocalDate(2026, 11, 17), today)
        )
    }

    @Test
    fun `the last valid day is not yet expired`() {
        // Service on the final day IS covered by the feed, so the boundary is inclusive.
        assertEquals(
            DataFreshness.EXPIRING_SOON,
            DatasetFreshness.classify(today, today)
        )
    }

    @Test
    fun `the day after the window closes is expired`() {
        assertEquals(
            DataFreshness.EXPIRED,
            DatasetFreshness.classify(LocalDate(2026, 7, 19), today)
        )
    }

    @Test
    fun `the warning fires while the app still works`() {
        // Exactly on the threshold: still valid, already warning — that is the point,
        // it leaves a release cycle of head start.
        assertEquals(
            DataFreshness.EXPIRING_SOON,
            DatasetFreshness.classify(LocalDate(2026, 8, 19), today)
        )
        assertEquals(
            DataFreshness.VALID,
            DatasetFreshness.classify(LocalDate(2026, 8, 20), today)
        )
    }

    @Test
    fun `a missing end date says nothing rather than guessing`() {
        assertEquals(DataFreshness.UNKNOWN, DatasetFreshness.classify(null, today))
    }

    @Test
    fun `only ISO dates parse`() {
        assertEquals(LocalDate(2026, 11, 17), DatasetFreshness.parseIsoDate("2026-11-17"))
        assertNull("GTFS-style dates are not ISO", DatasetFreshness.parseIsoDate("20261117"))
        assertNull(DatasetFreshness.parseIsoDate(""))
        assertNull(DatasetFreshness.parseIsoDate(null))
    }

    @Test
    fun `long date formatting follows the supplied locale month names`() {
        val fr = "janvier,février,mars,avril,mai,juin,juillet,août,septembre,octobre,novembre,décembre".split(",")
        val en = "January,February,March,April,May,June,July,August,September,October,November,December".split(",")

        assertEquals("17 novembre 2026", DatasetFreshness.formatLongDate(LocalDate(2026, 11, 17), fr))
        assertEquals("17 November 2026", DatasetFreshness.formatLongDate(LocalDate(2026, 11, 17), en))
    }

    @Test
    fun `a broken month list degrades to a numeric date instead of crashing`() {
        assertEquals(
            "17/11/2026",
            DatasetFreshness.formatLongDate(LocalDate(2026, 11, 17), listOf("oops"))
        )
    }
}
