package com.pelotcl.app.generic.ui.screens.plan

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pelotcl.app.generic.data.models.search.LineSearchResult
import com.pelotcl.app.generic.data.models.search.StationSearchResult
import com.pelotcl.app.generic.ui.components.search.TransportSearchBar
import com.pelotcl.app.generic.data.models.search.TransportSearchContent
import com.pelotcl.app.generic.ui.theme.Gray800
import com.pelotcl.app.generic.ui.theme.Gray900
import com.pelotcl.app.generic.ui.theme.Red500
import com.pelotcl.app.generic.ui.theme.Red600
import com.pelotcl.app.generic.ui.theme.Red700
import com.pelotcl.app.generic.ui.theme.Red800
import com.pelotcl.app.generic.ui.theme.Red900
import com.pelotcl.app.generic.ui.theme.Red950
import com.pelotcl.app.generic.ui.theme.SecondaryColor
import com.pelotcl.app.generic.ui.viewmodel.TransportViewModel
import com.pelotcl.app.generic.utils.graphics.BusIconHelper
import com.pelotcl.app.generic.utils.LineColorHelper
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
    viewModel: TransportViewModel,
    onDismiss: () -> Unit,
    initialStop: StationSearchResult? = null,
    nearestStopCandidate: StationSearchResult? = null
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var selectedStop by remember { mutableStateOf<StationSearchResult?>(initialStop) }
    var selectedLine by remember { mutableStateOf<LineSearchResult?>(null) }
    var showSearchFullscreen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    val allAlertTypes = AlertType.entries
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
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
                    text = "Signaler une alerte",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.Black,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(bottom = 16.dp, start = 8.dp)
                )

                Text(
                    text = "Commencez par rechercher un arrêt ou une ligne.",
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 16.dp, start = 8.dp)
                )

                // Search entry point
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(Color.Black)
                        .clickable {
                            android.util.Log.i("AlertReportBS", "Opening search. Query reset.")
                            searchQuery = "" // Reset query when opening
                            showSearchFullscreen = true
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color.Red,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Rechercher",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(Color(0xFFE5E7EB))
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
                        tint = Color.Black,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Arrêt le plus proche",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Black,
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
                        contentDescription = "Retour",
                        tint = Color.Black,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable {
                                selectedStop = null
                                selectedLine = null
                            }
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    if (selectedLine != null) {
                        val lineName = selectedLine!!.lineName
                        val iconRes = BusIconHelper.getResourceIdForLine(context, lineName)
                        val fallbackColor = Color(LineColorHelper.getColorForLineString(lineName))

                        if (iconRes != 0) {
                            Image(
                                painter = painterResource(id = iconRes),
                                contentDescription = "Ligne $lineName",
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
                                    color = SecondaryColor,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else if (selectedStop != null) {
                        Text(
                            text = selectedStop!!.stopName,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                val filteredAlertTypes = allAlertTypes.filter { alertType ->
                    (selectedStop != null && alertType.isStop) || (selectedLine != null && alertType.isLine)
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(filteredAlertTypes) { alertType ->
                        AlertButton(
                            alertType = alertType,
                            enabled = true,
                            onClick = {
                                android.util.Log.i("AlertReportBS", "Alert clicked: ${alertType.id}")
                                scope.launch {
                                    val result = submitUserAlert(
                                        alertTypeId = alertType.id,
                                        stopName = selectedStop?.stopName,
                                        stopIdFallback = selectedStop?.stopId,
                                        lineId = selectedLine?.lineName
                                    )
                                    if (result.isSuccess) {
                                        Toast.makeText(
                                            context,
                                            "Alerte envoyée avec succès",
                                            Toast.LENGTH_SHORT
                                        ).show()
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
                android.util.Log.i("AlertReportBS", "Search dialog dismissed via onDismissRequest")
                showSearchFullscreen = false 
            },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                TransportSearchBar(
                    viewModel = viewModel,
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
                        android.util.Log.i("AlertReportBS", "Search expanded change: $expanded")
                        if (!expanded) showSearchFullscreen = false
                    },
                    onStopPrimary = { result ->
                        android.util.Log.i("AlertReportBS", "Stop selected: ${result.stopName}, id=${result.stopId}")
                        selectedStop = result
                        selectedLine = null
                        showSearchFullscreen = false
                    },
                    onLineSelected = { result ->
                        android.util.Log.i("AlertReportBS", "Line selected: ${result.lineName}")
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
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
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
            text = alertType.label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Black,
            textAlign = TextAlign.Center,
            softWrap = false,
            overflow = TextOverflow.Visible
        )
    }
}
