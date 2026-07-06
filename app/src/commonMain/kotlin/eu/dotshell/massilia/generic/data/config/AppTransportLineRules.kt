package eu.dotshell.massilia.generic.data.config

import eu.dotshell.massilia.generic.data.models.ui.VehicleMarkerType
import eu.dotshell.massilia.generic.data.network.transport.TransportLineRules

class AppTransportLineRules(private val data: RulesData) : TransportLineRules {

    private val lineNamePatterns: List<Regex> = data.lineNameRegexes.map(::Regex)
    private val strongLinePatterns: List<Regex> = data.strongLineRegexes.map(::Regex)
    private val transportTypePatterns: List<Pair<TransportTypeData, Regex>> =
        data.transportTypes.map { it to Regex(it.regex) }
    private val strongLineSet: Set<String> =
        data.strongLines.mapTo(HashSet(data.strongLines.size)) { it.uppercase() }

    private val exactAliases: Map<String, LineAliasData> =
        data.aliases.filter { it.matchType.equals("exact", ignoreCase = true) }
            .associateBy { it.from.uppercase() }
    private val containsAliases: List<LineAliasData> =
        data.aliases.filter { it.matchType.equals("contains", ignoreCase = true) }
    private val displayAliasByCanonical: Map<String, String> =
        data.aliases.mapNotNull { alias ->
            alias.displayAs?.let { alias.to.uppercase() to it }
        }.toMap()
    private val excludedLinesSet: Set<String> =
        data.excludedLines.mapTo(HashSet(data.excludedLines.size)) { it.uppercase() }
    private val vehicleMarkerRules: List<VehicleMarkerRuleData> =
        data.vehicleMarkers.sortedByDescending { it.prefix.length }
    private val defaultVehicleMarker: VehicleMarkerType =
        parseMarker(data.defaultVehicleMarker)

    // Cache for isStrongLine: called once per line per stop during map rendering,
    // so potentially 10,000+ times per render for a full stop collection.
    // Bounded by the finite set of unique line names in the network.
    private val isStrongLineCache = HashMap<String, Boolean>()

    override fun normalizeAlertToken(raw: String): String {
        val token = raw.uppercase()
            .replace(" ", "")
            .replace("-", "")
            .replace("_", "")
            .replace("LIGNES", "")
            .replace("LIGNE", "")
            .replace("TRAM", "")
            .replace("METRO", "")
            .trim()

        return applyContainsAlias(token) ?: token
    }

    override fun isLikelyLineToken(token: String): Boolean {
        if (token.isBlank()) return false
        if (isStrongLine(token)) return true
        return lineNamePatterns.any { it.matches(token) }
    }

    override fun canonicalRouteName(raw: String): String {
        val token = raw.trim().uppercase()
        return exactAliases[token]?.to ?: token
    }

    override fun equivalentRouteNames(raw: String): List<String> {
        val token = raw.trim().uppercase()
        val alias = exactAliases[token]
        if (alias != null && alias.equivalents.isNotEmpty()) return alias.equivalents
        return listOf(token)
    }

    override fun normalizeForComparison(raw: String): String {
        val token = raw.trim().uppercase()
        return applyContainsAlias(token) ?: canonicalRouteName(token)
    }

    override fun isStrongLine(lineName: String): Boolean {
        isStrongLineCache[lineName]?.let { return it }
        val upperName = lineName.uppercase()
        val canonical = canonicalRouteName(lineName).uppercase()
        val uiName = normalizeLineNameForUi(lineName).uppercase()
        val result = when {
            upperName in excludedLinesSet || canonical in excludedLinesSet || uiName in excludedLinesSet -> false
            strongLineSet.contains(upperName) || strongLineSet.contains(canonical) || strongLineSet.contains(uiName) -> true
            else -> strongLinePatterns.any { it.matches(upperName) || it.matches(canonical) || it.matches(uiName) }
        }
        isStrongLineCache[lineName] = result
        return result
    }

    override fun getTransportType(lineName: String): String {
        val upperName = lineName.uppercase()
        val canonical = canonicalRouteName(lineName).uppercase()
        val uiName = normalizeLineNameForUi(lineName).uppercase()
        if (upperName in excludedLinesSet || canonical in excludedLinesSet || uiName in excludedLinesSet) return "Bus"
        for ((type, regex) in transportTypePatterns) {
            if (regex.matches(upperName) || regex.matches(canonical) || regex.matches(uiName)) return type.name
        }
        return "Bus" // Default
    }

    override fun getModeIcon(lineName: String): String? {
        if (isStrongLine(lineName)) return null
        val upperName = lineName.uppercase()
        val canonical = canonicalRouteName(lineName).uppercase()
        val uiName = normalizeLineNameForUi(lineName).uppercase()
        if (upperName in excludedLinesSet || canonical in excludedLinesSet || uiName in excludedLinesSet) return "mode_bus"
        for ((type, regex) in transportTypePatterns) {
            if (regex.matches(upperName) || regex.matches(canonical) || regex.matches(uiName)) return type.icon
        }
        return "mode_bus"
    }

