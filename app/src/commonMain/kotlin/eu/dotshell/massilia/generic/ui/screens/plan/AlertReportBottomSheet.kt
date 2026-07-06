package eu.dotshell.massilia.generic.ui.screens.plan

import kotlinx.datetime.Clock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Elevator
import androidx.compose.material.icons.filled.EmojiPeople
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import eu.dotshell.massilia.generic.ui.theme.bottomSheetContainerColor
import eu.dotshell.massilia.generic.ui.theme.searchFieldContainerColor
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.dotshell.massilia.generic.data.models.search.LineSearchResult
import eu.dotshell.massilia.generic.data.models.search.StationSearchResult
import eu.dotshell.massilia.generic.ui.components.search.TransportSearchBar
import eu.dotshell.massilia.generic.data.models.search.TransportSearchContent
import eu.dotshell.massilia.generic.ui.theme.Gray800
import eu.dotshell.massilia.generic.ui.theme.Gray900
import eu.dotshell.massilia.generic.ui.theme.Red500
import eu.dotshell.massilia.generic.ui.theme.Red600
import eu.dotshell.massilia.generic.ui.theme.Red700
import eu.dotshell.massilia.generic.ui.theme.Red800
import eu.dotshell.massilia.generic.ui.theme.Red900
import eu.dotshell.massilia.generic.ui.theme.Red950
import eu.dotshell.massilia.generic.ui.viewmodel.TransportViewModelInterface
import eu.dotshell.massilia.generic.utils.graphics.LineIconResolver
import eu.dotshell.massilia.generic.utils.LineColorHelper
import eu.dotshell.massilia.platform.DrawableProvider
import eu.dotshell.massilia.platform.LocalPlatformContext
import eu.dotshell.massilia.platform.StringProvider
import eu.dotshell.massilia.platform.Log
import eu.dotshell.massilia.platform.randomId
import eu.dotshell.massilia.platform.showToast
import kotlinx.coroutines.launch

enum class AlertType(val id: String, val label: String, val icon: ImageVector, val color: Color, val isStop: Boolean, val isLine: Boolean) {
    // STOP_ALERT_TYPES=closure,delay,elevator,crowding,works,strike,fire
    // LINE_ALERT_TYPES=interruption,congestion,works,strike
    
