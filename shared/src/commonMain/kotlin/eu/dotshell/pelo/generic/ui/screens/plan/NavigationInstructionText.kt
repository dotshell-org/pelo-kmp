package eu.dotshell.pelo.generic.ui.screens.plan

import androidx.compose.runtime.Composable
import eu.dotshell.pelo.platform.LocalPlatformContext
import eu.dotshell.pelo.platform.StringProvider
import eu.dotshell.pelo.resources.Res
import eu.dotshell.pelo.resources.allStringResources
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.getString

/**
 * Which localisable string an instruction needs, and with which arguments — decided without
 * reading a single resource.
 *
 * The wording used to live inside a `@Composable`, which meant only the screen could produce it:
 * the ongoing notification and the Live Activity had to be fed from composition, and froze with it
 * the moment the app left the screen. Splitting the *choice* from the *lookup* lets the same
 * decision be resolved twice — in composition by [displayText], from a plain coroutine by
 * [resolveText] — with no second copy of the wording to keep in step.
 */
data class NavigationTextSpec(
    val key: String,
    val args: List<NavigationTextArg> = emptyList(),
)

sealed interface NavigationTextArg {

    data class Text(val value: String) : NavigationTextArg

    data class Number(val value: Int) : NavigationTextArg

    /**
     * [whole] and [tenth] joined by the locale's decimal separator, which is itself a resource —
     * hence a dedicated kind rather than a pre-formatted string.
     */
    data class Decimal(val whole: Int, val tenth: Int) : NavigationTextArg

    /** An argument that is itself a localised string: a distance, a duration. */
    data class Nested(val spec: NavigationTextSpec) : NavigationTextArg
}

/** The sentence this instruction calls for. Pure, so it is directly unit-testable. */
fun NavigationInstruction.textSpec(): NavigationTextSpec = when (this) {
    is NavigationInstruction.AcquiringSignal -> NavigationTextSpec("nav_acquiring_signal")
    is NavigationInstruction.InProgress -> NavigationTextSpec("nav_in_progress")
    is NavigationInstruction.Arrived -> NavigationTextSpec("nav_arrived")

    is NavigationInstruction.WalkTo -> if (distanceMeters != null) {
        NavigationTextSpec(
            key = "nav_walk_to_distance",
            args = listOf(
                NavigationTextArg.Text(stopName),
                NavigationTextArg.Nested(navigationDistanceSpec(distanceMeters)),
            ),
        )
    } else {
        NavigationTextSpec("nav_walk_to", listOf(NavigationTextArg.Text(stopName)))
    }

    is NavigationInstruction.BoardAt -> NavigationTextSpec(
        key = "nav_board_at",
        args = listOf(
            NavigationTextArg.Nested(navigationDurationSpec(secondsUntilDeparture)),
            NavigationTextArg.Text(stopName),
        ),
    )

    is NavigationInstruction.RideTo -> when {
        remainingStops <= 0 && changesLine ->
            NavigationTextSpec("nav_ride_next_stop_change", listOf(NavigationTextArg.Text(stopName)))

        remainingStops <= 0 ->
            NavigationTextSpec("nav_ride_next_stop", listOf(NavigationTextArg.Text(stopName)))

        // Compose's plural resources are not exposed through the by-name registry these strings
        // are read from, and fr/en both need only the one/other split — so the count picks the key.
        else -> NavigationTextSpec(
            key = ridePluralKey(remainingStops, changesLine),
            args = listOf(
                NavigationTextArg.Text(stopName),
                NavigationTextArg.Number(remainingStops),
            ),
        )
    }
}

private fun ridePluralKey(remainingStops: Int, changesLine: Boolean): String = when {
    changesLine && remainingStops == 1 -> "nav_ride_stops_change_one"
    changesLine -> "nav_ride_stops_change_other"
    remainingStops == 1 -> "nav_ride_stops_one"
    else -> "nav_ride_stops_other"
}

fun navigationDurationSpec(seconds: Int): NavigationTextSpec {
    if (seconds < 60) return NavigationTextSpec("duration_less_than_a_minute")
    val minutes = seconds / 60
    return if (minutes < 60) {
        NavigationTextSpec("duration_minutes", listOf(NavigationTextArg.Number(minutes)))
    } else {
        NavigationTextSpec(
            key = "duration_hours_minutes",
            args = listOf(
                NavigationTextArg.Number(minutes / 60),
                NavigationTextArg.Text((minutes % 60).toString().padStart(2, '0')),
            ),
        )
    }
}

fun navigationDistanceSpec(meters: Int): NavigationTextSpec =
    if (meters < 1000) {
        NavigationTextSpec("distance_meters", listOf(NavigationTextArg.Number(meters)))
    } else {
        NavigationTextSpec(
            key = "distance_kilometers",
            args = listOf(NavigationTextArg.Decimal(meters / 1000, (meters % 1000) / 100)),
        )
    }

/** The instruction as a sentence in the active locale, for the screen and the voice guidance. */
@Composable
fun NavigationInstruction.displayText(): String =
    textSpec().resolve(StringProvider(LocalPlatformContext.current))

@Composable
private fun NavigationTextSpec.resolve(strings: StringProvider): String {
    if (args.isEmpty()) return strings[key]
    val resolved = arrayOfNulls<Any>(args.size)
    for (index in args.indices) {
        resolved[index] = when (val arg = args[index]) {
            is NavigationTextArg.Text -> arg.value
            is NavigationTextArg.Number -> arg.value
            is NavigationTextArg.Decimal -> "${arg.whole}${strings["decimal_separator"]}${arg.tenth}"
            is NavigationTextArg.Nested -> arg.spec.resolve(strings)
        }
    }
    @Suppress("UNCHECKED_CAST")
    return strings.format(key, *(resolved as Array<Any>))
}

/**
 * Same sentence, from anywhere: the foreground service and the Live Activity need it off the
 * composition, and Compose Resources exposes a suspending lookup for exactly that.
 */
suspend fun NavigationInstruction.resolveText(): String = textSpec().resolveOffComposition()

@OptIn(ExperimentalResourceApi::class)
private suspend fun NavigationTextSpec.resolveOffComposition(): String {
    val resource = Res.allStringResources.getValue(key)
    if (args.isEmpty()) return getString(resource)
    val resolved = arrayOfNulls<Any>(args.size)
    for (index in args.indices) {
        resolved[index] = when (val arg = args[index]) {
            is NavigationTextArg.Text -> arg.value
            is NavigationTextArg.Number -> arg.value
            is NavigationTextArg.Decimal -> {
                val separator = getString(Res.allStringResources.getValue("decimal_separator"))
                "${arg.whole}$separator${arg.tenth}"
            }
            is NavigationTextArg.Nested -> arg.spec.resolveOffComposition()
        }
    }
    @Suppress("UNCHECKED_CAST")
    return getString(resource, *(resolved as Array<Any>))
}
