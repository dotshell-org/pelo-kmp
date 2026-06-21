@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pelotcl.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pelotcl.app.generic.data.config.AppConfigLoader
import com.pelotcl.app.generic.data.models.geojson.FeatureCollection
import com.pelotcl.app.generic.data.models.geojson.StopCollection
import com.pelotcl.app.generic.data.models.geojson.StopFeature
import com.pelotcl.app.generic.data.models.itinerary.ItineraryFieldTarget
import com.pelotcl.app.generic.data.models.itinerary.SelectedStop
import com.pelotcl.app.generic.data.models.search.TransportSearchContent
import com.pelotcl.app.generic.data.models.stops.Favorite
import com.pelotcl.app.generic.data.models.stops.StationInfo
import com.pelotcl.app.generic.data.models.ui.AllSchedulesInfo
import com.pelotcl.app.generic.data.repository.itinerary.itinerary.ItineraryPreferencesRepository
import com.pelotcl.app.generic.data.repository.offline.mapstyle.MapStyleCompat
import com.pelotcl.app.generic.data.repository.offline.mapstyle.MapStyleRepository
import com.pelotcl.app.generic.service.TransportServiceProvider
import com.pelotcl.app.generic.utils.graphics.LineIconResolver
import com.pelotcl.app.generic.utils.map.toVehiclesGeoJson
import com.pelotcl.app.generic.ui.components.MapCanvas
import com.pelotcl.app.generic.ui.components.favorites.AddFavoriteDialog
import com.pelotcl.app.generic.ui.components.favorites.FavoritesBar
import com.pelotcl.app.generic.ui.components.search.TransportSearchBar
import com.pelotcl.app.generic.data.repository.offline.search.SearchHistoryRepository
import com.pelotcl.app.generic.data.repository.offline.search.SearchHistoryItem
import com.pelotcl.app.generic.data.repository.offline.search.SearchType
import com.pelotcl.app.generic.ui.screens.Destination
import com.pelotcl.app.generic.ui.screens.plan.AllSchedulesSheetContent
import com.pelotcl.app.generic.ui.screens.plan.LineDetailsBottomSheet
import com.pelotcl.app.generic.ui.screens.plan.LineInfo
import com.pelotcl.app.generic.ui.screens.plan.LinesBottomSheet
import com.pelotcl.app.generic.ui.screens.plan.MapStyleSelectionSheet
import com.pelotcl.app.generic.ui.screens.plan.StationSheetContent
import com.pelotcl.app.generic.ui.screens.plan.itinerary.InlineItinerarySheetContent
import com.pelotcl.app.generic.ui.screens.plan.itinerary.ItinerarySearchBarField
import com.pelotcl.app.generic.data.local_history.LocalHistoryStorage
import com.pelotcl.app.generic.data.telemetry.TelemetryEmitter
import com.pelotcl.app.generic.ui.screens.settings.ItinerarySettingsScreen
import com.pelotcl.app.generic.ui.screens.settings.OfflineSettingsScreen
import com.pelotcl.app.generic.ui.screens.settings.SettingsScreen
import com.pelotcl.app.generic.ui.screens.settings.TelemetryFaqScreen
import com.pelotcl.app.generic.ui.screens.settings.TelemetryPreviewScreen
import com.pelotcl.app.generic.ui.screens.settings.TelemetrySettingsScreen
import com.pelotcl.app.generic.ui.screens.settings.about.ContactScreen
import com.pelotcl.app.generic.ui.screens.settings.about.CreditsScreen
import com.pelotcl.app.generic.ui.screens.settings.about.LegalScreen
import com.pelotcl.app.generic.ui.theme.AccentColor
import com.pelotcl.app.generic.ui.theme.PeloTheme
import com.pelotcl.app.generic.ui.theme.PrimaryColor
import com.pelotcl.app.generic.ui.theme.SecondaryColor
import com.pelotcl.app.generic.ui.viewmodel.TransportLinesUiState
import com.pelotcl.app.generic.ui.viewmodel.TransportStopsUiState
import com.pelotcl.app.generic.ui.viewmodel.TransportViewModel
import com.pelotcl.app.generic.utils.location.LocationProvider
import com.pelotcl.app.platform.LocalPlatformContext
import com.pelotcl.app.platform.Log
import com.pelotcl.app.platform.appVersionName
import com.pelotcl.app.platform.ioDispatcher
import kotlinx.coroutines.launch
import org.maplibre.spatialk.geojson.Position

