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
import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

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
    /** Enumerating the installed voices is not free; the language rarely changes mid-journey. */
    private var cachedVoice: Pair<String, AVSpeechSynthesisVoice?>? = null

    actual fun speak(text: String) {
        if (isDisposed || text.isBlank()) return

        activateSession()

        // Superseded guidance is never worth queueing behind: drop whatever is still being said.
        if (synthesizer.isSpeaking()) {
            synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        }

        val utterance = AVSpeechUtterance(string = text)
        // Left null when no voice matches, so the system picks its own default. Forcing a
        // particular voice as a fallback is what made English instructions come out in a French
        // accent.
        voiceFor(speechLanguage())?.let { utterance.voice = it }
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

    /**
     * The language the instructions are actually written in.
     *
     * The app ships French and English, French being the default resource set, and the in-app
     * language may be "follow the system". Resolving it the same way Compose Resources does is
     * what keeps the voice and the text in the same language — reading the raw preference tag
     * gave an empty string under "system", which matched no voice at all.
     */
    private fun speechLanguage(): String {
        val explicit = LanguageManager.current.tag
        if (explicit.isNotBlank()) return explicit
        val preferred = NSLocale.preferredLanguages.firstOrNull() as? String ?: return "fr"
        return if (preferred.substringBefore('-').lowercase() == "en") "en" else "fr"
    }

    /**
     * A voice for [languageCode], matched on the base language.
     *
     * `voiceWithLanguage` wants a full BCP-47 tag: asking it for "en" returns nothing on most
     * devices, which is why an exact-tag lookup alone is not enough.
     */
    private fun voiceFor(languageCode: String): AVSpeechSynthesisVoice? {
        cachedVoice?.let { (tag, voice) -> if (tag == languageCode) return voice }

        val exact = AVSpeechSynthesisVoice.voiceWithLanguage(languageCode)
        val resolved = exact ?: AVSpeechSynthesisVoice.speechVoices()
            .filterIsInstance<AVSpeechSynthesisVoice>()
            .firstOrNull { it.language.substringBefore('-').equals(languageCode, ignoreCase = true) }

        cachedVoice = languageCode to resolved
        return resolved
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
