package eu.dotshell.pelo.generic.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import eu.dotshell.pelo.platform.LocalPlatformContext
import eu.dotshell.pelo.platform.StringProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.dotshell.pelo.generic.data.telemetry.DailyReportState
import eu.dotshell.pelo.generic.data.telemetry.TelemetryEvent
import eu.dotshell.pelo.generic.data.telemetry.PlaceRef
import androidx.compose.material3.MaterialTheme
import kotlinx.datetime.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelemetrySettingsScreen(
    snapshot: DailyReportState?,
    onBackClick: () -> Unit,
    onWipeHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = StringProvider(LocalPlatformContext.current)
    var showWipeConfirmDialog by remember { mutableStateOf(false) }

    if (showWipeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showWipeConfirmDialog = false },
            title = { Text("Supprimer l'historique ?") },
            text = { Text("Voulez-vous vraiment supprimer tout votre historique local (favoris et trajets) ? Cette action est irréversible.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onWipeHistory()
                        showWipeConfirmDialog = false
                    }
                ) {
                    Text("Oui", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirmDialog = false }) {
                    Text("Non", color = MaterialTheme.colorScheme.onSurface)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = strings["privacy_title"],
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings["back"],
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Compact delete history button
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = { showWipeConfirmDialog = true }),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = strings["delete_local_history"],
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = strings["data_collected_today"],
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )

            Spacer(Modifier.height(12.dp))

            when (snapshot) {
                null -> InfoCard(
                    title = strings["no_data"],
                    body = "Aucun événement n'a encore été enregistré."
                )
                else -> {
                    // Compact Side-by-Side Metadata Cards
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(text = snapshot.day, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(text = strings["events_count"].replace("%s", snapshot.events.size.toString()), color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    EventBreakdownCard(events = snapshot.events)
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = strings["events_log"],
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    val sortedEvents = remember(snapshot.events) { snapshot.events.reversed() }
                    sortedEvents.forEach { event ->
                        EventItem(event = event)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EventItem(event: TelemetryEvent) {
    val strings = StringProvider(LocalPlatformContext.current)
    val (icon, color) = eventIconAndColor(event::class.simpleName ?: "Unknown")
    val title = eventDescriptionLabel(event, strings)
    val time = remember(event.at) {
        try {
            event.at.substringAfter('T').substringBefore('.')
        } catch (_: Exception) {
            ""
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = strings["event_type"].replace("%s", event::class.simpleName ?: ""),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                Text(
                    text = time,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            val details = getEventDetails(event, strings)
            if (details.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 32.dp)
                ) {
                    details.forEach { (key, value) ->
                        Row(
                            modifier = Modifier.padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "$key :",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = value,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventBreakdownCard(events: List<TelemetryEvent>) {
    if (events.isEmpty()) return

    val strings = StringProvider(LocalPlatformContext.current)
    val grouped = events.groupBy { it::class.simpleName ?: "Unknown" }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(strings["telemetry_daily_summary"], color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            grouped.forEach { (className, list) ->
                val label = eventShortLabel(className, strings)
                val (icon, color) = eventIconAndColor(className)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("${list.size}", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

private fun eventIconAndColor(simpleName: String): Pair<ImageVector, Color> = when (simpleName) {
    "SessionOpened", "SessionClosed" -> Icons.Default.AccountCircle to Color(0xFF10B981)
    "SearchStop", "StopClicked" -> Icons.Default.Place to Color(0xFFEF4444)
    "SearchLine", "LineClicked" -> Icons.Default.Map to Color(0xFF3B82F6)
    "SearchItinerary", "ItineraryCalculated", "ItineraryChosen" -> Icons.Default.Directions to Color(0xFFF59E0B)
    "AlertSubmitted", "AlertRead" -> Icons.Default.Warning to Color(0xFFF59E0B)
    else -> Icons.Default.Notifications to Color(0xFF9CA3AF)
}

@Composable
private fun eventShortLabel(simpleName: String, strings: StringProvider): String = when (simpleName) {
    "SessionOpened", "SessionClosed" -> strings["telemetry_category_sessions"]
    "SearchStop", "StopClicked" -> strings["telemetry_category_stops"]
    "SearchLine", "LineClicked" -> strings["telemetry_category_lines"]
    "SearchItinerary", "ItineraryCalculated", "ItineraryChosen" -> strings["telemetry_category_itineraries"]
    "AlertSubmitted", "AlertRead" -> strings["telemetry_category_alerts"]
    else -> simpleName
}

@Composable
private fun eventDescriptionLabel(event: TelemetryEvent, strings: StringProvider): String = when (event) {
    is TelemetryEvent.SessionOpened -> strings["telemetry_event_session_opened"]
    is TelemetryEvent.SessionClosed -> strings["telemetry_event_session_closed"]
    is TelemetryEvent.SearchStop -> strings["telemetry_event_search_stop"]
    is TelemetryEvent.SearchLine -> strings["telemetry_event_search_line"]
    is TelemetryEvent.SearchItinerary -> strings["telemetry_event_search_itinerary"]
    is TelemetryEvent.ItineraryCalculated -> strings["telemetry_event_itinerary_calculated"]
    is TelemetryEvent.ItineraryChosen -> strings["telemetry_event_itinerary_chosen"]
    is TelemetryEvent.TripCompleted -> strings["telemetry_event_trip_completed"]
    is TelemetryEvent.LineClicked -> strings["telemetry_event_line_clicked"]
    is TelemetryEvent.StopClicked -> strings["telemetry_event_stop_clicked"]
    is TelemetryEvent.AlertSubmitted -> strings["telemetry_event_alert_submitted"]
    is TelemetryEvent.AlertRead -> strings["telemetry_event_alert_read"]
}

@Composable
private fun getEventDetails(event: TelemetryEvent, strings: StringProvider): List<Pair<String, String>> = when (event) {
    is TelemetryEvent.SessionOpened -> listOf(strings["telemetry_detail_session_id"] to event.sessionId.take(8) + "...")
    is TelemetryEvent.SessionClosed -> listOf(
        strings["telemetry_detail_session_id"] to event.sessionId.take(8) + "...",
        strings["telemetry_detail_duration"] to formatSessionDuration(event.openedAt, event.closedAt)
    )
    is TelemetryEvent.SearchStop -> listOf(strings["telemetry_detail_stop_id"] to event.stopId)
    is TelemetryEvent.SearchLine -> listOf(strings["telemetry_detail_line"] to event.lineId)
    is TelemetryEvent.SearchItinerary -> listOf(
        strings["telemetry_detail_departure"] to formatPlaceRef(event.originRef),
        strings["telemetry_detail_arrival"] to formatPlaceRef(event.destRef)
    )
    is TelemetryEvent.ItineraryCalculated -> listOf(
        strings["telemetry_detail_departure"] to formatPlaceRef(event.origin),
        strings["telemetry_detail_arrival"] to formatPlaceRef(event.dest),
        strings["telemetry_detail_options_proposed"] to "${event.options.size}"
    )
    is TelemetryEvent.ItineraryChosen -> listOf(
        strings["telemetry_detail_option_index"] to "${event.optionIndex + 1}"
    )
    is TelemetryEvent.TripCompleted -> listOf(
        strings["telemetry_detail_duration"] to formatSessionDuration(event.startedAt, event.endedAt),
        strings["telemetry_detail_stops_passed"] to "${event.stopsPassed.size}"
    )
    is TelemetryEvent.LineClicked -> listOf(
        strings["telemetry_detail_line"] to event.lineId,
        strings["telemetry_detail_context"] to translateContext(event.context, strings)
    )
    is TelemetryEvent.StopClicked -> listOf(
        strings["telemetry_detail_stop"] to event.stopId,
        strings["telemetry_detail_context"] to translateContext(event.context, strings)
    )
    is TelemetryEvent.AlertSubmitted -> buildList {
        add(strings["telemetry_detail_type"] to translateAlertKind(event.kind, strings))
        event.stopId?.let { add(strings["telemetry_detail_stop"] to it) }
        event.lineId?.let { add(strings["telemetry_detail_line"] to it) }
    }
    is TelemetryEvent.AlertRead -> listOf(strings["telemetry_detail_alert_id"] to event.alertId)
}

private fun formatPlaceRef(ref: PlaceRef): String {
    return ref.stopId ?: (ref.h3?.take(10) ?: "Inconnu")
}

@Composable
private fun translateContext(ctx: String, strings: StringProvider): String = when (ctx) {
    "map" -> strings["telemetry_context_map"]
    "search" -> strings["telemetry_context_search"]
    "itinerary" -> strings["telemetry_context_itinerary"]
    "favorites" -> strings["telemetry_context_favorites"]
    else -> ctx
}

@Composable
private fun translateAlertKind(kind: String, strings: StringProvider): String = when (kind) {
    "closure" -> strings["alert_type_closure"]
    "delay" -> strings["alert_type_delay"]
    "elevator" -> strings["alert_type_elevator"]
    "crowding" -> strings["alert_type_crowding"]
    "works" -> strings["alert_type_works"]
    "strike" -> strings["alert_type_strike"]
    "fire" -> strings["alert_type_fire"]
    "interruption" -> strings["alert_type_interruption"]
    "congestion" -> strings["alert_type_congestion"]
    "crowded" -> strings["telemetry_alert_crowded"]
    "incident" -> strings["telemetry_alert_incident"]
    else -> kind
}

private fun formatSessionDuration(startStr: String, endStr: String): String {
    return try {
        val startInstant = Instant.parse(startStr)
        val endInstant = Instant.parse(endStr)
        val seconds = (endInstant.toEpochMilliseconds() - startInstant.toEpochMilliseconds()) / 1000
        if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s"
    } catch (_: Exception) {
        "N/A"
    }
}
