package eu.dotshell.massilia.platform

import androidx.compose.runtime.Composable

/**
 * No-op on Android: Compose Multiplatform Resources automatically resolves strings
 * based on the system locale (values-en/ for English, values/ as French fallback).
 * Language selection has been removed from the app settings.
 */
@Composable
actual fun ProvideAppLocale(languageTag: String, content: @Composable () -> Unit) {
    content()
}
