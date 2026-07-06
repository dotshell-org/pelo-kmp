package eu.dotshell.massilia.generic.ui.components

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
import eu.dotshell.massilia.generic.ui.theme.bottomSheetContainerColor
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import eu.dotshell.massilia.generic.ui.viewmodel.StopDeparturePreview
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.dotshell.massilia.generic.data.models.stops.StationInfo
import eu.dotshell.massilia.generic.data.telemetry.TelemetryEvent
import eu.dotshell.massilia.generic.ui.viewmodel.TransportViewModelInterface
import eu.dotshell.massilia.generic.utils.schedule.DepartureManager
import eu.dotshell.massilia.platform.DrawableProvider
import eu.dotshell.massilia.platform.LocalPlatformContext
import eu.dotshell.massilia.platform.StringProvider
import eu.dotshell.massilia.generic.data.telemetry.emitTelemetryEvent
import eu.dotshell.massilia.platform.provideTransportLineRules
import eu.dotshell.massilia.platform.randomId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationBottomSheet(
    stationInfo: StationInfo?,
    sheetState: SheetState?,
    onDismiss: () -> Unit,
    viewModel: TransportViewModelInterface? = null,
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

    val drawableProvider = DrawableProvider(LocalPlatformContext.current)
    val strings = StringProvider(LocalPlatformContext.current)
    val realtimeConfig = remember { eu.dotshell.massilia.generic.service.TransportServiceProvider.getRealtimeConfig() }
    val stationName = stationInfo?.nom

    androidx.compose.runtime.LaunchedEffect(stationName) {
        val stop = stationInfo ?: return@LaunchedEffect
        emitTelemetryEvent(
            TelemetryEvent.StopClicked(
                eventId = randomId(),
                at = Clock.System.now().toString(),
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
                        color = MaterialTheme.colorScheme.onSurface,
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
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Directions,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(text = strings["itinerary"], fontWeight = FontWeight.Bold)
                    }

                    if (realtimeConfig.userStopAlertsEnabled) {
                        Button(
                            onClick = {
                                onReportAlertClick(stationInfo.nom, stationInfo.lignes)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(
                                painter = drawableProvider.getPainter("add_triangle_24px"),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = strings["alert_report_title"],
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Button(
                        onClick = { onAddFavoriteClick(stationInfo.nom) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FavoriteBorder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = strings["add_to_favorites"],
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.size(actionsInset))
                }

                // Virtualized list of departures (sorted)
                val lineRules = provideTransportLineRules()
                val lineOrder = remember(allStopLines) {
                    lineRules.sortLines(allStopLines)
                        .mapIndexed { index, line -> line.uppercase() to index }
                        .toMap()
                }

                val sortedDepartures = remember(departures, lineOrder) {
                    departures?.sortedWith(
                        compareBy<StopDeparturePreview> {
                            DepartureManager.minutesUntilDeparture(it.nextDeparture)
                        }
                            .thenBy { lineOrder[it.lineName.uppercase()] ?: Int.MAX_VALUE }
                            .thenBy { it.directionId }
                            .thenBy { DepartureManager.parseDepartureToMinutes(it.nextDeparture) ?: Int.MAX_VALUE }
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
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        departures.isEmpty() -> {
                            item(key = "empty") {
                                Text(
                                    text = strings["no_schedule_for_stop"],
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        }
                        else -> {
                            val deps = sortedDepartures.orEmpty()
                            itemsIndexed(
                                deps,
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

                                if (index < deps.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant
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
                containerColor = bottomSheetContainerColor(),
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                content()
            }
        } else {
            content()
        }
    }
}
