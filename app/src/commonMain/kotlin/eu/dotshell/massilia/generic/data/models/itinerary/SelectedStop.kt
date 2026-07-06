package eu.dotshell.massilia.generic.data.models.itinerary

import androidx.compose.runtime.Immutable

/**
 * Represents a selected stop for the itinerary
 */
@Immutable
data class SelectedStop(
    val name: String,
    val stopIds: List<Int>
)
