package eu.dotshell.massilia.platform

import androidx.compose.ui.graphics.Color

actual fun parseComposeColor(value: String?): Color? {
    if (value.isNullOrBlank()) return null
    return try {
        Color(android.graphics.Color.parseColor(value))
    } catch (_: Exception) {
        null
    }
}

actual fun parseComposeColor(value: String?, fallback: Color): Color {
    return parseComposeColor(value) ?: fallback
}
