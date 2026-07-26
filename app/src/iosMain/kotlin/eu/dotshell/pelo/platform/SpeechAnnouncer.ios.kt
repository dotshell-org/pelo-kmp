@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package eu.dotshell.pelo.platform

import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDuckOthers
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionModeVoicePrompt
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechUtterance
import platform.AVFAudio.setActive

/**
 * iOS actual backed by [AVSpeechSynthesizer].
 *
 * The session is configured once as a voice prompt that ducks other audio, so a navigation
 * instruction lowers the music for a sentence instead of stopping it. It is activated around each
 * utterance rather than held for the whole journey — holding it would keep other apps ducked in
 * the silence between instructions.
 */
actual class SpeechAnnouncer actual constructor(context: PlatformContext) {

    private val synthesizer = AVSpeechSynthesizer()
    private var isDisposed = false

    actual fun speak(text: String) {
        if (isDisposed || text.isBlank()) return

        activateSession()

        // Superseded guidance is never worth queueing behind: drop whatever is still being said.
        if (synthesizer.isSpeaking()) {
            synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        }

        val utterance = AVSpeechUtterance(string = text)
        // Follows the app's locale rather than the device's, so a French app speaks French even on
        // an English phone — the instructions themselves are resolved the same way.
        utterance.voice = AVSpeechSynthesisVoice.voiceWithLanguage(LanguageManager.current.tag)
            ?: AVSpeechSynthesisVoice.voiceWithLanguage("fr-FR")
        synthesizer.speakUtterance(utterance)
    }

    actual fun stop() {
        if (isDisposed) return
        if (synthesizer.isSpeaking()) {
            synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        }
        deactivateSession()
    }

    actual fun dispose() {
        if (isDisposed) return
        isDisposed = true
        if (synthesizer.isSpeaking()) {
            synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        }
        deactivateSession()
    }

    private fun activateSession() {
        val session = AVAudioSession.sharedInstance()
        runCatching {
            session.setCategory(
                category = AVAudioSessionCategoryPlayback,
                mode = AVAudioSessionModeVoicePrompt,
                options = AVAudioSessionCategoryOptionDuckOthers,
                error = null,
            )
            session.setActive(true, null)
        }
    }

    private fun deactivateSession() {
        runCatching { AVAudioSession.sharedInstance().setActive(false, null) }
    }
}
