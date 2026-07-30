package eu.dotshell.pelo.generic.utils

import eu.dotshell.pelo.generic.data.models.geojson.Feature
import eu.dotshell.pelo.generic.service.TransportServiceProvider
import eu.dotshell.pelo.platform.provideLineColors
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
        // Prefer the color carried by the line data itself (the operator's own
        // palette bundled in lines.bin): "#RRGGBB" from the GTFS-based clients,
        // or the legacy WFS "R G B" decimal triplet in the Lyon properties.
        // The config lineColors rules remain the fallback for colorless data.
        normalizeDataColor(feature.properties.color)?.let { return it }
        return resolveColorHex(feature.properties.lineName)
    }

    // Operator colors indexed by line name, so a caller holding only a name (live vehicles) can
    // reach the same color the line itself is drawn in. Copy-on-write like colorIntCache.
    @Volatile
    private var dataColorByLine: Map<String, String> = emptyMap()

    /**
     * Records the operator's own color for each of [features], keyed by line name.
     *
     * Without this, anything identified only by name falls through to the config `lineColors`
     * rules, which are deliberately coarse — one prefix per mode. That is fine as a last resort
     * but wrong as the primary answer: it paints every bus the same red and every tram the same
     * purple, so a vehicle never matches the line drawn under it. Call this whenever a fuller set
     * of lines becomes available; entries accumulate and later data wins.
     */
    fun registerLineColors(features: List<Feature>) {
        if (features.isEmpty()) return
        val merged = HashMap<String, String>(dataColorByLine.size + features.size)
        merged.putAll(dataColorByLine)
        for (feature in features) {
            val color = normalizeDataColor(feature.properties.color) ?: continue
            for (key in lineKeys(feature.properties.lineName)) {
                merged[key] = color
            }
        }
        dataColorByLine = merged
    }

    /**
     * Color for a line known only by name — live vehicles, mainly. Returns the operator color
     * registered by [registerLineColors] when there is one, so the vehicle matches its line, and
     * only then falls back to the per-mode config rules.
     */
    fun getColorForLineName(lineName: String): String {
        for (key in lineKeys(lineName)) {
            dataColorByLine[key]?.let { return it }
        }
        return resolveColorHex(lineName)
    }

    /**
     * Lookup keys for a line name, most specific first. The realtime feed and the line dataset
     * do not always spell a line the same way (`TB11` vs the aliased form), so the canonical name
     * is indexed alongside the raw one and both are tried on lookup.
     */
    private fun lineKeys(lineName: String): List<String> {
        val raw = lineName.trim().uppercase()
        if (raw.isEmpty()) return emptyList()
        val canonical = runCatching {
            TransportServiceProvider.getTransportLineRules().normalizeForComparison(raw).uppercase()
        }.getOrDefault(raw)
        return if (canonical == raw) listOf(raw) else listOf(raw, canonical)
    }

    private fun normalizeDataColor(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        if (value.startsWith("#")) {
            return if (value.length == 7 || value.length == 9) value else null
        }
        val parts = value.split(' ', ',').filter { it.isNotBlank() }
        if (parts.size != 3) return null
        val channels = parts.map { it.toIntOrNull()?.takeIf { v -> v in 0..255 } ?: return null }
        return "#" + channels.joinToString("") { it.toString(16).padStart(2, '0').uppercase() }
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
