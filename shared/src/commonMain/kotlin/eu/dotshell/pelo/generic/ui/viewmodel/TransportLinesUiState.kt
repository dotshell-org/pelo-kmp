package eu.dotshell.pelo.generic.ui.viewmodel

import eu.dotshell.pelo.generic.data.models.geojson.Feature

/**
 * Etats pour les lignes de transport (Compatibilite PlanScreen)
 */
sealed class TransportLinesUiState {
    data object Loading : TransportLinesUiState()
    data class Success(val lines: List<Feature>) : TransportLinesUiState()
    data class PartialSuccess(val lines: List<Feature>) : TransportLinesUiState()
    data class Error(val message: String) : TransportLinesUiState()
}
