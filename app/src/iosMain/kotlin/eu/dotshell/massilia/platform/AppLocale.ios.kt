package eu.dotshell.massilia.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import platform.Foundation.NSUserDefaults

/**
 * iOS reads the resource locale from the system bundle, so a chosen language can't be applied to
 * the running process the way Android can. We persist it into `AppleLanguages`; it takes effect on
 * the next launch. (System language = empty tag = clear the override.)
 */
@Composable
actual fun ProvideAppLocale(languageTag: String, content: @Composable () -> Unit) {
    LaunchedEffect(languageTag) {
        val defaults = NSUserDefaults.standardUserDefaults
        if (languageTag.isEmpty()) {
            defaults.removeObjectForKey("AppleLanguages")
        } else {
            defaults.setObject(listOf(languageTag), forKey = "AppleLanguages")
        }
        defaults.synchronize()
    }
    content()
}
