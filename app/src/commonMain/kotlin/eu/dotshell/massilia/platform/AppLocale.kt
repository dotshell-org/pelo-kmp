package eu.dotshell.massilia.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Languages the user can pick in-app. An empty [tag] means "follow the system language".
 * Language names are conventionally shown in their own language; the SYSTEM entry's label is
 * localised separately (string key `language_system`).
 */
enum class AppLanguage(val tag: String, val nativeName: String) {
    SYSTEM("", ""),
    FRENCH("fr", "Français"),
    ENGLISH("en", "English");

    companion object {
        fun fromTag(tag: String): AppLanguage = entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}

/**
 * Holds the chosen [AppLanguage] as Compose state and persists it via [Settings].
 * [init] must be called once (from each platform entry) before the value is read in composition.
 */
object LanguageManager {
    private const val STORE = "app_settings"
    private const val KEY = "app_language_tag"
    private var settings: Settings? = null

    var current by mutableStateOf(AppLanguage.SYSTEM)
        private set

    fun init(context: PlatformContext) {
        if (settings != null) return
        val s = Settings(context, STORE)
        settings = s
        current = AppLanguage.fromTag(s.getString(KEY, ""))
    }

    fun set(language: AppLanguage) {
        current = language
        settings?.putString(KEY, language.tag)
    }
}

/**
 * Applies [languageTag] (empty = system language) to every Compose Resources lookup inside
 * [content]. Android overrides the configuration live; iOS persists it for the next launch.
 */
@Composable
expect fun ProvideAppLocale(languageTag: String, content: @Composable () -> Unit)
