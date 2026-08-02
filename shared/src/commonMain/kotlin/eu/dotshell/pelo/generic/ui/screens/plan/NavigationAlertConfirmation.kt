package eu.dotshell.pelo.generic.ui.screens.plan

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import eu.dotshell.pelo.generic.data.models.navigation.NavigationAlertPrompt
import eu.dotshell.pelo.generic.data.models.navigation.NavigationAlertPromptKind
import eu.dotshell.pelo.generic.service.NavigationSession
import eu.dotshell.pelo.generic.service.TransportServiceProvider
import eu.dotshell.pelo.generic.ui.viewmodel.TransportViewModel
import eu.dotshell.pelo.platform.Log
import eu.dotshell.pelo.platform.ioDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Asks the traveller to settle an alert about the stop they are standing at.
 *
 * The timing is the whole point. A confirmation prompt is only worth anything if the person
 * answering can see the answer — asked while planning, about a stop forty minutes away, it can
 * only be guessed at, and a guessed answer is worse than no answer: it carries the same weight in
 * the karma that decides whether everyone else's itinerary gets bent.
 *
 * So the question follows the journey rather than preceding it: it appears when navigation reports
 * the traveller has reached the stop the alert is about, and only then.
 */
@Composable
fun NavigationAlertConfirmation(
    viewModel: TransportViewModel,
    session: NavigationSession,
    isNavigating: Boolean
) {
    val realtimeConfig = remember { TransportServiceProvider.getRealtimeConfig() }
    if (!realtimeConfig.userStopAlertsEnabled) return

    val scope = rememberCoroutineScope()
    var prompt by remember { mutableStateOf<NavigationAlertPrompt?>(null) }

    val currentStopName = if (isNavigating) session.currentTransitStopName() else null

    // Keyed on the stop: arriving somewhere new is what makes a new question askable, and leaving
    // clears whatever was still on screen for the previous one.
    LaunchedEffect(currentStopName) {
        prompt = null
        val stopName = currentStopName ?: return@LaunchedEffect
        try {
            val disruptions = withContext(ioDispatcher) {
                viewModel.userStopAlertsRepository.disruptionsFor(listOf(stopName), emptyList())
            }
            // Only alerts about *this* stop: the query can return line-scoped ones the traveller
            // has no better view of than anybody else.
            prompt = disruptions.pendingConfirmations.firstOrNull { it.alert.stopId != null }
        } catch (e: Exception) {
            Log.w(TAG, "Could not load alerts to confirm at $stopName: ${e.message}")
        }
    }

    prompt?.let { current ->
        NavigationAlertConfirmationDialog(
            prompt = current,
            onAnswer = { confirm ->
                // Dismissed before the call returns: the traveller has answered, and making them
                // watch a spinner for it is how a one-tap question stops being one.
                prompt = null
                scope.launch {
                    viewModel.userStopAlertsRepository.vote(current.alert.id, confirm)
                }
            },
            onDismiss = { prompt = null }
        )
    }
}

/**
 * The stop the traveller is at on the current transit leg, or null when walking, arrived, or off
 * the route — none of which is a position from which to vouch for anything.
 */
private fun NavigationSession.currentTransitStopName(): String? {
    if (!isActive || progress.isArrived || progress.isOffRoute) return null
    val leg = journey?.legs?.getOrNull(progress.legIndex) ?: return null
    if (leg.isWalking) return null

    val chain = buildList {
        add(leg.fromStopName)
        leg.intermediateStops.forEach { add(it.stopName) }
        add(leg.toStopName)
    }
    return chain.getOrNull(progress.stopIndex)?.takeIf { it.isNotBlank() }
}

@Composable
private fun NavigationAlertConfirmationDialog(
    prompt: NavigationAlertPrompt,
    onAnswer: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val where = prompt.alert.stopId.orEmpty()
    val what = alertTypeLabel(prompt.alert.type)
    val question = when (prompt.kind) {
        NavigationAlertPromptKind.LOW_KARMA_CONFIRM -> "Un usager signale : $what. Vous confirmez ?"
        NavigationAlertPromptKind.HIGH_KARMA_STILL_THERE -> "$what : c'est toujours le cas ?"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(where.ifBlank { "Signalement" }) },
        text = { Text(question) },
        confirmButton = {
            TextButton(onClick = { onAnswer(true) }) { Text("Oui") }
        },
        dismissButton = {
            TextButton(onClick = { onAnswer(false) }) { Text("Non") }
        }
    )
}

/** Human wording for an alert type id, falling back to the raw id for types added server-side. */
private fun alertTypeLabel(type: String): String = when (type.lowercase()) {
    "closure" -> "arrêt fermé"
    "delay" -> "un retard"
    "elevator" -> "ascenseur hors service"
    "crowding" -> "une forte affluence"
    "works" -> "des travaux"
    "strike" -> "une grève"
    "fire" -> "un incendie"
    "interruption" -> "une interruption"
    "congestion" -> "un trafic élevé"
    else -> type
}

private const val TAG = "NavigationAlertConfirm"