/**
 * Shared application root (commonMain) — the cross-platform Plan UI assembled from common
 * building blocks. Each platform provides a PlatformContext via [LocalPlatformContext] and hosts
 * this composable: iOS via `MainViewController()`; Android (eventually) via `MainActivity`.
 */
@Composable
fun App(onNavigationModeChanged: (Boolean) -> Unit = {}) {
    PeloTheme {
        val context = LocalPlatformContext.current
        val viewModel = remember {
            try {
                TransportServiceProvider.initialize(context)
                TransportViewModel(context)
            } catch (t: Throwable) {
                Log.e("PeloApp", "Transport data init failed: ${t.message}")
                null
            }
        }
        LaunchedEffect(viewModel) {
            val vm = viewModel ?: return@LaunchedEffect
            runCatching { vm.raptorRepository.initialize() }
                .onFailure { Log.e("PeloApp", "Raptor init failed: ${it.message}") }
        }
        if (viewModel != null) {
            RootScaffold(viewModel, onNavigationModeChanged)
        } else {
            MapCanvas(modifier = Modifier.fillMaxSize(), styleUrl = MapStyleCompat.POSITRON.styleUrl)
        }
    }
}

@Composable
private fun RootScaffold(
    viewModel: TransportViewModel,
    onNavigationModeChanged: (Boolean) -> Unit = {}
) {
    val context = LocalPlatformContext.current
    val scope = rememberCoroutineScope()
    val lineRules = remember { TransportServiceProvider.getTransportLineRules() }
    var selectedTab by remember { mutableStateOf(Destination.PLAN) }
    var showLinesSheet by remember { mutableStateOf(false) }

    // Contextual sheet state — shown in a NON-blocking BottomSheetScaffold (map usable behind).
    var selectedLine by remember { mutableStateOf<LineInfo?>(null) }
    var lineDirection by remember { mutableIntStateOf(0) }
    var selectedStation by remember { mutableStateOf<StationInfo?>(null) }
    var allSchedules by remember { mutableStateOf<AllSchedulesInfo?>(null) }
    var showAddFavoriteDialog by remember { mutableStateOf(false) }

    val availableDirections by viewModel.availableDirections.collectAsState(initial = emptyList())
    val headsigns by viewModel.headsigns.collectAsState(initial = emptyMap())
    val linesUiState by viewModel.uiState.collectAsState()
    val stopsUiState by viewModel.stopsUiState.collectAsState()
    val userFavorites by viewModel.userFavorites.collectAsState(initial = emptyList())
    val stops = (stopsUiState as? TransportStopsUiState.Success)?.stops
    val selectedLineName = selectedLine?.lineName
    val planStops = remember(stops, selectedLineName) {
        if (selectedLineName.isNullOrBlank()) {
            stops
        } else {
            val normSelected = lineRules.normalizeForComparison(selectedLineName)
            stops?.filter { stop ->
                viewModel.parseLineCodesFromDesserte(stop.properties.desserte)
                    .any { lineRules.normalizeForComparison(it) == normSelected }
            }
        }
    }

    var userLocation by remember { mutableStateOf<Position?>(null) }
    val locationProvider = remember { LocationProvider(context) }
    DisposableEffect(Unit) {
        locationProvider.startUpdates { p -> userLocation = Position(latitude = p.latitude, longitude = p.longitude) }
        onDispose { locationProvider.stopUpdates() }
    }

    // Live vehicles
    val vehiclePositions by viewModel.vehiclePositions.collectAsState(initial = emptyList())
    val isGlobalLiveEnabled by viewModel.isGlobalLiveEnabled.collectAsState(initial = false)
    val isLiveTrackingEnabled by viewModel.isLiveTrackingEnabled.collectAsState(initial = false)
    val globalVehiclePositions by viewModel.globalVehiclePositions.collectAsState(initial = emptyList())

    LaunchedEffect(selectedLine?.lineName) {
        val ln = selectedLine?.lineName
        if (!ln.isNullOrBlank()) viewModel.startLiveTracking(ln) else viewModel.stopLiveTracking()
    }

    val activeVehiclePositions = remember(selectedLineName, isGlobalLiveEnabled, isLiveTrackingEnabled, vehiclePositions, globalVehiclePositions) {
        if (!selectedLineName.isNullOrBlank()) {
            if (isLiveTrackingEnabled) vehiclePositions else emptyList()
        } else {
            if (isGlobalLiveEnabled) globalVehiclePositions else emptyList()
        }
    }

    val vehiclesGeoJson = remember(activeVehiclePositions) {
        if (activeVehiclePositions.isEmpty()) null else toVehiclesGeoJson(activeVehiclePositions)
    }
    val vehicleIconName = remember(selectedLine?.lineName) {
        selectedLine?.lineName?.let { LineIconResolver.getDrawableNameForLineName(it) }
    }

    // Itinerary mode: two search fields at the top + a non-blocking results sheet.
    var itineraryActive by remember { mutableStateOf(false) }
    var itineraryDeparture by remember { mutableStateOf<SelectedStop?>(null) }
    var itineraryArrival by remember { mutableStateOf<SelectedStop?>(null) }
    var itineraryNearby by remember { mutableStateOf<List<String>>(emptyList()) }
    var itinerarySearchTarget by remember { mutableStateOf<ItineraryFieldTarget?>(null) }
    var itineraryArrivalSeed by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(itineraryArrivalSeed) {
        val arrivalName = itineraryArrivalSeed ?: return@LaunchedEffect
        runCatching { viewModel.raptorRepository.resolveStopIdsByName(arrivalName) }
            .getOrDefault(emptyList()).takeIf { it.isNotEmpty() }
            ?.let { itineraryArrival = SelectedStop(name = arrivalName, stopIds = it) }
        val loc = userLocation
        if (loc != null && itineraryDeparture == null) {
            val nearest = runCatching { viewModel.raptorRepository.findNearestStops(loc.latitude, loc.longitude, 5) }
                .getOrDefault(emptyList())
            val names = nearest.map { it.name }.distinct()
            itineraryNearby = names
            names.firstOrNull()?.let { depName ->
                runCatching { viewModel.raptorRepository.resolveStopIdsByName(depName) }
                    .getOrDefault(emptyList()).takeIf { it.isNotEmpty() }
                    ?.let { itineraryDeparture = SelectedStop(name = depName, stopIds = it) }
            }
        }
    }

    val closeSheet = { selectedStation = null; selectedLine = null; allSchedules = null }
    val closeItinerary = {
        itineraryActive = false
        itinerarySearchTarget = null
        itineraryArrival = null
        itineraryDeparture = null
        itineraryArrivalSeed = null
        itineraryNearby = emptyList()
        onNavigationModeChanged(false)
    }
    fun showStation(name: String, stopId: Int? = null, searchLines: List<String> = emptyList()) {
        val stop = stops?.firstOrNull { 
            (stopId != null && it.properties.id == stopId) || 
            it.properties.nom.equals(name, ignoreCase = true) 
        }
        selectedStation = if (stop != null) {
            val lines = (viewModel.parseLineCodesFromDesserte(stop.properties.desserte) + searchLines).distinct()
            StationInfo(stop.properties.nom, lines, stop.properties.desserte, listOf(stop.properties.id))
        } else {
            StationInfo(nom = name, lignes = searchLines.distinct())
        }
        selectedLine = null; allSchedules = null
    }
    fun showLine(name: String) {
        viewModel.selectLine(name); lineDirection = 0
        selectedLine = LineInfo(lineName = name, currentStationName = ""); selectedStation = null; allSchedules = null
    }
    fun showLineAtStation(lineName: String, stationName: String) {
        viewModel.selectLine(lineName); lineDirection = 0
        selectedLine = LineInfo(lineName = lineName, currentStationName = stationName); selectedStation = null; allSchedules = null
    }
    fun startItinerary(name: String) {
        closeSheet()
        itineraryArrival = null; itineraryDeparture = null
        itineraryArrivalSeed = name
        itineraryActive = true
    }

    val bottomSheetState = rememberStandardBottomSheetState(initialValue = SheetValue.Hidden, skipHiddenState = false)
    val bsScaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = bottomSheetState)
    val hasSheet = itineraryActive || selectedStation != null || selectedLine != null || allSchedules != null
    // Open the sheet at its content height (like PlanScreen's .expand()), not just the peek.
    val sheetContentKey = "$itineraryActive|${selectedStation?.nom}|${selectedLine?.lineName}|${allSchedules?.lineName}"
    LaunchedEffect(sheetContentKey) {
        if (hasSheet) bottomSheetState.expand() else bottomSheetState.hide()
    }
    LaunchedEffect(bottomSheetState.currentValue) {
        if (bottomSheetState.currentValue == SheetValue.Hidden) {
            closeSheet()
            if (itineraryActive) {
                closeItinerary()
            }
        }
    }

    // Cap the expanded sheet so its top stays just BELOW the top buttons (search/favorites),
    // and let shorter content size itself naturally so its scroll fills to the bottom.
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val screenHeightDp = with(density) { windowInfo.containerSize.height.toDp() }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val maxSheetHeight = (screenHeightDp - topInset - 220.dp).coerceAtLeast(320.dp)

    Box(Modifier.fillMaxSize()) {
        // Column instead of Scaffold(bottomBar) so the content area (and the BottomSheetScaffold's
        // sheet) is hard-constrained above the navbar — the sheet's bottom rests on the navbar top
        // instead of sliding behind it.
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
            if (selectedTab == Destination.SETTINGS) {
                SettingsTab(viewModel, Modifier.fillMaxSize()) { selectedTab = Destination.PLAN }
            } else {
                BottomSheetScaffold(
                    modifier = Modifier.fillMaxSize(),
                    scaffoldState = bsScaffoldState,
                    sheetPeekHeight = if (hasSheet) 360.dp else 0.dp,
                    sheetContent = {
                        // Cap the height so the sheet top stays below the search/favorites bar; shorter
                        // content keeps its natural height so its scroll fills to the bottom.
                        Box(Modifier.heightIn(max = maxSheetHeight)) {
                            val sc = allSchedules
                            val ln = selectedLine
                            val st = selectedStation
                            when {
                                itineraryActive -> InlineItinerarySheetContent(
                                    viewModel = viewModel,
                                    departureStop = itineraryDeparture,
                                    arrivalStop = itineraryArrival,
                                    maxHeight = 600.dp,
                                    nearbyDepartureStops = itineraryNearby,
                                    onDepartureFallbackSelected = { itineraryDeparture = it },
                                    onJourneysChanged = { },
                                    onSelectedJourneyChanged = { },
                                    onStartNavigation = {
                                        onNavigationModeChanged(true)
                                    },
                                    onClose = closeItinerary,
                                    onRequestExpandSheet = { },
                                )
                                sc != null -> AllSchedulesSheetContent(
                                    allSchedulesInfo = sc,
                                    stationName = selectedLine?.currentStationName ?: "",
                                    selectedDirection = lineDirection,
                                    availableDirections = availableDirections,
                                    headsigns = headsigns,
                                    onDirectionChange = { lineDirection = it },
                                    onBack = { allSchedules = null },
                                )
                                ln != null -> LineDetailsBottomSheet(
                                    viewModel = viewModel,
                                    lineInfo = ln,
                                    sheetState = null,
                                    selectedDirection = lineDirection,
                                    onDirectionChange = { lineDirection = it },
                                    onDismiss = closeSheet,
                                    onStopClick = { stopName -> selectedLine = selectedLine?.copy(currentStationName = stopName) },
                                    onBackToStation = {
                                        val s = selectedLine?.currentStationName
                                        if (!s.isNullOrBlank()) showStation(s) else closeSheet()
                                    },
                                    onShowAllSchedules = { lineName, directionName, schedules ->
                                        allSchedules = AllSchedulesInfo(lineName = lineName, directionName = directionName, schedules = schedules)
                                    },
                                    onItineraryClick = { name -> startItinerary(name) },
                                )
                                st != null -> StationSheetContent(
                                    stationInfo = st,
                                    viewModel = viewModel,
                                    onDismiss = closeSheet,
                                    onDepartureClick = { lineName, _, _ -> showLineAtStation(lineName, st.nom) },
                                    isFavoriteStop = userFavorites.any { it.stopName.equals(st.nom, ignoreCase = true) },
                                    onToggleFavoriteStop = {
                                        val existing = userFavorites.firstOrNull { it.stopName.equals(st.nom, ignoreCase = true) }
                                        if (existing != null) viewModel.removeUserFavorite(existing.id) else showAddFavoriteDialog = true
                                    },
                                    onAddFavoriteClick = { showAddFavoriteDialog = true },
                                    onItineraryClick = { stopName -> startItinerary(stopName) },
                                )
                            }
                        }
                    },
                ) {
                    val focusCenter: Position? = remember(selectedLine?.lineName, selectedStation?.nom, stops, linesUiState) {
                        val st = selectedStation
                        if (st != null) {
                            val stop = stops?.firstOrNull { it.properties.nom.equals(st.nom, ignoreCase = true) }
                            if (stop != null && stop.geometry.coordinates.size >= 2) {
                                return@remember Position(latitude = stop.geometry.coordinates[1], longitude = stop.geometry.coordinates[0])
                            }
                        }
                        val ln = selectedLine?.lineName
                        if (ln != null) {
                            val allLines = when (val s = linesUiState) {
                                is TransportLinesUiState.Success -> s.lines
                                is TransportLinesUiState.PartialSuccess -> s.lines
                                else -> null
                            }
                            val feat = allLines?.firstOrNull { it.properties.lineName.equals(ln, ignoreCase = true) }
                            if (feat != null) {
                                val points = feat.multiLineStringGeometry.coordinates.flatten()
                                if (points.isNotEmpty()) {
                                    return@remember Position(
                                        latitude = points.map { it[1] }.average(),
                                        longitude = points.map { it[0] }.average(),
                                    )
                                }
                            }
                        }
                        null
                    }

                    val focusZoom: Double? = remember(selectedLine?.lineName, selectedStation?.nom, stops, linesUiState) {
                        val st = selectedStation
                        if (st != null) {
                            return@remember 17.0
                        }
                        val ln = selectedLine?.lineName
                        if (ln != null) {
                            val allLines = when (val s = linesUiState) {
                                is TransportLinesUiState.Success -> s.lines
                                is TransportLinesUiState.PartialSuccess -> s.lines
                                else -> null
                            }
                            val feat = allLines?.firstOrNull { it.properties.lineName.equals(ln, ignoreCase = true) }
                            if (feat != null) {
                                val points = feat.multiLineStringGeometry.coordinates.flatten()
                                if (points.isNotEmpty()) {
                                    val lats = points.map { it[1] }
                                    val lons = points.map { it[0] }
                                    val latMin = lats.minOrNull() ?: 45.75
                                    val latMax = lats.maxOrNull() ?: 45.75
                                    val lonMin = lons.minOrNull() ?: 4.85
                                    val lonMax = lons.maxOrNull() ?: 4.85
                                    val latDiff = latMax - latMin
                                    val lonDiff = lonMax - lonMin
                                    val span = maxOf(latDiff, lonDiff)
                                    if (span > 0.0001) {
                                        val log2Val = kotlin.math.log2(360.0 / span)
                                        return@remember (log2Val - 1.2).coerceIn(9.5, 15.0)
                                    }
                                }
                            }
                        }
                        null
                    }

                    PlanContent(
                        viewModel = viewModel,
                        stops = planStops,
                        userLocation = userLocation,
                        userFavorites = userFavorites,
                        showTopBar = !itineraryActive,
                        vehiclesGeoJson = vehiclesGeoJson,
                        vehicleIconName = vehicleIconName,
                        focusCenter = focusCenter,
                        focusZoom = focusZoom,
                        selectedLineName = selectedLineName,
                        onStopSelected = { name, id, lines -> showStation(name, id, lines) },
                        onLineSelected = { showLine(it) },
                        onAddFavoriteClick = { showAddFavoriteDialog = true },
                        onItinerarySelected = { name -> startItinerary(name) },
                    )
                }
            }
            }
            NavigationBar(containerColor = PrimaryColor) {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = when (destination) {
                            Destination.LINES -> showLinesSheet
                            Destination.PLAN -> selectedTab == Destination.PLAN && !showLinesSheet
                            Destination.SETTINGS -> selectedTab == Destination.SETTINGS
                        },
                        onClick = {
                            when (destination) {
                                Destination.LINES -> { selectedTab = Destination.PLAN; showLinesSheet = true }
                                Destination.PLAN -> { selectedTab = Destination.PLAN; showLinesSheet = false }
                                Destination.SETTINGS -> { selectedTab = Destination.SETTINGS; showLinesSheet = false }
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.contentDescription) },
                        label = { Text(destination.label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = AccentColor,
                            selectedIconColor = SecondaryColor,
                            unselectedIconColor = SecondaryColor,
                            selectedTextColor = SecondaryColor,
                            unselectedTextColor = SecondaryColor,
                        ),
                    )
                }
            }
        }

        // Itinerary header: two stop fields (departure / arrival) + swap, replacing the search bar.
        if (itineraryActive) {
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = closeItinerary) {
                        Icon(Icons.Filled.Close, contentDescription = "Fermer l'itinéraire", tint = PrimaryColor)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ItinerarySearchBarField(
                            selectedStop = itineraryDeparture,
                            onClick = { itinerarySearchTarget = ItineraryFieldTarget.DEPARTURE },
                            icon = Icons.Filled.MyLocation,
                            placeholder = "Arrêt de départ",
                        )
                        ItinerarySearchBarField(
                            selectedStop = itineraryArrival,
                            onClick = { itinerarySearchTarget = ItineraryFieldTarget.ARRIVAL },
                            icon = Icons.Filled.Search,
                            placeholder = "Arrêt d'arrivée",
                        )
                    }
                    IconButton(onClick = {
                        val tmp = itineraryDeparture; itineraryDeparture = itineraryArrival; itineraryArrival = tmp
                    }) {
                        Icon(Icons.Filled.SwapVert, contentDescription = "Inverser", tint = PrimaryColor)
                    }
                }
            }
        }

        // Stop-search overlay shown when an itinerary field is being edited.
        itinerarySearchTarget?.let { target ->
            val isDeparture = target == ItineraryFieldTarget.DEPARTURE
            TransportSearchBar(
                onSearchStops = { q -> viewModel.searchStops(q) },
                onSearchLines = { emptyList() },
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
                content = TransportSearchContent.STOPS_ONLY,
                showHistory = false,
                startExpanded = true,
                searchPlaceholder = if (isDeparture) "Rechercher un départ" else "Rechercher une arrivée",
                onExpandedChange = { expanded -> if (!expanded) itinerarySearchTarget = null },
                onStopPrimary = { result ->
                    scope.launch {
                        val ids = runCatching { viewModel.raptorRepository.resolveStopIdsByName(result.stopName) }.getOrDefault(emptyList())
                        val sel = SelectedStop(name = result.stopName, stopIds = ids)
                        if (isDeparture) itineraryDeparture = sel else itineraryArrival = sel
                        itinerarySearchTarget = null
                    }
                },
            )
        }

        // Lignes: a blocking modal sheet over the map (the one sheet meant to be blocking).
        if (showLinesSheet) {
            val linesSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val allLines = remember(linesUiState, stopsUiState) { viewModel.getAllAvailableLines() }
            ModalBottomSheet(
                onDismissRequest = { showLinesSheet = false },
                containerColor = SecondaryColor,
                sheetState = linesSheetState,
            ) {
                LinesBottomSheet(
                    allLines = allLines,
                    onLineClick = { lineName -> showLinesSheet = false; showLine(lineName) },
                    viewModel = viewModel,
                )
            }
        }

        if (showAddFavoriteDialog) {
            AddFavoriteDialog(
                onDismiss = { showAddFavoriteDialog = false },
                onFavoriteCreated = { name, iconName, stopName ->
                    viewModel.addUserFavorite(name, iconName, stopName)
                    showAddFavoriteDialog = false
                },
                viewModel = viewModel,
            )
        }
    }
}

