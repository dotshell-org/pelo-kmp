package eu.dotshell.pelo.generic.ui.screens.plan

import eu.dotshell.pelo.resources.Res
import eu.dotshell.pelo.resources.allStringResources
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the choice of wording, which is now made once and resolved twice — on screen and, off
 * composition, in the ongoing notification and the Live Activity. A spec that drifts here is a
 * notification that disagrees with the card the traveller was just looking at.
 */
class NavigationInstructionTextTest {

    @Test
    fun `stateless instructions carry no arguments`() {
        assertEquals(
            NavigationTextSpec("nav_acquiring_signal"),
            NavigationInstruction.AcquiringSignal.textSpec(),
        )
        assertEquals(NavigationTextSpec("nav_in_progress"), NavigationInstruction.InProgress.textSpec())
        assertEquals(NavigationTextSpec("nav_arrived"), NavigationInstruction.Arrived.textSpec())
    }

    @Test
    fun `walking without a fix omits the distance`() {
        val spec = NavigationInstruction.WalkTo("Bellecour", distanceMeters = null).textSpec()
        assertEquals(
            NavigationTextSpec("nav_walk_to", listOf(NavigationTextArg.Text("Bellecour"))),
            spec,
        )
    }

    @Test
    fun `walking with a fix nests the distance`() {
        val spec = NavigationInstruction.WalkTo("Bellecour", distanceMeters = 240).textSpec()
        assertEquals(
            NavigationTextSpec(
                key = "nav_walk_to_distance",
                args = listOf(
                    NavigationTextArg.Text("Bellecour"),
                    NavigationTextArg.Nested(
                        NavigationTextSpec("distance_meters", listOf(NavigationTextArg.Number(240)))
                    ),
                ),
            ),
            spec,
        )
    }

    @Test
    fun `boarding nests the countdown before the stop name`() {
        val spec = NavigationInstruction.BoardAt("Part-Dieu", secondsUntilDeparture = 300).textSpec()
        assertEquals(
            NavigationTextSpec(
                key = "nav_board_at",
                args = listOf(
                    NavigationTextArg.Nested(
                        NavigationTextSpec("duration_minutes", listOf(NavigationTextArg.Number(5)))
                    ),
                    NavigationTextArg.Text("Part-Dieu"),
                ),
            ),
            spec,
        )
    }

    @Test
    fun `the last stop drops the count entirely`() {
        assertEquals(
            "nav_ride_next_stop",
            NavigationInstruction.RideTo("Jean Macé", remainingStops = 0, changesLine = false)
                .textSpec().key,
        )
        assertEquals(
            "nav_ride_next_stop_change",
            NavigationInstruction.RideTo("Jean Macé", remainingStops = 0, changesLine = true)
                .textSpec().key,
        )
    }

    @Test
    fun `the remaining stop count picks the singular or plural key`() {
        fun key(stops: Int, changes: Boolean) =
            NavigationInstruction.RideTo("Jean Macé", stops, changes).textSpec().key

        assertEquals("nav_ride_stops_one", key(1, false))
        assertEquals("nav_ride_stops_other", key(2, false))
        assertEquals("nav_ride_stops_change_one", key(1, true))
        assertEquals("nav_ride_stops_change_other", key(4, true))
    }

    @Test
    fun `durations cross from seconds to minutes to hours`() {
        assertEquals(NavigationTextSpec("duration_less_than_a_minute"), navigationDurationSpec(59))
        assertEquals(
            NavigationTextSpec("duration_minutes", listOf(NavigationTextArg.Number(1))),
            navigationDurationSpec(60),
        )
        assertEquals(
            NavigationTextSpec("duration_minutes", listOf(NavigationTextArg.Number(59))),
            navigationDurationSpec(59 * 60 + 59),
        )
        // 1 h 05, and the minutes stay zero-padded so the reading is unambiguous.
        assertEquals(
            NavigationTextSpec(
                key = "duration_hours_minutes",
                args = listOf(NavigationTextArg.Number(1), NavigationTextArg.Text("05")),
            ),
            navigationDurationSpec(65 * 60),
        )
    }

    @Test
    fun `distances cross from metres to kilometres`() {
        assertEquals(
            NavigationTextSpec("distance_meters", listOf(NavigationTextArg.Number(999))),
            navigationDistanceSpec(999),
        )
        // 1234 m reads as 1,2 km — the separator is resolved later, from the locale.
        assertEquals(
            NavigationTextSpec("distance_kilometers", listOf(NavigationTextArg.Decimal(1, 2))),
            navigationDistanceSpec(1234),
        )
        assertEquals(
            NavigationTextSpec("distance_kilometers", listOf(NavigationTextArg.Decimal(1, 0))),
            navigationDistanceSpec(1000),
        )
    }

    /**
     * The lookup is by name, so a typo in a key is not a compile error — it is a crash on the road,
     * inside the guidance, at the moment the instruction changes.
     */
    @OptIn(ExperimentalResourceApi::class)
    @Test
    fun `every key a spec can produce exists in the resource registry`() {
        val instructions = listOf(
            NavigationInstruction.AcquiringSignal,
            NavigationInstruction.InProgress,
            NavigationInstruction.Arrived,
            NavigationInstruction.WalkTo("A", distanceMeters = null),
            NavigationInstruction.WalkTo("A", distanceMeters = 240),
            NavigationInstruction.WalkTo("A", distanceMeters = 2400),
            NavigationInstruction.BoardAt("A", secondsUntilDeparture = 30),
            NavigationInstruction.BoardAt("A", secondsUntilDeparture = 300),
            NavigationInstruction.BoardAt("A", secondsUntilDeparture = 4000),
            NavigationInstruction.RideTo("A", remainingStops = 0, changesLine = false),
            NavigationInstruction.RideTo("A", remainingStops = 0, changesLine = true),
            NavigationInstruction.RideTo("A", remainingStops = 1, changesLine = false),
            NavigationInstruction.RideTo("A", remainingStops = 1, changesLine = true),
            NavigationInstruction.RideTo("A", remainingStops = 3, changesLine = false),
            NavigationInstruction.RideTo("A", remainingStops = 3, changesLine = true),
        )

        val keys = (instructions.flatMap { it.textSpec().keys() } + "decimal_separator").toSet()
        val missing = keys.filterNot { Res.allStringResources.containsKey(it) }
        assertTrue("Missing string resources: $missing", missing.isEmpty())
    }

    private fun NavigationTextSpec.keys(): List<String> =
        listOf(key) + args.filterIsInstance<NavigationTextArg.Nested>().flatMap { it.spec.keys() }
}
