package eu.dotshell.pelo.generic.data.models.itinerary

import androidx.compose.runtime.Immutable

/**
 * Represents a selected itinerary endpoint: either a stop (name + platform ids) or an
 * arbitrary coordinate (geocoded address/POI or GPS position) when [lat]/[lon] are set.
 */
@Immutable
data class SelectedStop(
    val name: String,
    val stopIds: List<Int>,
    val lat: Double? = null,
    val lon: Double? = null
) {
    val isCoordinate: Boolean get() = lat != null && lon != null
}