/**
 * Presentational map screen: the MapCanvas + the search/favorites/map-style overlay. Taps bubble
 * up to [RootScaffold] (which owns the sheets). [showTopBar] hides the search bar in itinerary mode.
 */
@Composable
private fun PlanContent(
    viewModel: TransportViewModel,
    stops: List<StopFeature>?,
    userLocation: Position?,
    userFavorites: List<Favorite>,
    showTopBar: Boolean,
    vehiclesGeoJson: String?,
    vehicleIconName: String?,
    focusCenter: Position?,
    focusZoom: Double?,
    selectedLineName: String?,
    onStopSelected: (String, Int?, List<String>) -> Unit,
    onLineSelected: (String) -> Unit,
    onAddFavoriteClick: () -> Unit,
    onItinerarySelected: (String) -> Unit,
) {
    val context = LocalPlatformContext.current
    val searchHistoryRepo = remember { SearchHistoryRepository(context) }
    var searchHistory by remember { mutableStateOf(searchHistoryRepo.getSearchHistory()) }
    val linesState by viewModel.uiState.collectAsState()
    val lineRules = remember { TransportServiceProvider.getTransportLineRules() }
    val mapStyleRepo = remember { MapStyleRepository(context, TransportServiceProvider.getMapStyleConfig()) }
    var selectedMapStyle by remember { mutableStateOf(mapStyleRepo.getSelectedStyle()) }
    var showStyleSheet by remember { mutableStateOf(false) }
    var searchExpanded by remember { mutableStateOf(false) }

    val isGlobalLiveEnabled by viewModel.isGlobalLiveEnabled.collectAsState(initial = false)
    val isLiveTrackingEnabled by viewModel.isLiveTrackingEnabled.collectAsState(initial = false)
    val vehiclePositions by viewModel.vehiclePositions.collectAsState(initial = emptyList())
    val globalVehiclePositions by viewModel.globalVehiclePositions.collectAsState(initial = emptyList())

    val isOffline by viewModel.isOffline.collectAsState()
    val offlineDataInfo by viewModel.offlineDataInfo.collectAsState()

    val allLines = when (val s = linesState) {
        is TransportLinesUiState.Success -> s.lines
        is TransportLinesUiState.PartialSuccess -> s.lines
        else -> null
    }
    val strongLines = allLines?.filter { lineRules.isStrongLine(it.properties.lineName) }
    val mapLines = remember(strongLines, selectedLineName, allLines) {
        if (allLines == null) return@remember null
        val strongs = strongLines ?: emptyList()
        val selected = if (!selectedLineName.isNullOrBlank()) {
            val normSelected = lineRules.normalizeForComparison(selectedLineName)
            allLines.firstOrNull { lineRules.normalizeForComparison(it.properties.lineName) == normSelected }
        } else null
        if (selected != null) {
            listOf(selected)
        } else {
            strongs
        }
    }

    Box(Modifier.fillMaxSize()) {
        MapCanvas(
            modifier = Modifier.fillMaxSize(),
            styleUrl = selectedMapStyle.styleUrl,
            initialLatitude = 45.75,
            initialLongitude = 4.85,
            initialZoom = 12.0,
            centerOn = focusCenter,
            focusZoom = focusZoom,
            lines = mapLines?.let { FeatureCollection(features = it) },
            stops = stops?.let { StopCollection(features = it) },
            userLocation = userLocation,
            vehiclesGeoJson = vehiclesGeoJson,
            vehicleIconName = vehicleIconName,
            selectedLineName = selectedLineName,
            onStopClick = { nom -> onStopSelected(nom, null, emptyList()) },
            onLineClick = { lineName -> onLineSelected(lineName) },
            onVehicleClick = { lineName -> onLineSelected(lineName) },
        )

        if (showTopBar) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .then(if (searchExpanded) Modifier.background(Color.Black) else Modifier)
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                Column {
                    TransportSearchBar(
                        onSearchStops = { q -> viewModel.searchStops(q) },
                        onSearchLines = { q -> viewModel.searchLines(q) },
                        onExpandedChange = { searchExpanded = it },
                        onStopPrimary = { result -> onStopSelected(result.stopName, result.stopId, result.lines) },
                        onStopSecondary = { result -> onItinerarySelected(result.stopName) },
                        onLineSelected = { line -> onLineSelected(line.lineName) },
                        searchHistory = searchHistory,
                        onAddToHistory = { item ->
                            searchHistoryRepo.addToHistory(item)
                            searchHistory = searchHistoryRepo.getSearchHistory()
                        },
                        onRemoveFromHistory = { query, type ->
                            searchHistoryRepo.removeFromHistory(query, type)
                            searchHistory = searchHistoryRepo.getSearchHistory()
                        },
                    )
                    if (!searchExpanded) {
                        FavoritesBar(
                            favorites = userFavorites,
                            onAddFavoriteClick = onAddFavoriteClick,
                            onFavoriteClick = { fav -> onStopSelected(fav.stopName, null, emptyList()) },
                            onRemoveFavoriteClick = { fav -> viewModel.removeUserFavorite(fav.id) },
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 0.dp, end = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val isLiveModeEnabled = isLiveTrackingEnabled || isGlobalLiveEnabled
                            val hasVehicles = when {
                                isLiveTrackingEnabled -> vehiclePositions.isNotEmpty()
                                isGlobalLiveEnabled -> globalVehiclePositions.isNotEmpty()
                                else -> false
                            }
                            val isActiveNoVehicles = isLiveModeEnabled && !hasVehicles

                            // Animation for the bouncing dot (goes up and down)
                            val infiniteTransition = rememberInfiniteTransition(label = "live_dot")
                            val dotOffset by infiniteTransition.animateFloat(
                                initialValue = if (hasVehicles) -2f else 0f,
                                targetValue = if (hasVehicles) 2f else 0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(400),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "dot_bounce"
                            )

                            val buttonColor = when {
                                hasVehicles -> Color(0xFFEF4444) // Red when active with vehicles
                                isActiveNoVehicles -> Color(0xFF9CA3AF) // Gray when active but no vehicles
                                else -> PrimaryColor // Black when inactive
                            }

                             Row(
                                 modifier = Modifier
                                     .shadow(4.dp, RoundedCornerShape(20.dp))
                                     .clip(RoundedCornerShape(20.dp))
                                     .background(PrimaryColor)
                                     .clickable { showStyleSheet = true }
                                     .height(40.dp)
                                     .padding(horizontal = 16.dp),
                                 verticalAlignment = Alignment.CenterVertically
                             ) {
                                 Icon(
                                     Icons.Filled.Layers,
                                     contentDescription = "Style de carte",
                                     tint = SecondaryColor,
                                     modifier = Modifier.size(20.dp)
                                 )
                             }

                             Row(
                                 modifier = Modifier
                                     .shadow(4.dp, RoundedCornerShape(20.dp))
                                     .clip(RoundedCornerShape(20.dp))
                                     .background(buttonColor)
                                     .clickable {
                                         if (isLiveModeEnabled) {
                                             if (isLiveTrackingEnabled) {
                                                 viewModel.stopLiveTracking()
                                             }
                                             if (isGlobalLiveEnabled) {
                                                 viewModel.stopGlobalLive()
                                             }
                                         } else {
                                             if (!selectedLineName.isNullOrBlank()) {
                                                 viewModel.startLiveTracking(selectedLineName)
                                             } else {
                                                 viewModel.toggleGlobalLive()
                                             }
                                         }
                                     }
                                     .height(40.dp)
                                     .padding(horizontal = 16.dp),
                                 verticalAlignment = Alignment.CenterVertically
                             ) {
                                 Canvas(
                                     modifier = Modifier
                                         .size(8.dp)
                                         .graphicsLayer { translationY = dotOffset }
                                 ) {
                                     drawCircle(color = SecondaryColor)
                                 }
                                 Spacer(modifier = Modifier.width(6.dp))
                                 Text(
                                     text = "LIVE",
                                     fontWeight = FontWeight.Bold,
                                     color = SecondaryColor,
                                     fontSize = 14.sp
                                 )
                             }
                        }
                    }
                }
            }
        }
    }

    if (showStyleSheet) {
        MapStyleSelectionSheet(
            isOffline = isOffline,
            downloadedMapStyles = offlineDataInfo.downloadedMapStyles,
            selectedMapStyle = selectedMapStyle,
            onDismiss = { showStyleSheet = false },
            onStyleSelected = { style ->
                selectedMapStyle = style
                mapStyleRepo.saveSelectedStyle(style)
                showStyleSheet = false
            },
        )
    }
}