    CLOSURE("closure", "Arrêt Fermé", Icons.Default.Block, Red500, isStop = true, isLine = false),
    DELAY("delay", "Retard", Icons.Default.Schedule, Red600, isStop = true, isLine = false),
    ELEVATOR("elevator", "Ascenseur HS", Icons.Default.Elevator, Red700, isStop = true, isLine = false),
    CROWDING("crowding", "Forte Foule", Icons.Default.Groups, Red800, isStop = true, isLine = false),
    WORKS("works", "Travaux", Icons.Default.Engineering, Red900, isStop = true, isLine = true),
    STRIKE("strike", "Grève", Icons.Default.EmojiPeople, Red950, isStop = true, isLine = true),
    FIRE("fire", "Incendie", Icons.Default.Whatshot, Color.Black, isStop = true, isLine = false),
    INTERRUPTION("interruption", "Interruption", Icons.Default.Pause, Gray900, isStop = false, isLine = true),
    CONGESTION("congestion", "Traffic Elevé", Icons.AutoMirrored.Filled.TrendingUp, Gray800, isStop = false, isLine = true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertReportBottomSheet(
    viewModel: TransportViewModelInterface,
    onDismiss: () -> Unit,
    initialStop: StationSearchResult? = null,
    nearestStopCandidate: StationSearchResult? = null
) {
    val scope = rememberCoroutineScope()
    val context = LocalPlatformContext.current
    val strings = StringProvider(context)
    var selectedStop by remember { mutableStateOf<StationSearchResult?>(initialStop) }
    var selectedLine by remember { mutableStateOf<LineSearchResult?>(null) }
    var showSearchFullscreen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    val allAlertTypes = AlertType.entries
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = bottomSheetContainerColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            if (selectedStop == null && selectedLine == null) {
                // PAGE 1: Initial view
                Text(
                    text = strings["alert_report_title"],
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(bottom = 16.dp, start = 8.dp)
                )

                Text(
                    text = strings["alert_report_search_hint"],
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp, start = 8.dp)
                )

                // Search entry point
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(searchFieldContainerColor())
                        .clickable {
                            Log.i("AlertReportBS", "Opening search. Query reset.")
                            searchQuery = "" // Reset query when opening
                            showSearchFullscreen = true
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = strings["search"],
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable(enabled = nearestStopCandidate != null) {
                            nearestStopCandidate?.let {
                                selectedStop = it
                                selectedLine = null
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = strings["nearest_stop"],
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // PAGE 2: Selection view with icons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = strings["back"],
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable {
                                selectedStop = null
                                selectedLine = null
                            }
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    val selectedLineLocal = selectedLine
                    val selectedStopLocal = selectedStop
                    if (selectedLineLocal != null) {
                        val lineName = selectedLineLocal.lineName
                        val drawableProvider = DrawableProvider(context)
                        val drawableName = remember(lineName) {
                            LineIconResolver.getDrawableNameForLineName(lineName)
                        }
                        val hasIcon = remember(drawableName, drawableProvider) {
                            drawableProvider.hasDrawable(drawableName)
                        }
                        val fallbackColor = Color(LineColorHelper.getColorForLineString(lineName))

                        if (hasIcon) {
                            Image(
                                painter = drawableProvider.getPainter(drawableName),
                                contentDescription = strings["line_label"].replace("%s", lineName),
                                modifier = Modifier.size(44.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(fallbackColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = lineName.ifBlank { "?" }.take(3),
                                    // Contrast on the fixed line-color badge — not theme-driven.
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else if (selectedStopLocal != null) {
                        Text(
                            text = selectedStopLocal.stopName,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                val filteredAlertTypes = allAlertTypes.filter { alertType ->
                    (selectedStop != null && alertType.isStop) || (selectedLine != null && alertType.isLine)
                }

                val itemWidth = 88.dp
                androidx.compose.foundation.layout.BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                ) {
                    val availableWidth = maxWidth
                    
                    // Nombre d'items par ligne (minimum 4)
                    val itemsPerRow = (availableWidth / itemWidth).toInt().coerceAtLeast(4)
                    
                    // Si itemsPerRow est 4 mais que ça ne rentre pas avec itemWidth=88dp,
                    // on réduit dynamiquement la taille de l'item pour que ça rentre.
                    val actualItemWidth = if (availableWidth < itemWidth * itemsPerRow) {
                        availableWidth / itemsPerRow
                    } else {
                        itemWidth
                    }

                    val gap = if (itemsPerRow > 1) {
                        (availableWidth - (actualItemWidth * itemsPerRow)) / (itemsPerRow - 1)
                    } else {
                        0.dp
                    }

                    // On limite à 2 lignes maximum
                    val maxItems = itemsPerRow * 2
                    val displayedAlertTypes = filteredAlertTypes.take(maxItems)
                    val rows = displayedAlertTypes.chunked(itemsPerRow)

                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        rows.forEachIndexed { rowIndex, rowItems ->
                            val isLastRow = rowIndex == rows.lastIndex
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isLastRow || itemsPerRow == 1) {
                                    Arrangement.spacedBy(gap)
                                } else {
                                    Arrangement.SpaceBetween
                                }
                            ) {
                                rowItems.forEach { alertType ->
                                    Box(modifier = Modifier.width(actualItemWidth)) {
                                        AlertButton(
                                            alertType = alertType,
                                            enabled = true,
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                            onClick = {
                                                Log.i("AlertReportBS", "Alert clicked: ${alertType.id}")
                                                scope.launch {
                                                    val result = submitUserAlert(
                                                        alertTypeId = alertType.id,
                                                        stopName = selectedStop?.stopName,
                                                        stopIdFallback = selectedStop?.stopId,
                                                        lineId = selectedLine?.lineName
                                                    )
                                                    if (result.isSuccess) {
                                                        // Telemetry: record only on success so we don't pollute
                                                        // the dataset with failed submissions.
                                                        eu.dotshell.massilia.generic.data.telemetry.TelemetryEmitter.emit(
                                                            eu.dotshell.massilia.generic.data.telemetry.TelemetryEvent.AlertSubmitted(
                                                                eventId = randomId(),
                                                                at = Clock.System.now().toString(),
                                                                kind = alertType.id,
                                                                stopId = selectedStop?.stopName,
                                                                lineId = selectedLine?.lineName
                                                            )
                                                        )
                                                        showToast(context, "Alerte envoyée avec succès")
                                                        onDismiss()
                                                    } else {
                                                        errorMessage = result.errorMessage
                                                            ?: "Une erreur est survenue. Veuillez réessayer."
                                                        showErrorDialog = true
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Error dialog
    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text("Erreur d\'envoi") },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(
                    onClick = { showErrorDialog = false }
                ) {
                    Text("OK")
                }
            }
        )
    }

    if (showSearchFullscreen) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { 
                Log.i("AlertReportBS", "Search dialog dismissed via onDismissRequest")
                showSearchFullscreen = false 
            },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                TransportSearchBar(
                    onSearchStops = { query -> viewModel.searchStops(query) },
                    onSearchLines = { query -> viewModel.searchLines(query) },
                    modifier = Modifier.fillMaxSize(),
                    content = TransportSearchContent.STOPS_AND_LINES,
                    showHistory = false,
                    startExpanded = true,
                    showDarkOutline = false,
                    searchPlaceholder = "Ligne ou arrêt concerné",
                    query = searchQuery,
                    onQueryChange = { q ->
                        searchQuery = q
                    },
                    onExpandedChange = { expanded ->
                        Log.i("AlertReportBS", "Search expanded change: $expanded")
                        if (!expanded) showSearchFullscreen = false
                    },
                    onStopPrimary = { result ->
                        Log.i("AlertReportBS", "Stop selected: ${result.stopName}, id=${result.stopId}")
                        selectedStop = result
                        selectedLine = null
                        showSearchFullscreen = false
                    },
                    onLineSelected = { result ->
                        Log.i("AlertReportBS", "Line selected: ${result.lineName}")
                        selectedLine = result
                        selectedStop = null
                        showSearchFullscreen = false
                    },
                    showDirections = false
                )
            }
        }
    }
}

@Composable
fun AlertButton(
    alertType: AlertType,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val strings = StringProvider(LocalPlatformContext.current)
    val localizedLabel = when (alertType.id) {
        "closure" -> strings["alert_type_closure"]
        "delay" -> strings["alert_type_delay"]
        "elevator" -> strings["alert_type_elevator"]
        "crowding" -> strings["alert_type_crowding"]
        "works" -> strings["alert_type_works"]
        "strike" -> strings["alert_type_strike"]
        "fire" -> strings["alert_type_fire"]
        "interruption" -> strings["alert_type_interruption"]
        "congestion" -> strings["alert_type_congestion"]
        else -> alertType.label
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(alertType.color),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = alertType.icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = Color.White
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = localizedLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            softWrap = false,
            overflow = TextOverflow.Visible
        )
    }
}
