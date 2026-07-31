package eu.dotshell.pelo.generic.data.models.stops

import androidx.compose.runtime.Immutable

/**
 * Station data for display in the bottom sheet
 *
 * @Immutable for the two list properties; all vals, built once by the caller.
 */
@Immutable
data class StationInfo(
    val nom: String,
    val lignes: List<String>, // List of line names (ex: ["A", "D", "F1"])
    val desserte: String = "", // Complete service string for reference
    val stopIds: List<Int> = emptyList()
)
