package eu.dotshell.pelo.generic.data.models.ui

import androidx.compose.runtime.Immutable

/** @Immutable for the list and map properties; all vals, built once by the caller. */
@Immutable
data class AllSchedulesInfo(
    val lineName: String,
    val directionName: String,
    val schedules: List<String>,
    val availableDirections: List<Int> = emptyList(),
    val headsigns: Map<Int, String> = emptyMap()
)
