package eu.dotshell.massilia.generic.ui.screens.plan

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import eu.dotshell.massilia.generic.data.models.stops.StationInfo
import eu.dotshell.massilia.generic.ui.components.StationBottomSheet
import eu.dotshell.massilia.generic.ui.viewmodel.TransportViewModelInterface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationSheetContent(
    stationInfo: StationInfo,
    viewModel: TransportViewModelInterface,
    onDismiss: () -> Unit,
    onDepartureClick: (lineName: String, directionId: Int, departureTime: String) -> Unit,
    isFavoriteStop: Boolean = false,
    onToggleFavoriteStop: () -> Unit = {},
    onAddFavoriteClick: (String) -> Unit = {},
    onItineraryClick: (String) -> Unit = {},
    onReportAlertClick: (String, List<String>) -> Unit = { _, _ -> }
) {
    StationBottomSheet(
        stationInfo = stationInfo,
        sheetState = null,
        onDismiss = onDismiss,
        viewModel = viewModel,
        onDepartureClick = onDepartureClick,
        isFavoriteStop = isFavoriteStop,
        onToggleFavoriteStop = onToggleFavoriteStop,
        onAddFavoriteClick = onAddFavoriteClick,
        onItineraryClick = { onItineraryClick(stationInfo.nom) },
        onReportAlertClick = onReportAlertClick
    )
}