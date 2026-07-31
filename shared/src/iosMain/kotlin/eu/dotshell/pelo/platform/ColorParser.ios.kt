package eu.dotshell.pelo.platform

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

actual fun parseComposeColor(value: String?): Color? {
    if (value.isNullOrBlank()) return null
    return try {
        val hex = value.trim().removePrefix("#")
        when (hex.length) {
            6 -> {
                val r = hex.substring(0, 2).toInt(16)
                val g = hex.substring(2, 4).toInt(16)
                val b = hex.substring(4, 6).toInt(16)
                Color(r, g, b)
            }
            8 -> {
                val a = hex.substring(0, 2).toInt(16)
                val r = hex.substring(2, 4).toInt(16)
                val g = hex.substring(4, 6).toInt(16)
                val b = hex.substring(6, 8).toInt(16)
                Color(r, g, b, a)
            }
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

actual fun parseComposeColor(value: String?, fallback: Color): Color {
    return parseComposeColor(value) ?: fallback
}
