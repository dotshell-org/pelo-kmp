package eu.dotshell.pelo.platform

/**
 * Speaks short navigation instructions out loud.
 *
 * Implementations take transient audio focus so they duck music or a podcast rather than fight it,
 * and release it as soon as the utterance ends. Announcements replace one another: guidance that
 * has been superseded is never worth queueing behind what is current.
 */
expect class SpeechAnnouncer(context: PlatformContext) {

    /** Speak [text], interrupting anything still being said. */
    fun speak(text: String)

    /** Stop mid-sentence and drop anything pending. */
    fun stop()

    /** Release the engine and any audio focus held. */
    fun dispose()
}
