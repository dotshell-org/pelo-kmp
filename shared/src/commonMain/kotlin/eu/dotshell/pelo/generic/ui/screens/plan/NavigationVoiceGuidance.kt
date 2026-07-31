package eu.dotshell.pelo.generic.ui.screens.plan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import eu.dotshell.pelo.generic.utils.navigation.navigationVoiceCueFor
import eu.dotshell.pelo.platform.LocalPlatformContext
import eu.dotshell.pelo.platform.SpeechAnnouncer

/**
 * Speaks the guidance. Renders nothing.
 *
 * The wording comes from the same resources as the screen — [displayText] — so the voice and the
 * card never disagree, and both follow the app's locale. What to say and when is decided by
 * [navigationVoiceCueFor], which is pure and unit-tested; this only owns the engine's lifetime.
 */
@Composable
fun NavigationVoiceGuidance(
    state: NavigationModeUiState,
    isEnabled: Boolean,
) {
    val context = LocalPlatformContext.current
    val announcer = remember(context) { SpeechAnnouncer(context) }

    DisposableEffect(announcer) {
        onDispose { announcer.dispose() }
    }

    // Muting mid-sentence should be immediate — waiting for the current instruction to finish is
    // exactly what someone reaching for the mute button does not want.
    DisposableEffect(isEnabled) {
        if (!isEnabled) announcer.stop()
        onDispose { }
    }

    val cue = remember(state) { navigationVoiceCueFor(state) }
    // Resolved in composition: string resources are not readable from inside the effect.
    val spoken = cue?.instruction?.displayText()

    LaunchedEffect(cue?.key, isEnabled) {
        if (isEnabled && cue != null && spoken != null) {
            announcer.speak(spoken)
        }
    }
}
