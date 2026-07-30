package eu.dotshell.pelo

import eu.dotshell.pelo.generic.data.dataset.DatasetUpdateScheduler
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatasetUpdateSchedulerTest {

    private val day = DatasetUpdateScheduler.DEFAULT_INTERVAL_MS

    @Test
    fun `never checks on a metered network`() {
        assertFalse(
            DatasetUpdateScheduler.shouldCheck(now = day * 10, lastCheck = 0, unmetered = false, minIntervalMs = day)
        )
    }

    @Test
    fun `first unmetered launch checks even though it never checked before`() {
        assertTrue(
            DatasetUpdateScheduler.shouldCheck(now = 5_000, lastCheck = 0, unmetered = true, minIntervalMs = day)
        )
    }

    @Test
    fun `does not check again within the interval`() {
        val now = day * 10
        assertFalse(
            DatasetUpdateScheduler.shouldCheck(now = now, lastCheck = now - day / 2, unmetered = true, minIntervalMs = day)
        )
    }

    @Test
    fun `checks again once the interval has elapsed`() {
        val now = day * 10
        assertTrue(
            DatasetUpdateScheduler.shouldCheck(now = now, lastCheck = now - day, unmetered = true, minIntervalMs = day)
        )
    }

    @Test
    fun `both gates must pass`() {
        val now = day * 10
        // Interval elapsed but metered → still no.
        assertFalse(
            DatasetUpdateScheduler.shouldCheck(now = now, lastCheck = now - day * 2, unmetered = false, minIntervalMs = day)
        )
    }
}
