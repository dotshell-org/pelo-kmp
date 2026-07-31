package eu.dotshell.pelo

import eu.dotshell.pelo.generic.data.dataset.DatasetHealthGuard
import eu.dotshell.pelo.generic.data.dataset.DatasetStorage
import eu.dotshell.pelo.generic.data.dataset.InitPlan
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class DatasetHealthGuardTest {

    private lateinit var root: String
    private lateinit var storage: DatasetStorage

    @Before
    fun setUp() {
        root = File(System.getProperty("java.io.tmpdir"), "dh-${System.nanoTime()}").absolutePath
        storage = DatasetStorage(root)
    }

    @After
    fun tearDown() {
        File(root).deleteRecursively()
    }

    // A fresh guard over the SAME storage models a new app launch: the on-disk tally
    // persists, the once-per-process in-memory flag resets. This is what makes the
    // count grow per launch, not per in-session call.
    private fun launch() = DatasetHealthGuard(storage)

    @Test
    fun `the bundle is never guarded`() {
        assertEquals(InitPlan.PROCEED, launch().beforeInit(null))
    }

    @Test
    fun `a version reverts on the launch after two failed launches`() {
        assertEquals(InitPlan.PROCEED, launch().beforeInit("v1")) // launch 1: fails=1
        assertEquals(InitPlan.PROCEED, launch().beforeInit("v1")) // launch 2: fails=2
        assertEquals(InitPlan.REVERT, launch().beforeInit("v1"))  // launch 3: revert
    }

    @Test
    fun `an in-session retry does not inflate the count`() {
        val g = launch()
        assertEquals(InitPlan.PROCEED, g.beforeInit("v1")) // counts once
        assertEquals(InitPlan.PROCEED, g.beforeInit("v1")) // same process: no extra count
        assertEquals(InitPlan.PROCEED, g.beforeInit("v1"))
        // A genuine second launch is still needed to reach the revert threshold.
        assertEquals(InitPlan.PROCEED, launch().beforeInit("v1")) // fails=2
        assertEquals(InitPlan.REVERT, launch().beforeInit("v1"))
    }

    @Test
    fun `a success resets the count so the next failure starts from zero`() {
        launch().beforeInit("v1")
        launch().apply { beforeInit("v1"); recordSuccess("v1") }
        // Back to a clean slate: it would take two fresh failed launches to revert.
        assertEquals(InitPlan.PROCEED, launch().beforeInit("v1"))
        assertEquals(InitPlan.PROCEED, launch().beforeInit("v1"))
        assertEquals(InitPlan.REVERT, launch().beforeInit("v1"))
    }

    @Test
    fun `switching to a different version starts a fresh count`() {
        launch().beforeInit("v1")
        launch().beforeInit("v1") // v1 fails=2
        // A newly promoted v2 must not inherit v1's tally.
        assertEquals(InitPlan.PROCEED, launch().beforeInit("v2"))
        assertEquals(InitPlan.PROCEED, launch().beforeInit("v2"))
        assertEquals(InitPlan.REVERT, launch().beforeInit("v2"))
    }

    @Test
    fun `quarantine bans a version and clears the count`() {
        assertFalse(launch().isQuarantined("v1"))
        launch().quarantine("v1")
        assertTrue(launch().isQuarantined("v1"))
        // After quarantine the guard is clean for whatever loads next.
        assertEquals(InitPlan.PROCEED, launch().beforeInit("v2"))
    }
}
