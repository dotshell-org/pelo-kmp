package eu.dotshell.pelo.platform

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Android actual backed by [TextToSpeech].
 *
 * The engine initialises asynchronously, so anything asked for before it is ready is held as a
 * single pending utterance — held, not queued: by the time the engine wakes up, only the most
 * recent instruction is still true.
 */
actual class SpeechAnnouncer actual constructor(private val context: PlatformContext) {

    private val audioManager =
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as? AudioManager

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private var focusRequest: AudioFocusRequest? = null
    private var isReady = false
    private var isDisposed = false
    private var pendingText: String? = null

    private val engine: TextToSpeech = TextToSpeech(context) { status ->
        isReady = status == TextToSpeech.SUCCESS
        if (isReady) {
            engine.language = Locale.getDefault()
            engine.setAudioAttributes(attributes)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = abandonFocus()

                @Deprecated("Kept for the pre-21 signature; the API 21+ overload delegates here.")
                override fun onError(utteranceId: String?) = abandonFocus()
            })
            pendingText?.let { text ->
                pendingText = null
                speak(text)
            }
        }
    }

    actual fun speak(text: String) {
        if (isDisposed || text.isBlank()) return
        if (!isReady) {
            pendingText = text
            return
        }
        requestFocus()
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
    }

    actual fun stop() {
        pendingText = null
        if (isReady) engine.stop()
        abandonFocus()
    }

    actual fun dispose() {
        if (isDisposed) return
        isDisposed = true
        pendingText = null
        runCatching { engine.stop() }
        runCatching { engine.shutdown() }
        abandonFocus()
    }

    /**
     * Transient focus that allows ducking: navigation should lower the music for a sentence, not
     * pause it, and certainly not stop it for the whole journey.
     */
    private fun requestFocus() {
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (focusRequest != null) return
            val request = AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .build()
            focusRequest = request
            manager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    private fun abandonFocus() {
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { manager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(null)
        }
    }
}
