package eu.dotshell.pelo.generic.utils.navigation

import eu.dotshell.pelo.generic.ui.screens.plan.NavigationInstruction
import eu.dotshell.pelo.generic.ui.screens.plan.NavigationModeUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The voice speaks off a state rebuilt every second, so what matters is not what it says once but
 * what it refuses to say again.
 */
class NavigationVoiceCueTest {

    private fun state(instruction: NavigationInstruction) = NavigationModeUiState(
        currentLeg = null,
        displayedLeg = null,
        previousLeg = null,
        upcomingLeg = null,
        shouldChangeLine = false,
        instruction = instruction,
        direction = null,
        remainingSeconds = 600,
        arrivalTimeText = "08:30",
        isArrived = instruction is NavigationInstruction.Arrived,
        isOffRoute = false,
        canReroute = false,
        isDeadReckoning = false,
    )

    private fun cueKey(instruction: NavigationInstruction) =
        navigationVoiceCueFor(state(instruction))?.key

    @Test
    fun `nothing is said before the guidance can be trusted`() {
        assertNull(cueKey(NavigationInstruction.AcquiringSignal))
        assertNull(cueKey(NavigationInstruction.InProgress))
    }

    @Test
    fun `a ride still far out stays silent`() {
        // Boarding was already announced; counting down every stop of a twelve-stop leg is noise.
        assertNull(cueKey(NavigationInstruction.RideTo("Part-Dieu", remainingStops = 8, changesLine = false)))
    }

    @Test
    fun `the same step is never announced twice`() {
        val first = cueKey(NavigationInstruction.RideTo("Saxe", remainingStops = 3, changesLine = false))
        val again = cueKey(NavigationInstruction.RideTo("Saxe", remainingStops = 2, changesLine = false))
        assertEquals("both sit in the same bucket", first, again)
    }

    @Test
    fun `a flickering stop count cannot make the voice stutter`() {
        // The nearest-stop scan can oscillate between two values on a boundary; both must map to
        // one cue or the voice would repeat itself every second.
        val a = cueKey(NavigationInstruction.RideTo("Saxe", remainingStops = 3, changesLine = false))
        val b = cueKey(NavigationInstruction.RideTo("Saxe", remainingStops = 2, changesLine = false))
        val c = cueKey(NavigationInstruction.RideTo("Saxe", remainingStops = 3, changesLine = false))
        assertEquals(a, b)
        assertEquals(b, c)
    }

    @Test
    fun `approaching the stop is announced again as it gets close`() {
        val soon = cueKey(NavigationInstruction.RideTo("Saxe", remainingStops = 3, changesLine = false))
        val next = cueKey(NavigationInstruction.RideTo("Saxe", remainingStops = 1, changesLine = false))
        val now = cueKey(NavigationInstruction.RideTo("Saxe", remainingStops = 0, changesLine = false))
        assertNotEquals(soon, next)
        assertNotEquals(next, now)
    }

    @Test
    fun `a line change is its own announcement`() {
        val alight = cueKey(NavigationInstruction.RideTo("Saxe", remainingStops = 1, changesLine = false))
        val change = cueKey(NavigationInstruction.RideTo("Saxe", remainingStops = 1, changesLine = true))
        assertNotEquals("missing a change costs the journey", alight, change)
    }

    @Test
    fun `changing target stop is a new announcement`() {
        val first = cueKey(NavigationInstruction.RideTo("Saxe", remainingStops = 1, changesLine = false))
        val second = cueKey(NavigationInstruction.RideTo("Part-Dieu", remainingStops = 1, changesLine = false))
        assertNotEquals(first, second)
    }

    @Test
    fun `walking and boarding are announced once each`() {
        assertEquals("walk:Bellecour", cueKey(NavigationInstruction.WalkTo("Bellecour", 320)))
        // The distance ticks down continuously; it must not re-trigger the announcement.
        assertEquals("walk:Bellecour", cueKey(NavigationInstruction.WalkTo("Bellecour", 180)))

        assertEquals("board:Bellecour", cueKey(NavigationInstruction.BoardAt("Bellecour", 120)))
        assertEquals("board:Bellecour", cueKey(NavigationInstruction.BoardAt("Bellecour", 30)))
    }

    @Test
    fun `arrival is announced`() {
        assertEquals("arrived", cueKey(NavigationInstruction.Arrived))
    }

    @Test
    fun `the cue carries the instruction so the voice and the screen cannot disagree`() {
        val instruction = NavigationInstruction.RideTo("Saxe", remainingStops = 1, changesLine = false)
        assertEquals(instruction, navigationVoiceCueFor(state(instruction))?.instruction)
    }
}
