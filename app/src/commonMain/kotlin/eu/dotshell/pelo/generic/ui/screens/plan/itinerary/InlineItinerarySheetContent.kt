@file:Suppress("VariableNeverRead")

package eu.dotshell.pelo.generic.ui.screens.plan.itinerary

import eu.dotshell.pelo.platform.ioDispatcher

import eu.dotshell.pelo.platform.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import eu.dotshell.pelo.generic.ui.theme.isAppInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.dotshell.pelo.generic.data.models.realtime.alerts.community.UserStopAlert
import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.ItineraryPreferencesRepository
import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.JourneyLegKind
import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.JourneyResult
import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.journeyWalkingMeters
import eu.dotshell.pelo.generic.data.models.itinerary.SelectedStop
import eu.dotshell.pelo.generic.data.telemetry.PlaceRef
import eu.dotshell.pelo.generic.data.telemetry.PrivacyScrubber
import io.raptor.Location
import io.raptor.WalkingParams
import eu.dotshell.pelo.generic.data.models.itinerary.TimeMode
import eu.dotshell.pelo.generic.ui.viewmodel.TransportViewModel
import eu.dotshell.pelo.generic.utils.graphics.LineIconResolver
import eu.dotshell.pelo.generic.utils.search.SearchUtils
import eu.dotshell.pelo.generic.utils.LineColorHelper
import eu.dotshell.pelo.platform.DrawableProvider
import eu.dotshell.pelo.platform.LocalPlatformContext
import eu.dotshell.pelo.platform.StringProvider
import eu.dotshell.pelo.platform.randomId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

private const val MAX_ITINERARY_STOP_IDS_PER_SIDE = 64
private const val MAX_ITINERARY_FALLBACK_STOPS = 2

// "Closest stop, then walk" fallback: when nothing else is found, retry with a large access/egress
// walk radius and only keep options whose total walk stays within this cap (meters).
private const val LONG_WALK_METERS = 6000.0

/**
 * Raptor endpoint for this selection: an arbitrary point for coordinate endpoints (address/POI,
 * GPS), the platform-id set for stops, null when unusable.
 */
private fun SelectedStop.toRaptorLocation(): Location? = when {
    lat != null && lon != null -> Location.Point(lat, lon)
    stopIds.isNotEmpty() -> Location.StopIds(stopIds.distinct().take(MAX_ITINERARY_STOP_IDS_PER_SIDE))
    else -> null
}

/**
 * Telemetry place reference. Stops keep their name as before; coordinate endpoints emit only a
 * ~600 m geohash bucket — the raw address label and exact coordinates never leave the device.
 */
private fun SelectedStop.toPlaceRef(): PlaceRef? = when {
    lat != null && lon != null -> PlaceRef(h3 = PrivacyScrubber.bucket(lat, lon))
    name.isNotBlank() -> PlaceRef(stopId = name)
    else -> null
}

