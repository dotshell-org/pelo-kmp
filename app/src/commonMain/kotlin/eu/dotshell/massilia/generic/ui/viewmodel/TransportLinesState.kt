package eu.dotshell.massilia.generic.ui.viewmodel

import eu.dotshell.massilia.generic.data.models.geojson.FeatureCollection

/**
 * Etats pour les lignes de transport
 */
sealed class TransportLinesState {
    data object Loading : TransportLinesState()
    data class Success(val lines: FeatureCollection) : TransportLinesState()
    data class Error(val message: String) : TransportLinesState()
}
