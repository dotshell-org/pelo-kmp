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
import eu.dotshell.pelo.generic.ui.theme.PrimaryColor
import eu.dotshell.pelo.generic.ui.theme.SecondaryColor
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
                    Text("Oui", color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirmDialog = false }) {
                    Text("Non", color = SecondaryColor)
                }
            },
            containerColor = Color(0xFF1C1C1E),
            titleContentColor = SecondaryColor,
            textContentColor = Color.Gray
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = strings["privacy_title"],
                        color = SecondaryColor,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings["back"],
                            tint = SecondaryColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryColor
                )
            )
        },
        containerColor = PrimaryColor
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
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
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
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = strings["delete_local_history"],
                        color = Color(0xFFEF4444),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = strings["data_collected_today"],
                color = SecondaryColor,
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
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(text = snapshot.day, color = SecondaryColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(text = strings["events_count"].replace("%s", snapshot.events.size.toString()), color = SecondaryColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    EventBreakdownCard(events = snapshot.events)
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = strings["events_log"],
                        color = SecondaryColor,
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
    val title = eventDescriptionLabel(event)
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
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
                        color = SecondaryColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = strings["event_type"].replace("%s", event::class.simpleName ?: ""),
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
                Text(
                    text = time,
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            val details = getEventDetails(event)
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
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = value,
                                color = SecondaryColor.copy(alpha = 0.8f),
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

    val grouped = events.groupBy { eventShortLabel(it::class.simpleName ?: "Unknown") }
        .map { (label, list) ->
            val firstEvent = list.first()
            val (icon, color) = eventIconAndColor(firstEvent::class.simpleName ?: "Unknown")
            Triple(label, icon, list.size)
        }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Résumé de la journée", color = SecondaryColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            grouped.forEach { (label, icon, count) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, contentDescription = null, tint = SecondaryColor.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(label, color = SecondaryColor, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("$count", color = SecondaryColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = SecondaryColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
            Text(body, color = Color.Gray, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

private fun eventIconAndColor(simpleName: String): Pair<ImageVector, Color> = when (simpleName) {
    "SessionOpened", "SessionClosed" -> Icons.Default.AccountCircle to Color(0xFF9CA3AF)
    "SearchStop", "StopClicked" -> Icons.Default.Place to Color(0xFFEF4444)
    "SearchLine", "LineClicked" -> Icons.Default.Map to Color(0xFF3B82F6)
    "SearchItinerary", "ItineraryCalculated", "ItineraryChosen" -> Icons.Default.Directions to Color(0xFFF59E0B)
    "AlertSubmitted", "AlertRead" -> Icons.Default.Warning to Color(0xFFF59E0B)
    else -> Icons.Default.Notifications to Color(0xFF9CA3AF)
}

private fun eventShortLabel(simpleName: String): String = when (simpleName) {
    "SessionOpened", "SessionClosed" -> "Sessions"
    "SearchStop", "StopClicked" -> "Arrêts"
    "SearchLine", "LineClicked" -> "Lignes"
    "SearchItinerary", "ItineraryCalculated", "ItineraryChosen" -> "Itinéraires"
    "AlertSubmitted", "AlertRead" -> "Alertes"
    else -> simpleName
}

private fun eventDescriptionLabel(event: TelemetryEvent): String = when (event) {
    is TelemetryEvent.SessionOpened -> "Session démarrée"
    is TelemetryEvent.SessionClosed -> "Session terminée"
    is TelemetryEvent.SearchStop -> "Recherche d'arrêt"
    is TelemetryEvent.SearchLine -> "Recherche de ligne"
    is TelemetryEvent.SearchItinerary -> "Recherche d'itinéraire"
    is TelemetryEvent.ItineraryCalculated -> "Itinéraire calculé"
    is TelemetryEvent.ItineraryChosen -> "Itinéraire sélectionné"
    is TelemetryEvent.TripCompleted -> "Trajet complété"
    is TelemetryEvent.LineClicked -> "Ligne consultée"
    is TelemetryEvent.StopClicked -> "Arrêt consulté"
    is TelemetryEvent.AlertSubmitted -> "Signalement envoyé"
    is TelemetryEvent.AlertRead -> "Alerte lue"
}

private fun getEventDetails(event: TelemetryEvent): List<Pair<String, String>> = when (event) {
    is TelemetryEvent.SessionOpened -> listOf("ID session" to event.sessionId.take(8) + "...")
    is TelemetryEvent.SessionClosed -> listOf(
        "ID session" to event.sessionId.take(8) + "...",
        "Durée" to formatSessionDuration(event.openedAt, event.closedAt)
    )
    is TelemetryEvent.SearchStop -> listOf("ID Arrêt" to event.stopId)
    is TelemetryEvent.SearchLine -> listOf("Ligne" to event.lineId)
    is TelemetryEvent.SearchItinerary -> listOf(
        "Départ" to formatPlaceRef(event.originRef),
        "Arrivée" to formatPlaceRef(event.destRef)
    )
    is TelemetryEvent.ItineraryCalculated -> listOf(
        "Départ" to formatPlaceRef(event.origin),
        "Arrivée" to formatPlaceRef(event.dest),
        "Options proposées" to "${event.options.size}"
    )
    is TelemetryEvent.ItineraryChosen -> listOf(
        "Index option" to "${event.optionIndex + 1}"
    )
    is TelemetryEvent.TripCompleted -> listOf(
        "Durée" to formatSessionDuration(event.startedAt, event.endedAt),
        "Arrêts traversés" to "${event.stopsPassed.size}"
    )
    is TelemetryEvent.LineClicked -> listOf(
        "Ligne" to event.lineId,
        "Contexte" to translateContext(event.context)
    )
    is TelemetryEvent.StopClicked -> listOf(
        "Arrêt" to event.stopId,
        "Contexte" to translateContext(event.context)
    )
    is TelemetryEvent.AlertSubmitted -> buildList {
        add("Type" to translateAlertKind(event.kind))
        event.stopId?.let { add("Arrêt" to it) }
        event.lineId?.let { add("Ligne" to it) }
    }
    is TelemetryEvent.AlertRead -> listOf("ID Alerte" to event.alertId)
}

private fun formatPlaceRef(ref: PlaceRef): String {
    return ref.stopId ?: (ref.h3?.take(10) ?: "Inconnu")
}

private fun translateContext(ctx: String): String = when (ctx) {
    "map" -> "Carte"
    "search" -> "Recherche"
    "itinerary" -> "Itinéraire"
    "favorites" -> "Favoris"
    else -> ctx
}

private fun translateAlertKind(kind: String): String = when (kind) {
    "crowded" -> "Forte affluence"
    "incident" -> "Incident/Perturbation"
    "elevator" -> "Ascenseur en panne"
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
