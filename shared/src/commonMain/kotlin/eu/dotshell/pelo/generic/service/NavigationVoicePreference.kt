package eu.dotshell.pelo.generic.service

import eu.dotshell.pelo.platform.PlatformContext
import eu.dotshell.pelo.platform.Settings

/**
 * Whether spoken guidance is on. Persisted so the choice survives the session — being made to
 * mute the voice at the start of every journey would be its own annoyance.
 *
 * Defaults to on: a navigation mode whose whole point is not having to watch the screen should not
 * need to be discovered in a settings menu first. The overlay carries the toggle.
 */
object NavigationVoicePreference {

    private const val PREFS_NAME = "navigation_mode_prefs"
    private const val KEY_VOICE_ENABLED = "voice_guidance_enabled"

    fun isEnabled(context: PlatformContext): Boolean =
        Settings(context, PREFS_NAME).getBoolean(KEY_VOICE_ENABLED, true)

    fun setEnabled(context: PlatformContext, enabled: Boolean) {
        Settings(context, PREFS_NAME).putBoolean(KEY_VOICE_ENABLED, enabled)
    }
}