    override fun isNavigoneLine(lineName: String): Boolean {
        val canonical = canonicalRouteName(lineName)
        val uiName = normalizeLineNameForUi(lineName)
        return getTransportType(lineName) in WATER_TRANSPORT_TYPES ||
               getTransportType(canonical) in WATER_TRANSPORT_TYPES ||
               getTransportType(uiName) in WATER_TRANSPORT_TYPES
    }

    override fun isLiveTrackableLine(lineName: String): Boolean {
        val upperName = lineName.uppercase()
        if (isStrongLine(lineName)) {
            // Tram lines are trackable despite being strong
            return upperName.startsWith("T") || canonicalRouteName(lineName).uppercase().startsWith("T")
        }
        return true
    }

    override fun getVehicleMarkerType(lineName: String): VehicleMarkerType {
        val upperName = lineName.uppercase()
        val canonical = canonicalRouteName(lineName).uppercase()
        val uiName = normalizeLineNameForUi(lineName).uppercase()
        if (upperName in excludedLinesSet || canonical in excludedLinesSet || uiName in excludedLinesSet) return defaultVehicleMarker
        val matched = vehicleMarkerRules.firstOrNull { 
            upperName.startsWith(it.prefix.uppercase()) ||
            canonical.startsWith(it.prefix.uppercase()) ||
            uiName.startsWith(it.prefix.uppercase())
        }
        return matched?.let { parseMarker(it.marker) } ?: defaultVehicleMarker
    }

    override fun normalizeLineNameForUi(lineName: String): String {
        val canonical = canonicalRouteName(lineName).uppercase()
        return displayAliasByCanonical[canonical] ?: lineName
    }

    override fun sortLines(lines: List<String>): List<String> {
        data class Key(
            val family: Int,
            val subFamily: String = "",
            val number: Int = Int.MAX_VALUE,
            val raw: String = ""
        )

        fun keyFor(lineRaw: String): Key {
            val up = lineRaw.trim().uppercase()

            // RTM display order: metro (M1, M2), then tram (T1-T3), then BHNS
            // (B1-B5), then navettes maritimes, then everything else.
            if (up.startsWith("M")) {
                val num = up.drop(1).toIntOrNull()
                if (num != null) return Key(1000, number = num, raw = up)
            }

            if (up.startsWith("T")) {
                val num = up.drop(1).toIntOrNull()
                if (num != null) return Key(2000, number = num, raw = up)
            }

            if (up.startsWith("B")) {
                val num = up.drop(1).toIntOrNull()
                if (num != null) return Key(3000, number = num, raw = up)
            }

            if (up == "FERRY") return Key(3500, number = 0, raw = up)
            if (up.startsWith("NAV")) {
                val num = up.drop(3).toIntOrNull()
                if (num != null) return Key(3500, number = num, raw = up)
            }

            val match = SORT_PREFIX_NUMBER_SUFFIX.matchEntire(up)
            if (match != null) {
                val prefix = match.groupValues[1]
                val num = match.groupValues[2].toIntOrNull() ?: Int.MAX_VALUE
                return Key(4000, subFamily = prefix, number = num, raw = up)
            }

            val pureNum = up.toIntOrNull()
            if (pureNum != null) {
                return Key(5000, number = pureNum, raw = up)
            }

            return Key(9000, subFamily = up, number = Int.MAX_VALUE, raw = up)
        }

        return lines
            .filter { it.uppercase() !in excludedLinesSet }
            .sortedWith(Comparator { a, b ->
                val ka = keyFor(a)
                val kb = keyFor(b)
                when {
                    ka.family != kb.family -> ka.family - kb.family
                    ka.subFamily != kb.subFamily -> ka.subFamily.compareTo(kb.subFamily)
                    ka.number != kb.number -> ka.number - kb.number
                    else -> ka.raw.compareTo(kb.raw)
                }
            })
    }

    private fun applyContainsAlias(token: String): String? {
        for (alias in containsAliases) {
            if (token.contains(alias.from.uppercase())) return alias.to
        }
        return null
    }

    private fun parseMarker(name: String): VehicleMarkerType = runCatching {
        VehicleMarkerType.valueOf(name.uppercase())
    }.getOrDefault(VehicleMarkerType.BUS)

    companion object {
        private val SORT_PREFIX_NUMBER_SUFFIX = Regex("^([A-Z]+)(\\d+)([A-Z]*)$")

        /** transportType names that count as water/ferry lines (Lyon "Navigone", RTM "Navette maritime"). */
        private val WATER_TRANSPORT_TYPES = setOf("Navigone", "Navette maritime")
    }
}
