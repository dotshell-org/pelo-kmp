package com.pelotcl.app.generic.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.pelotcl.app.generic.ui.viewmodel.TransportViewModel.StopDeparturePreview
import kotlinx.coroutines.flow.map
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pelotcl.app.R
import com.pelotcl.app.generic.data.models.stops.StationInfo
import com.pelotcl.app.generic.ui.theme.Gray200
import com.pelotcl.app.generic.ui.theme.Gray700
import com.pelotcl.app.generic.ui.theme.PrimaryColor
import com.pelotcl.app.generic.ui.theme.SecondaryColor
import com.pelotcl.app.generic.ui.viewmodel.TransportViewModel
import com.pelotcl.app.generic.utils.schedule.DepartureManager
import com.pelotcl.app.generic.service.TransportServiceProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationBottomSheet(
    stationInfo: StationInfo?,
    sheetState: SheetState?,
    onDismiss: () -> Unit,
    viewModel: TransportViewModel? = null,
    onLineClick: (String) -> Unit = {},
    onDepartureClick: (lineName: String, directionId: Int, departureTime: String) -> Unit = { lineName, _, _ ->
        onLineClick(lineName)
    },
    isFavoriteStop: Boolean = false,
    onToggleFavoriteStop: () -> Unit = {},
    onAddFavoriteClick: (String) -> Unit = {},
    onItineraryClick: () -> Unit = {},
    onReportAlertClick: (String, List<String>) -> Unit = { _, _ -> }
) {
    val titleInset = 20.dp
    val departuresInset = 20.dp
    val actionsInset = 8.dp

    androidx.compose.runtime.LaunchedEffect(stationInfo?.nom) {
        val stop = stationInfo ?: return@LaunchedEffect
        com.pelotcl.app.generic.data.telemetry.TelemetryEmitter.emit(
            com.pelotcl.app.generic.data.telemetry.TelemetryEvent.StopClicked(
                eventId = java.util.UUID.randomUUID().toString(),
                at = java.time.Instant.now().toString(),
                stopId = stop.nom,
                context = "bottom_sheet"
            )
        )
    }

    if (stationInfo != null) {
        val allStopLines by produceState(
            initialValue = stationInfo.lignes,
            key1 = stationInfo.nom,
            key2 = stationInfo.lignes,
            key3 = viewModel
        ) {
            if (viewModel == null) {
                value = stationInfo.lignes
            } else {
                viewModel.getConnectionsForStop(stationInfo.nom, "")
                    .map { connections ->
                        (connections.map { it.lineName } + stationInfo.lignes)
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .distinctBy { it.uppercase() }
                    }.collect { value = it }
            }
        }

        val departuresState = produceState<List<StopDeparturePreview>?>(
            initialValue = null,
            key1 = stationInfo.nom,
            key2 = allStopLines,
            key3 = viewModel
        ) {
            value = viewModel?.getNextDeparturesForStop(
                stopName = stationInfo.nom,
                lines = allStopLines
            )
                ?: emptyList()
        }

        val departures = departuresState.value

        val content = @Composable {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                // Nom de la station
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = titleInset),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stationInfo.nom,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryColor,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Spacer(modifier = Modifier.size(actionsInset))

                    Button(
                        onClick = onItineraryClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryColor,
                            contentColor = SecondaryColor
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Directions,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(text = "Itinéraire", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            onReportAlertClick(stationInfo.nom, stationInfo.lignes)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFavoriteStop) Color(0xFFD1D5DB) else Color(
                                0xFFE5E7EB
                            ),
                            contentColor = Color(0xFF374151)
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.add_triangle_24px),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Signaler une alerte",
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = { onAddFavoriteClick(stationInfo.nom) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFavoriteStop) Color(0xFFD1D5DB) else Color(
                                0xFFE5E7EB
                            ),
                            contentColor = Color(0xFF374151)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FavoriteBorder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Ajouter aux favoris",
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.size(actionsInset))
                }

                // Virtualized list of departures (sorted)
                val lineOrder = remember(allStopLines) {
                    TransportServiceProvider.getTransportLineRules().sortLines(allStopLines)
                        .mapIndexed { index, line -> line.uppercase() to index }
                        .toMap()
                }

                val sortedDepartures = remember(departures, lineOrder) {
                    departures?.sortedWith(
                        compareBy<StopDeparturePreview> {
                            DepartureManager().minutesUntilDeparture(it.nextDeparture)
                        }
                            .thenBy { lineOrder[it.lineName.uppercase()] ?: Int.MAX_VALUE }
                            .thenBy { it.directionId }
                            .thenBy { DepartureManager().parseDepartureToMinutes(it.nextDeparture) ?: Int.MAX_VALUE }
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = departuresInset)
                ) {
                    when {
                        departures == null -> {
                            item(key = "loading") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(color = PrimaryColor)
                                }
                            }
                        }
                        departures.isEmpty() -> {
                            item(key = "empty") {
                                Text(
                                    text = "Aucun horaire disponible pour cet arrêt",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Gray700,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        }
                        else -> {
                            itemsIndexed(
                                sortedDepartures!!,
                                key = { _, dep -> "${dep.lineName}-${dep.directionId}-${dep.nextDeparture}" }
                            ) { index, departure ->
                                DepartureListItem(
                                    lineName = departure.lineName,
                                    directionName = departure.directionName,
                                    departureTime = departure.nextDeparture,
                                    onClick = {
                                        onDepartureClick(
                                            departure.lineName,
                                            departure.directionId,
                                            departure.nextDeparture
                                        )
                                    }
                                )

                                if (index < sortedDepartures.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        color = Gray200
                                    )
                                }
                            }

                            item(key = "bottom_spacer") {
                                Spacer(modifier = Modifier.height(20.dp))
                            }
                        }
                    }
                }
            }
        }

        // If sheetState is provided, wrap in ModalBottomSheet, otherwise show content directly
        if (sheetState != null) {
            ModalBottomSheet(
                onDismissRequest = onDismiss,
                sheetState = sheetState,
                containerColor = SecondaryColor
            ) {
                content()
            }
        } else {
            content()
        }
    }
}
