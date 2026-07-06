package eu.dotshell.massilia.generic.utils

import eu.dotshell.massilia.generic.data.models.geojson.Feature
import eu.dotshell.massilia.platform.provideLineColors
import kotlin.concurrent.Volatile

object LineColorHelper {

    // Copy-on-write cache (see FrenchPublicHolidayStrategy): lock-free reads, no CME if accessed
    // off the main thread. A racing double-compute just recomputes the same color once.
    @Volatile
    private var colorIntCache: Map<String, Int> = emptyMap()

    private class CompiledRule(
        val type: String,
        val match: String,
        val color: String,
        val regex: Regex?,
    )

    // Compile the colour rules (incl. regexes) once instead of on every resolveColorHex call.
    // A malformed regex pattern compiles to null and is skipped rather than throwing at render.
    private val compiledRules: List<CompiledRule> by lazy {
        provideLineColors().rules.map { rule ->
            val type = rule.type.lowercase()
            CompiledRule(
                type = type,
                match = rule.match.trim().uppercase(),
                color = rule.color,
                regex = if (type == "regex") runCatching { Regex(rule.match) }.getOrNull() else null,
            )
        }
    }
    private val fallbackColor: String by lazy { provideLineColors().fallback }

    private fun resolveColorHex(lineName: String): String {
        val upper = lineName.trim().uppercase()
        for (rule in compiledRules) {
            when (rule.type) {
                "exact" -> if (upper == rule.match) return rule.color
                "prefix" -> if (upper.startsWith(rule.match)) return rule.color
                "regex" -> if (rule.regex != null && upper.matches(rule.regex)) return rule.color
            }
        }
        return fallbackColor
    }

    fun getColorForLine(feature: Feature): String {
        return resolveColorHex(feature.properties.lineName)
    }

    fun getColorForLineStringAux(lineName: String): String {
        return resolveColorHex(lineName)
    }

    fun getColorForLineString(lineName: String): Int {
        val key = lineName.trim().uppercase()
        colorIntCache[key]?.let { return it }
        val color = hexToArgb(resolveColorHex(lineName))
        colorIntCache = colorIntCache + (key to color)
        return color
    }

    private fun hexToArgb(hex: String): Int {
        val color = hex.removePrefix("#")
        return when (color.length) {
            6 -> (0xFF shl 24) or
                (color.substring(0, 2).toInt(16) shl 16) or
                (color.substring(2, 4).toInt(16) shl 8) or
                color.substring(4, 6).toInt(16)
            8 -> (color.substring(0, 2).toInt(16) shl 24) or
                (color.substring(2, 4).toInt(16) shl 16) or
                (color.substring(4, 6).toInt(16) shl 8) or
                color.substring(6, 8).toInt(16)
            else -> throw IllegalArgumentException("Invalid hex color: $hex")
        }
    }
}