@Composable
private fun SettingsTab(viewModel: TransportViewModel, modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalPlatformContext.current
    val scope = rememberCoroutineScope()
    var route by remember { mutableStateOf("root") }
    val backToRoot = { route = "root" }
    // Full-screen (no inset padding) so settings covers the whole screen, behind the notch too.
    Box(modifier) {
        when (route) {
            "legal" -> LegalScreen(
                legalSections = remember { AppConfigLoader.getConfig().about.legalSections },
                onBackClick = backToRoot,
            )
            "credits" -> CreditsScreen(onBackClick = backToRoot)
            "contact" -> ContactScreen(onBackClick = backToRoot)
            "offline" -> OfflineSettingsScreen(viewModel = viewModel, onBackClick = backToRoot)
            "itinerary" -> {
                val cfg = remember { AppConfigLoader.getConfig().itinerarySettings }
                val prefs = remember { ItineraryPreferencesRepository(context) }
                ItinerarySettingsScreen(
                    screenTitle = cfg.screenTitle,
                    sectionTitle = cfg.sectionTitle,
                    options = cfg.options,
                    onBackClick = backToRoot,
                    onOptionToggle = { key, enabled -> prefs.setOptionEnabled(key, enabled) },
                    getInitialOptionState = { opt -> prefs.isOptionEnabled(opt.key, opt.defaultEnabled) },
                )
            }
            "telemetry" -> TelemetrySettingsScreen(
                onBackClick = backToRoot,
                onShowCollectedData = { route = "telemetry_preview" },
                onWipeHistory = { scope.launch(ioDispatcher) { runCatching { LocalHistoryStorage(context).wipeAll() } } },
                onLegalClick = { route = "legal" },
                onFaqClick = { route = "telemetry_faq" },
            )
            "telemetry_preview" -> TelemetryPreviewScreen(
                snapshot = TelemetryEmitter.repository()?.state?.value,
                onBackClick = { route = "telemetry" },
            )
            "telemetry_faq" -> TelemetryFaqScreen(
                entries = TelemetryEmitter.config()?.disclosure?.faq.orEmpty(),
                onBackClick = { route = "telemetry" },
            )
            else -> SettingsScreen(
                versionName = appVersionName(context),
                onBackClick = onBack,
                onItineraryClick = { route = "itinerary" },
                onLegalClick = { route = "legal" },
                onCreditsClick = { route = "credits" },
                onContactClick = { route = "contact" },
                onOfflineClick = { route = "offline" },
                onTelemetryClick = { route = "telemetry" },
            )
        }
    }
}
