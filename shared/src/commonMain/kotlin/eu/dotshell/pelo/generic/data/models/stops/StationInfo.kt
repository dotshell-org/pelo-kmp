package eu.dotshell.pelo.generic.data.models.stops

/**
 * Station data for display in the bottom sheet
 */
data class StationInfo(
    val nom: String,
    val lignes: List<String>, // List of line names (ex: ["A", "D", "F1"])
    val desserte: String = "", // Complete service string for reference
    val stopIds: List<Int> = emptyList()
)