private data class AvoidedJourneyUi(
    val journey: JourneyResult,
    val label: String
)

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
    val context = LocalPlatformContext.current
    val strings = StringProvider(context)

    // Load user preferences for route filtering
    val itineraryPrefsRepo = remember { ItineraryPreferencesRepository(context) }
    val jdLinesEnabled = remember { itineraryPrefsRepo.isJdLinesEnabled() }
    val rxLineEnabled = remember { itineraryPrefsRepo.isRxLineEnabled() }

    // Build set of blocked route names based on user preferences.
    // raptorKt's RouteFilter matches route names EXACTLY (no wildcards), so
    // the JD family must be expanded into the real line names of the network.
    val blockedRouteNames = remember(jdLinesEnabled, rxLineEnabled) {
        buildSet {
            if (!jdLinesEnabled) {
                addAll(raptorRepository.getAllRouteNames().filter { it.startsWith("JD", ignoreCase = true) })
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
    // Non-null when the shown journeys come from the "closest stop, then walk" fallback; holds the
    // shortest total walk (meters) among them, for the banner label. Reset each recalc.
    var longWalkFallbackMeters by remember { mutableStateOf<Double?>(null) }
    var previousStopPairKey by remember { mutableStateOf<String?>(null) }
    var recalcVersion by remember { mutableStateOf(0) }
    // When recalc() itself moves the search to "tomorrow" it writes selectedDate/selectedTimeSeconds,
    // which are keys of the recalc LaunchedEffect. This flag swallows that one self-triggered
    // re-run so the (already-computed) tomorrow journeys aren't recalculated a second time.
    var suppressRecalcOnce by remember { mutableStateOf(false) }
    var avoidAlertsJob by remember { mutableStateOf<Job?>(null) }
    // Telemetry: the calc_id of the most recent itinerary calculation. Bound at the start of
    // recalc() and reused for itinerary.calculated + itinerary.chosen events to correlate them.
    var lastCalcId by remember { mutableStateOf<String?>(null) }
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
            origin: Location,
            destination: Location,
            date: LocalDate,
            blockedNames: Set<String>,
            overrideTimeSeconds: Int? = null,
            walking: WalkingParams = WalkingParams.DEFAULT
        ): List<JourneyResult> {
            return withContext(ioDispatcher) {
                if (timeMode == TimeMode.ARRIVAL) {
                    raptorRepository.getOptimizedPathsArriveBy(
                        origin = origin,
                        destination = destination,
                        arrivalTimeSeconds = overrideTimeSeconds ?: selectedTimeSeconds ?: defaultArrivalSeconds(),
                        searchWindowMinutes = 120,
                        date = date,
                        blockedRouteNames = blockedNames,
                        originLabel = departureStop?.name,
                        destinationLabel = arrivalStop?.name,
                        walking = walking
                    )
                } else {
                    raptorRepository.getOptimizedPaths(
                        origin = origin,
                        destination = destination,
                        departureTimeSeconds = overrideTimeSeconds ?: selectedTimeSeconds,
                        date = date,
                        blockedRouteNames = blockedNames,
                        originLabel = departureStop?.name,
                        destinationLabel = arrivalStop?.name,
                        walking = walking
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
            val topAlertType = topEntry.value.maxByOrNull { it.karma }?.type?.lowercase()

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

        val originLocation = departureStop?.toRaptorLocation()
        val destinationLocation = arrivalStop?.toRaptorLocation()
        if (originLocation == null || destinationLocation == null) {
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
        longWalkFallbackMeters = null
        journeysAvoidingAlerts = emptyList()
        selectedJourney = null

        // Telemetry: mint a new calc_id and emit search.itinerary before we kick off the heavy
        // Raptor call. Stops use their name as the place ref; coordinate endpoints (addresses,
        // GPS) are scrubbed to a ~600 m geohash bucket in toPlaceRef — raw addresses and exact
        // coordinates never leave the device.
        val calcId = randomId()
        lastCalcId = calcId
        val originRef = departureStop?.toPlaceRef()
        val destRef = arrivalStop?.toPlaceRef()
        if (originRef != null && destRef != null) {
            eu.dotshell.pelo.generic.data.telemetry.TelemetryEmitter.emit(
                eu.dotshell.pelo.generic.data.telemetry.TelemetryEvent.SearchItinerary(
                    eventId = randomId(),
                                at = Clock.System.now().toString(),
                    originRef = originRef,
                    destRef = destRef
                )
            )
        }

        try {
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val date = selectedDate ?: today
            journeys = calculateJourneys(
                origin = originLocation,
                destination = destinationLocation,
                date = date,
                blockedNames = blockedRouteNames
            )
            // Nearby-stop fallback only applies to stop departures: a coordinate departure
            // already competes with walking to every stop in range natively
            if (journeys.isEmpty() && nearbyDepartureStops.isNotEmpty() &&
                timeMode == TimeMode.DEPARTURE && departureStop?.isCoordinate != true
            ) {
                for (fallbackName in nearbyDepartureStops.take(MAX_ITINERARY_FALLBACK_STOPS)) {
                    if (fallbackName.equals(departureStop?.name, ignoreCase = true)) continue

                    val fallbackIds = raptorRepository.resolveStopIdsByName(
                        fallbackName,
                        maxIds = MAX_ITINERARY_STOP_IDS_PER_SIDE
                    )
                    if (fallbackIds.isEmpty()) continue

                    val fallbackJourneys = calculateJourneys(
                        origin = Location.StopIds(fallbackIds),
                        destination = destinationLocation,
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

            // "Closest stop, then walk" fallback — before the "tomorrow" fallback, so getting home
            // TONIGHT via a longer walk beats punting to tomorrow's first bus. Retry with a large
            // access/egress walk radius; keep only options whose total walk stays under the cap so we
            // don't suggest an absurd multi-hour hike. Only meaningful for coordinate endpoints
            // (a pure stop destination has no egress walk).
            if (journeys.isEmpty()) {
                val longWalk = WalkingParams(
                    maxAccessEgressDistanceMeters = LONG_WALK_METERS,
                    maxDirectWalkDistanceMeters = LONG_WALK_METERS
                )
                val longWalkJourneys = calculateJourneys(
                    origin = originLocation,
                    destination = destinationLocation,
                    date = date,
                    blockedNames = blockedRouteNames,
                    walking = longWalk
                ).filter { journeyWalkingMeters(it, longWalk.speedMetersPerSecond) <= LONG_WALK_METERS }

                if (longWalkJourneys.isNotEmpty()) {
                    journeys = longWalkJourneys
                    longWalkFallbackMeters =
                        longWalkJourneys.minOf { journeyWalkingMeters(it, longWalk.speedMetersPerSecond) }
                }
            }

            if (journeys.isEmpty() && timeMode == TimeMode.DEPARTURE && date == today) {
                val hasServiceEarlierToday = withContext(ioDispatcher) {
                    raptorRepository.getOptimizedPaths(
                        origin = originLocation,
                        destination = destinationLocation,
                        departureTimeSeconds = 0,
                        date = today,
                        blockedRouteNames = blockedRouteNames,
                        originLabel = departureStop?.name,
                        destinationLabel = arrivalStop?.name
                    ).isNotEmpty()
                }

                if (hasServiceEarlierToday) {
                    val tomorrow = today.plus(1, DateTimeUnit.DAY)
                    journeys = calculateJourneys(
                        origin = originLocation,
                        destination = destinationLocation,
                        date = tomorrow,
                        blockedNames = blockedRouteNames,
                        overrideTimeSeconds = 0
                    )
                    suppressRecalcOnce = true
                    selectedDate = tomorrow
                    selectedTimeSeconds = 0
                }
            }

            if (journeys.isEmpty()) {
                errorText = "Aucun itineraire trouve"
            } else {
                // Telemetry: emit itinerary.calculated with the proposed options. We cap the
                // payload at 3 options to mirror how the UI displays them and keep the
                // message size small. Walking-only legs are excluded from the line list.
                val nowIso = Clock.System.now().toString()
                val depSecondsAtCalc = selectedTimeSeconds
                val options = journeys.take(3).mapIndexed { idx, journey ->
                    val nonWalkingLegs = journey.legs.filter { !it.isWalking }
                    eu.dotshell.pelo.generic.data.telemetry.ItineraryOption(
                        index = idx,
                        durationMin = journey.durationMinutes,
                        transfers = (nonWalkingLegs.size - 1).coerceAtLeast(0),
                        lines = nonWalkingLegs.mapNotNull { it.routeName }.distinct()
                    )
                }
                val departureIso = depSecondsAtCalc?.let { seconds ->
                    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                    LocalDateTime(
                        today.year, today.month, today.dayOfMonth,
                        seconds / 3600, (seconds % 3600) / 60
                    ).toInstant(TimeZone.currentSystemDefault()).toString()
                } ?: nowIso
                if (originRef != null && destRef != null) {
                    eu.dotshell.pelo.generic.data.telemetry.TelemetryEmitter.emit(
                        eu.dotshell.pelo.generic.data.telemetry.TelemetryEvent.ItineraryCalculated(
                            eventId = randomId(),
                            at = nowIso,
                            calcId = calcId,
                            origin = originRef,
                            dest = destRef,
                            requestedAt = nowIso,
                            departureAt = departureIso,
                            options = options
                        )
                    )
                }
            }
        } catch (_: Exception) {
            errorText = "Erreur lors du calcul d'itineraire"
        } finally {
            isLoading = false
        }

        if (journeys.isEmpty()) return

        val journeysSnapshot = journeys
        val dateSnapshot = selectedDate ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val blockedSnapshot = blockedRouteNames
        avoidAlertsJob = coroutineScope.launch {
            try {
                val stopNames = journeysSnapshot.flatMap { extractStopNames(it) }.distinct()
                if (stopNames.isEmpty()) return@launch

                Log.d(
                    "InlineItinerary",
                    "Alert-avoidance input stops (${stopNames.size}): ${stopNames.joinToString()}"
                )

                val problematicDetails = withContext(ioDispatcher) {
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
                    origin = originLocation,
                    destination = destinationLocation,
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
        if (suppressRecalcOnce) {
            suppressRecalcOnce = false
            return@LaunchedEffect
        }
        recalc()
    }

    LaunchedEffect(journeys, journeysAvoidingAlerts) {
        onJourneysChanged(journeysAvoidingAlerts.map { it.journey } + journeys)
    }

    LaunchedEffect(selectedJourney) {
        onSelectedJourneyChanged(selectedJourney)
    }

    // Drive the itinerary result palette from the app theme: light palette in light
    // mode (dark text on the light sheet), dark palette in dark mode (light text on
    // the dark sheet). The BottomSheetScaffold container is MaterialTheme surface.
    val useLightColors = !isAppInDarkTheme()
    val showSearchBars = selectedJourney == null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .padding(horizontal = if (selectedJourney == null) 16.dp else 0.dp)
    ) {
        // Header row with title and close button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (selectedJourney != null) Modifier.padding(horizontal = 16.dp) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Show "Itinéraire" title when no journey is selected
            if (selectedJourney == null) {
                Text(
                    text = strings["itinerary"],
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 16.dp)
                )
            } else {
                // Show extra large line icons on the left when a journey is selected.
                // Weighted + wrapping: the close button is measured first and keeps its normal
                // size; the badge strip flows onto extra lines when the journey has many legs
                val drawableProvider = DrawableProvider(context)
                FlowRow(
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {
                    val nonWalkingLegs = remember(selectedJourney?.legs) {
                        selectedJourney?.legs?.filterNot { it.isWalking }.orEmpty()
                    }
                    // Start/end walks only — mid-journey transfer walks stay hidden
                    val startWalk = remember(selectedJourney?.legs) {
                        selectedJourney?.legs?.firstOrNull()?.takeIf {
                            it.legKind == JourneyLegKind.WALK_ACCESS || it.legKind == JourneyLegKind.WALK_DIRECT
                        }
                    }
                    val endWalk = remember(selectedJourney?.legs) {
                        selectedJourney?.legs?.lastOrNull()?.takeIf { it.legKind == JourneyLegKind.WALK_EGRESS }
                    }

                    if (startWalk != null) {
                        WalkDurationChip(
                            minutes = startWalk.durationMinutes,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            textColor = MaterialTheme.colorScheme.onSurface,
                            iconSize = 24.dp,
                            fontSize = 14.sp
                        )
                        if (nonWalkingLegs.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    nonWalkingLegs.forEachIndexed { index, leg ->
                        val drawableName = LineIconResolver.getDrawableNameForLineName(leg.routeName ?: "")

                        if (drawableProvider.hasDrawable(drawableName)) {
                            Image(
                                painter = drawableProvider.getPainter(drawableName),
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
                                    // Contrast on the fixed line-color badge — not theme-driven.
                                    color = Color.White
                                )
                            }
                        }

                        if (index < nonWalkingLegs.size - 1) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    if (endWalk != null) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                        WalkDurationChip(
                            minutes = endWalk.durationMinutes,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            textColor = MaterialTheme.colorScheme.onSurface,
                            iconSize = 24.dp,
                            fontSize = 14.sp
                        )
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
                    tint = MaterialTheme.colorScheme.onSurface
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
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (errorText != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = errorText.orEmpty(), color = MaterialTheme.colorScheme.onSurface)
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
                                useLightColors = useLightColors
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }



                    if (journeysAvoidingAlerts.isNotEmpty()) {
                        items(journeysAvoidingAlerts, key = { "${journeySignature(it.journey)}_${it.label}" }) { avoidedJourney ->
                            CompactJourneyCard(
                                journey = avoidedJourney.journey,
                                onClick = { selectedJourney = avoidedJourney.journey },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                useLightColors = useLightColors,
                                showAvoidedAlertsBadge = true,
                                avoidedAlertsLabel = avoidedJourney.label
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    if (regularJourneys.isNotEmpty()) {
                        items(regularJourneys, key = { journeySignature(it) }) { journey ->
                            CompactJourneyCard(
                                journey = journey,
                                onClick = { selectedJourney = journey },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                useLightColors = useLightColors
                            )
                        }
                    }
                }
            }
        } else {
            val chosenJourney = selectedJourney
            if (chosenJourney != null) {
                JourneyDetailsSheetContent(
                    journey = chosenJourney,
                    isExpanded = true,
                    onStartNavigation = {
                        val combined = journeysAvoidingAlerts.map { it.journey } + journeys
                        val index = combined.indexOf(chosenJourney).takeIf { it >= 0 } ?: -1
                        lastCalcId?.let { calcId ->
                            eu.dotshell.pelo.generic.data.telemetry.TelemetryEmitter.emit(
                                eu.dotshell.pelo.generic.data.telemetry.TelemetryEvent.ItineraryChosen(
                                    eventId = randomId(),
                                    at = Clock.System.now().toString(),
                                    calcId = calcId,
                                    optionIndex = index
                                )
                            )
                        }
                        onStartNavigation(chosenJourney)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    useLightColors = useLightColors,
                    scrollAllContent = true
                )
            }
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
                initialDate = selectedDate ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
                onDateSelected = { date ->
                    selectedDate = date
                },
                onDismiss = { showDatePicker = false }
            )
        }
    }
}

private fun defaultArrivalSeconds(): Int {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return (now.hour + 1) * 3600 + now.minute * 60
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
