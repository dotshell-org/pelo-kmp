@file:Suppress("VariableNeverRead")

package com.pelotcl.app.generic.ui.screens.plan.itinerary

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pelotcl.app.generic.data.models.realtime.alerts.community.UserStopAlert
import com.pelotcl.app.generic.data.repository.itinerary.itinerary.ItineraryPreferencesRepository
import com.pelotcl.app.generic.data.repository.itinerary.itinerary.JourneyResult
import com.pelotcl.app.generic.data.models.itinerary.SelectedStop
import com.pelotcl.app.generic.data.models.itinerary.TimeMode
import com.pelotcl.app.generic.ui.theme.PrimaryColor
import com.pelotcl.app.generic.ui.theme.SecondaryColor
import com.pelotcl.app.generic.ui.viewmodel.TransportViewModel
import com.pelotcl.app.generic.utils.graphics.BusIconHelper
import com.pelotcl.app.generic.utils.search.SearchUtils
import com.pelotcl.app.generic.utils.LineColorHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale

private const val MAX_ITINERARY_STOP_IDS_PER_SIDE = 64
private const val MAX_ITINERARY_FALLBACK_STOPS = 2

private data class AvoidedJourneyUi(
    val journey: JourneyResult,
    val label: String
)

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun InlineItinerarySheetContent(
    viewModel: TransportViewModel,
    departureStop: SelectedStop?,
    arrivalStop: SelectedStop?,
    maxHeight: Dp,
    nearbyDepartureStops: List<String> = emptyList(),
    onDepartureFallbackSelected: (SelectedStop) -> Unit = {},
    onJourneysChanged: (List<JourneyResult>) -> Unit = {},
    onSelectedJourneyChanged: (JourneyResult?) -> Unit = {},
    onStartNavigation: (JourneyResult) -> Unit = {},
    onClose: () -> Unit,
    onRequestExpandSheet: () -> Unit = {}
) {
    val raptorRepository = viewModel.raptorRepository
    val context = LocalContext.current
    
    // Load user preferences for route filtering
    val itineraryPrefsRepo = remember { ItineraryPreferencesRepository(context) }
    val jdLinesEnabled = remember { itineraryPrefsRepo.isJdLinesEnabled() }
    val rxLineEnabled = remember { itineraryPrefsRepo.isRxLineEnabled() }
    
    // Build set of blocked route names based on user preferences
    // For JD lines, we need to block all possible JD line numbers (JD1-JD999)
    // since the Raptor library does exact matching, not prefix matching
    val blockedRouteNames = remember(jdLinesEnabled, rxLineEnabled) {
        buildSet {
            if (!jdLinesEnabled) {
                // Add all possible JD line patterns
                for (i in 1..999) {
                    add("JD$i")
                }
            }
            if (!rxLineEnabled) add("RX")
        }
    }

    var timeMode by remember { mutableStateOf(TimeMode.DEPARTURE) }
    var selectedTimeSeconds by remember { mutableStateOf<Int?>(null) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    var journeys by remember { mutableStateOf<List<JourneyResult>>(emptyList()) }
    var journeysAvoidingAlerts by remember { mutableStateOf<List<AvoidedJourneyUi>>(emptyList()) }
    var selectedJourney by remember { mutableStateOf<JourneyResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var previousStopPairKey by remember { mutableStateOf<String?>(null) }
    var recalcVersion by remember { mutableStateOf(0) }
    var avoidAlertsJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Reset date/time overrides when the itinerary stop pair changes so default search
    // uses the current time for each new itinerary request.
    LaunchedEffect(departureStop?.name, arrivalStop?.name) {
        val currentPairKey = "${departureStop?.name.orEmpty()}->${arrivalStop?.name.orEmpty()}"
        if (previousStopPairKey != null && previousStopPairKey != currentPairKey) {
            selectedTimeSeconds = null
            selectedDate = null
        }
        previousStopPairKey = currentPairKey
    }

    suspend fun recalc() {
        suspend fun calculateJourneys(
            originIds: List<Int>,
            destinationIds: List<Int>,
            date: LocalDate,
            blockedNames: Set<String>
        ): List<JourneyResult> {
            return withContext(Dispatchers.IO) {
                if (timeMode == TimeMode.ARRIVAL) {
                    raptorRepository.getOptimizedPathsArriveBy(
                        originStopIds = originIds,
                        destinationStopIds = destinationIds,
                        arrivalTimeSeconds = selectedTimeSeconds ?: defaultArrivalSeconds(),
                        searchWindowMinutes = 120,
                        date = date,
                        blockedRouteNames = blockedNames
                    )
                } else {
                    raptorRepository.getOptimizedPaths(
                        originStopIds = originIds,
                        destinationStopIds = destinationIds,
                        departureTimeSeconds = selectedTimeSeconds,
                        date = date,
                        blockedRouteNames = blockedNames
                    )
                }
            }
        }

        fun extractStopNames(journey: JourneyResult): Set<String> {
            val names = mutableSetOf<String>()
            journey.legs.forEach { leg ->
                if (!leg.isWalking) {
                    names.add(leg.fromStopName)
                    names.add(leg.toStopName)
                    leg.intermediateStops.forEach { names.add(it.stopName) }
                }
            }
            return names
        }

        fun extractRouteNamesAtProblematicStops(
            allJourneys: List<JourneyResult>,
            problematicStops: Set<String>
        ): Set<String> {
            if (problematicStops.isEmpty()) return emptySet()

            val normalizedProblematic = problematicStops.map(SearchUtils::normalizeStopKey).toSet()

            val blockedNames = mutableSetOf<String>()
            allJourneys.forEach { journey ->
                journey.legs.forEach { leg ->
                    if (leg.isWalking) return@forEach
                    val touchesProblematicStop =
                        normalizedProblematic.contains(SearchUtils.normalizeStopKey(leg.fromStopName)) ||
                            normalizedProblematic.contains(SearchUtils.normalizeStopKey(leg.toStopName)) ||
                            leg.intermediateStops.any {
                                normalizedProblematic.contains(SearchUtils.normalizeStopKey(it.stopName))
                            }
                    if (touchesProblematicStop && !leg.routeName.isNullOrBlank()) {
                        blockedNames.add(leg.routeName)
                    }
                }
            }
            return blockedNames
        }

        fun journeyTouchesProblematicStop(
            journey: JourneyResult,
            problematicStops: Set<String>
        ): Boolean {
            if (problematicStops.isEmpty()) return false

            val normalizedProblematic = problematicStops.map(SearchUtils::normalizeStopKey).toSet()
            return journey.legs.any { leg ->
                if (leg.isWalking) return@any false
                normalizedProblematic.contains(SearchUtils.normalizeStopKey(leg.fromStopName)) ||
                    normalizedProblematic.contains(SearchUtils.normalizeStopKey(leg.toStopName)) ||
                    leg.intermediateStops.any {
                        normalizedProblematic.contains(SearchUtils.normalizeStopKey(it.stopName))
                    }
            }
        }

        fun buildAvoidedLabel(problematicDetails: Map<String, List<UserStopAlert>>): String {
            if (problematicDetails.isEmpty()) return "Alertes utilisateur évitées"

            val topEntry = problematicDetails
                .entries
                .maxByOrNull { (_, alerts) -> alerts.maxOfOrNull { it.karma } ?: Int.MIN_VALUE }
                ?: return "Alertes utilisateur évitées"

            val stopName = topEntry.key
            val topAlertType = topEntry.value.maxByOrNull { it.karma }?.type?.lowercase(Locale.ROOT)

            return when (topAlertType) {
                "closure" -> "Arret ferme évité à $stopName"
                "delay" -> "Retard évité à $stopName"
                "elevator" -> "Ascenseur HS évité à $stopName"
                "crowding" -> "Forte foule évitée à $stopName"
                "works" -> "Travaux évités à $stopName"
                "strike" -> "Greve évitée à $stopName"
                "fire" -> "Incendie évité à $stopName"
                "interruption" -> "Interruption évitée à $stopName"
                "congestion" -> "Trafic eleve évité à $stopName"
                "incident" -> "Incident évité à $stopName"
                "security" -> "Alerte securite évitée à $stopName"
                else -> "Alerte utilisateur évitée à $stopName"
            }
        }

        val departureStopIds =
            departureStop?.stopIds?.distinct()?.take(MAX_ITINERARY_STOP_IDS_PER_SIDE) ?: emptyList()
        val arrivalStopIds =
            arrivalStop?.stopIds?.distinct()?.take(MAX_ITINERARY_STOP_IDS_PER_SIDE) ?: emptyList()
        if (departureStopIds.isEmpty() || arrivalStopIds.isEmpty()) {
            journeys = emptyList()
            journeysAvoidingAlerts = emptyList()
            selectedJourney = null
            errorText = null
            return
        }
        avoidAlertsJob?.cancel()
        recalcVersion += 1
        val currentVersion = recalcVersion
        isLoading = true
        errorText = null
        journeysAvoidingAlerts = emptyList()
        selectedJourney = null
        try {
            val today = LocalDate.now()
            val date = selectedDate ?: today
            journeys = calculateJourneys(
                originIds = departureStopIds,
                destinationIds = arrivalStopIds,
                date = date,
                blockedNames = blockedRouteNames
            )
            if (journeys.isEmpty() && nearbyDepartureStops.isNotEmpty() && timeMode == TimeMode.DEPARTURE) {
                for (fallbackName in nearbyDepartureStops.take(MAX_ITINERARY_FALLBACK_STOPS)) {
                    if (fallbackName.equals(departureStop?.name, ignoreCase = true)) continue

                    val fallbackIds = raptorRepository.resolveStopIdsByName(
                        fallbackName,
                        maxIds = MAX_ITINERARY_STOP_IDS_PER_SIDE
                    )
                    if (fallbackIds.isEmpty()) continue

                    val fallbackJourneys = calculateJourneys(
                        originIds = fallbackIds,
                        destinationIds = arrivalStopIds,
                        date = date,
                        blockedNames = blockedRouteNames
                    )

                    if (fallbackJourneys.isNotEmpty()) {
                        journeys = fallbackJourneys
                        onDepartureFallbackSelected(
                            SelectedStop(
                                name = fallbackName,
                                stopIds = fallbackIds
                            )
                        )
                        break
                    }
                }
            }

            if (journeys.isEmpty() && timeMode == TimeMode.DEPARTURE && date == today) {
                val hasServiceEarlierToday = withContext(Dispatchers.IO) {
                    raptorRepository.getOptimizedPaths(
                        originStopIds = departureStopIds,
                        destinationStopIds = arrivalStopIds,
                        departureTimeSeconds = 0,
                        date = today,
                        blockedRouteNames = blockedRouteNames
                    ).isNotEmpty()
                }

                if (hasServiceEarlierToday) {
                    val tomorrow = today.plusDays(1)
                    journeys = calculateJourneys(
                        originIds = departureStopIds,
                        destinationIds = arrivalStopIds,
                        date = tomorrow,
                        blockedNames = blockedRouteNames
                    )
                    selectedDate = tomorrow
                    selectedTimeSeconds = 0
                }
            }

            if (journeys.isEmpty()) {
                errorText = "Aucun itineraire trouve"
            }
        } catch (_: Exception) {
            errorText = "Erreur lors du calcul d'itineraire"
        } finally {
            isLoading = false
        }

        if (journeys.isEmpty()) return

        val journeysSnapshot = journeys
        val dateSnapshot = selectedDate ?: LocalDate.now()
        val blockedSnapshot = blockedRouteNames
        avoidAlertsJob = coroutineScope.launch {
            try {
                val stopNames = journeysSnapshot.flatMap { extractStopNames(it) }.distinct()
                if (stopNames.isEmpty()) return@launch

                Log.d(
                    "InlineItinerary",
                    "Alert-avoidance input stops (${stopNames.size}): ${stopNames.joinToString()}"
                )

                val problematicDetails = withContext(Dispatchers.IO) {
                    viewModel.userStopAlertsRepository.getProblematicAlertDetails(stopNames)
                }
                val problematicStops = problematicDetails.keys
                Log.d(
                    "InlineItinerary",
                    "Problematic stops after threshold filter (${problematicStops.size}): ${problematicStops.joinToString()}"
                )

                val routeNamesToAvoid = extractRouteNamesAtProblematicStops(
                    allJourneys = journeysSnapshot,
                    problematicStops = problematicStops
                )
                Log.d(
                    "InlineItinerary",
                    "Route names to avoid (${routeNamesToAvoid.size}): ${routeNamesToAvoid.joinToString()}"
                )

                if (routeNamesToAvoid.isEmpty()) return@launch

                val blockedForAvoided = blockedSnapshot + routeNamesToAvoid
                val avoidedJourneys = calculateJourneys(
                    originIds = departureStopIds,
                    destinationIds = arrivalStopIds,
                    date = dateSnapshot,
                    blockedNames = blockedForAvoided
                )

                val seenAvoidedSignatures = mutableSetOf<String>()
                val label = buildAvoidedLabel(problematicDetails)
                val recalculatedAvoided = avoidedJourneys
                    .filter { !journeyTouchesProblematicStop(it, problematicStops) }
                    .filter {
                        val sig = journeySignature(it)
                        seenAvoidedSignatures.add(sig)
                    }

                val baselineAvoided = journeysSnapshot
                    .filter { !journeyTouchesProblematicStop(it, problematicStops) }
                    .filter {
                        val sig = journeySignature(it)
                        seenAvoidedSignatures.add(sig)
                    }

                val nextAvoided = (recalculatedAvoided + baselineAvoided)
                    .map { AvoidedJourneyUi(journey = it, label = label) }

                if (currentVersion != recalcVersion) return@launch
                journeysAvoidingAlerts = nextAvoided
                Log.d(
                    "InlineItinerary",
                    "Avoided journeys kept: ${journeysAvoidingAlerts.size} / recalculated=${avoidedJourneys.size}"
                )
            } catch (e: Exception) {
                Log.e("InlineItinerary", "Error fetching user stop alerts", e)
            }
        }
    }

    LaunchedEffect(departureStop, arrivalStop, timeMode, selectedTimeSeconds, selectedDate) {
        recalc()
    }

    LaunchedEffect(journeys, journeysAvoidingAlerts) {
        onJourneysChanged(journeysAvoidingAlerts.map { it.journey } + journeys)
    }

    LaunchedEffect(selectedJourney) {
        onSelectedJourneyChanged(selectedJourney)
    }

    val showSearchBars = selectedJourney == null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .padding(horizontal = 16.dp)
    ) {
        // Header row with title and close button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Show "Itinéraire" title when no journey is selected
            if (selectedJourney == null) {
                Text(
                    text = "Itinéraire",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryColor,
                    modifier = Modifier.padding(start = 16.dp)
                )
            } else {
                // Show extra large line icons on the left when a journey is selected
                val context = LocalContext.current
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    val nonWalkingLegs = remember(selectedJourney!!.legs) {
                        selectedJourney!!.legs.filterNot { it.isWalking }
                    }

                    nonWalkingLegs.forEachIndexed { index, leg ->
                        val resourceId = BusIconHelper.getResourceIdForLine(context, leg.routeName ?: "")

                        if (resourceId != 0) {
                            Image(
                                painter = painterResource(id = resourceId),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp)
                            )
                        } else {
                            val legColor = remember(leg.routeName) {
                                Color(LineColorHelper.getColorForLineString(leg.routeName ?: ""))
                            }
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(legColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = leg.routeName ?: "?",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SecondaryColor
                                )
                            }
                        }
                        
                        if (index < nonWalkingLegs.size - 1) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = PrimaryColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // Show close button (for closing the sheet when no journey is selected, or back button when a journey is selected)
            IconButton(
                onClick = {
                    if (selectedJourney != null) {
                        selectedJourney = null
                        onRequestExpandSheet()
                    } else {
                        onClose()
                    }
                }
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = if (selectedJourney != null) "Retour" else "Fermer",
                    tint = PrimaryColor
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedJourney == null) {
            if (isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(color = PrimaryColor)
                }
            } else if (errorText != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = errorText!!, color = PrimaryColor)
                }
            } else {
                val avoidedSignatures = remember(journeysAvoidingAlerts) {
                    journeysAvoidingAlerts.map { journeySignature(it.journey) }.toHashSet()
                }
                val regularJourneys = remember(journeys, avoidedSignatures) {
                    journeys.filter { journeySignature(it) !in avoidedSignatures }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    if (showSearchBars) {
                        item {
                            TimeSelectionRow(
                                timeMode = timeMode,
                                selectedTimeSeconds = selectedTimeSeconds,
                                selectedDate = selectedDate,
                                onTimeModeChange = { timeMode = it },
                                onTimeClick = { showTimePicker = true },
                                onDateClick = { showDatePicker = true },
                                onClearDateTime = {
                                    selectedTimeSeconds = null
                                    selectedDate = null
                                },
                                useLightColors = true
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }

                    if (journeysAvoidingAlerts.isNotEmpty()) {
                        items(journeysAvoidingAlerts, key = { "${it.journey.departureTime}_${it.journey.arrivalTime}_${it.journey.legs.size}_${it.label}" }) { avoidedJourney ->
                            CompactJourneyCard(
                                journey = avoidedJourney.journey,
                                onClick = { selectedJourney = avoidedJourney.journey },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                useLightColors = true,
                                showAvoidedAlertsBadge = true,
                                avoidedAlertsLabel = avoidedJourney.label
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    if (regularJourneys.isNotEmpty()) {
                        items(regularJourneys, key = { "${it.departureTime}_${it.arrivalTime}_${it.legs.size}" }) { journey ->
                            CompactJourneyCard(
                                journey = journey,
                                onClick = { selectedJourney = journey },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                useLightColors = true
                            )
                        }
                    }
                }
            }
        } else {
            JourneyDetailsSheetContent(
                journey = selectedJourney!!,
                isExpanded = true,
                onStartNavigation = { onStartNavigation(selectedJourney!!) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                useLightColors = true,
                scrollAllContent = true
            )
        }

        if (showTimePicker) {
            TimePickerDialog(
                initialTimeSeconds = selectedTimeSeconds ?: defaultArrivalSeconds(),
                onTimeSelected = { seconds ->
                    selectedTimeSeconds = seconds
                },
                onDismiss = { showTimePicker = false }
            )
        }

        if (showDatePicker) {
            DatePickerDialog(
                initialDate = selectedDate ?: LocalDate.now(),
                onDateSelected = { date ->
                    selectedDate = date
                },
                onDismiss = { showDatePicker = false }
            )
        }
    }
}

private fun defaultArrivalSeconds(): Int {
    val cal = Calendar.getInstance()
    return (cal.get(Calendar.HOUR_OF_DAY) + 1) * 3600 + cal.get(Calendar.MINUTE) * 60
}

private fun journeySignature(journey: JourneyResult): String {
    val legSig = journey.legs.joinToString("|") { leg ->
        listOf(
            leg.fromStopId,
            leg.toStopId,
            leg.routeName.orEmpty(),
            leg.departureTime.toString(),
            leg.arrivalTime.toString(),
            leg.isWalking.toString()
        ).joinToString("~")
    }
    return "${journey.departureTime}->${journey.arrivalTime}#$legSig"
}
