package eu.dotshell.pelo.generic.utils.navigation

import eu.dotshell.pelo.generic.ui.screens.plan.NavigationInstruction
import eu.dotshell.pelo.generic.ui.screens.plan.NavigationModeUiState

/**
 * One thing worth saying out loud, plus the identity that decides whether it has been said.
 *
 * [key] is what dedupes: the guidance state is rebuilt every second, so speaking on every change
 * would talk over itself continuously. Two states that mean the same thing to a listener produce
 * the same key and are spoken once.
 */
data class NavigationVoiceCue(
    val key: String,
    val instruction: NavigationInstruction,
)

/**
 * Decides what the voice should say for [state], or null when there is nothing new to announce.
 *
 * The rules are about what a traveller needs to hear rather than what changed on screen:
 *
 *  - a ride is announced on boarding, then again only as the stop to get off approaches — a
 *    countdown read out at every single stop is noise, and the screen already shows it;
 *  - a line change is called out as its own event, because missing it costs the journey;
 *  - nothing is said while the signal is being acquired, since the guidance is not yet trustworthy.
 */
fun navigationVoiceCueFor(state: NavigationModeUiState): NavigationVoiceCue? {
    val instruction = state.instruction
    return when (instruction) {
        is NavigationInstruction.AcquiringSignal -> null
        is NavigationInstruction.InProgress -> null

        is NavigationInstruction.Arrived -> NavigationVoiceCue("arrived", instruction)

        is NavigationInstruction.WalkTo ->
            NavigationVoiceCue("walk:${instruction.stopName}", instruction)

        is NavigationInstruction.BoardAt ->
            NavigationVoiceCue("board:${instruction.stopName}", instruction)

        is NavigationInstruction.RideTo -> {
            // Buckets, not raw counts: the traveller hears "three stops", then "next stop", and
            // nothing in between. A stop count that flickers between two values on the nearest-stop
            // boundary also cannot make the voice stutter, because both land in the same bucket.
            val bucket = when {
                instruction.remainingStops <= 0 -> "now"
                instruction.remainingStops == 1 -> "next"
                instruction.remainingStops <= 3 -> "soon"
                else -> return null // still far out; boarding was already announced
            }
            val kind = if (instruction.changesLine) "change" else "alight"
            NavigationVoiceCue("$kind:${instruction.stopName}:$bucket", instruction)
        }
    }
}
