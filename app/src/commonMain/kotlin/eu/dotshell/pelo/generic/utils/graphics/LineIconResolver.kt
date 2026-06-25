package eu.dotshell.pelo.generic.utils.graphics

import eu.dotshell.pelo.generic.data.models.geojson.StopFeature

/**
 * Multiplatform-safe line icon name resolution.
 * Maps line names to drawable file names (e.g., "212" -> "_212", "A" -> "a").
 * Also parses "desserte" strings from stop features.
 */
object LineIconResolver {

    // Simple bounded cache for parsed desserte strings
    private val desserteCache = HashMap<String, List<String>>()
    private const val CACHE_MAX_SIZE = 500

    /**
     * Converts a line name to a drawable name.
     * Lines composed only of digits are prefixed with an underscore.
     *
     * @param lineName The line name (ex: "212", "C17", "A", "NAVI1")
     * @return The corresponding drawable name (ex: "_212", "c17", "a", "navi1")
     */
    fun getDrawableNameForLineName(lineName: String): String {
        if (lineName.isBlank()) return ""
        return if (lineName.all { it.isDigit() }) {
            "_$lineName"
        } else {
            lineName.lowercase()
        }
    }

    /**
     * Returns all lines serving a stop (line names parsed from desserte).
     * Results are cached by desserte string to avoid repeated parsing.
     */
    fun getAllLinesForStop(stopFeature: StopFeature): List<String> {
        val desserte = stopFeature.properties.desserte
        desserteCache[desserte]?.let { return it }
        val result = parseDesserte(desserte)
        if (desserteCache.size >= CACHE_MAX_SIZE) {
            desserteCache.clear()
        }
        desserteCache[desserte] = result
        return result
    }

    fun clearCache() {
        desserteCache.clear()
    }

    /**
     * Parses the desserte string to extract the list of lines.
     * Handled cases:
     *  - "5:A,86:A,JD844:R" -> ["5", "86", "JD844"] (buses with directions)
     *  - "A:A,D:A" -> ["A", "D"] (metros A and D, :A = outbound direction)
     *  - "F1:A,F2:A" -> ["F1", "F2"] (funiculars)
     *  - "M:A:B" -> ["A"] (mode prefix M is stripped, A is the actual line)
     *  - "T:T1:A" -> ["T1"] (tram T1, mode T stripped)
     *  - "C17:22:31" (old format) -> ["C17"]
     *
     * IMPORTANT: Don't confuse ":A" (outbound direction) with metro line A
     */
    private fun parseSingleDesserteEntry(entry: String): List<String> {
        val tokens = entry.split(":")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()

        return if (tokens.size == 2) {
            val first = tokens[0]
            val second = tokens[1]
            if (second.uppercase() == "A" || second.uppercase() == "R") {
                listOf(first)
            } else {
                tokens
            }
        } else if (tokens.size > 2) {
            // WFS format can be "M:A:B" (mode:line:direction) where the
            // first token is a mode prefix (M=Métro, T=Tram, F=Funiculaire)
            // and the actual line name is the SECOND token.
            // Older format "C17:22:31" also hits this branch (line:extra:extra)
            // where extra tokens are direction/terminus, not lines.
            val modePrefixes = setOf("M", "T", "F", "TB", "NAV")
            val lineName = if (tokens.first().uppercase() in modePrefixes) {
                tokens.getOrNull(1) ?: tokens.first()
            } else {
                tokens.first()
            }
            listOf(lineName)
        } else {
            tokens
        }
    }

    fun parseDesserte(desserte: String): List<String> {
        if (desserte.isBlank()) return emptyList()

        val entries = desserte.split(",")
        val rawLines = entries.flatMap { entry ->
            parseSingleDesserteEntry(entry)
        }

        val seen = HashSet<String>()
        val unique = ArrayList<String>(rawLines.size)
        rawLines.forEach { line ->
            val key = line.uppercase()
            if (seen.add(key)) {
                unique.add(line)
            }
        }
        return unique
    }
}
