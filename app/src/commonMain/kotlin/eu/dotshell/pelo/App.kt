@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package eu.dotshell.pelo

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
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Navigation
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.camera.CameraPosition
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
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import eu.dotshell.pelo.generic.data.config.AppConfigLoader
import eu.dotshell.pelo.generic.data.models.geojson.FeatureCollection
import eu.dotshell.pelo.generic.data.models.geojson.StopCollection
import eu.dotshell.pelo.generic.data.models.geojson.StopFeature
import eu.dotshell.pelo.generic.data.models.itinerary.ItineraryFieldTarget
import eu.dotshell.pelo.generic.data.models.itinerary.SelectedStop
import eu.dotshell.pelo.generic.data.models.search.TransportSearchContent
import eu.dotshell.pelo.generic.data.models.stops.Favorite
import eu.dotshell.pelo.generic.data.models.stops.StationInfo
import eu.dotshell.pelo.generic.data.models.ui.AllSchedulesInfo
import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.ItineraryPreferencesRepository
import eu.dotshell.pelo.generic.data.repository.offline.mapstyle.MapStyleRepository
import eu.dotshell.pelo.generic.service.TransportServiceProvider
import eu.dotshell.pelo.generic.utils.graphics.LineIconResolver
import eu.dotshell.pelo.generic.utils.map.toVehiclesGeoJson
import eu.dotshell.pelo.generic.utils.map.toItinerariesGeoJson
import eu.dotshell.pelo.generic.utils.map.calculateJourneyTrace
import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.JourneyResult
import eu.dotshell.pelo.generic.ui.components.MapCanvas
import eu.dotshell.pelo.generic.ui.components.favorites.AddFavoriteDialog
import eu.dotshell.pelo.generic.ui.components.favorites.FavoritesBar
import eu.dotshell.pelo.generic.ui.components.search.TransportSearchBar
import eu.dotshell.pelo.generic.data.repository.offline.search.SearchHistoryRepository
import eu.dotshell.pelo.generic.data.repository.offline.search.SearchHistoryItem
import eu.dotshell.pelo.generic.data.repository.offline.search.SearchType
import eu.dotshell.pelo.generic.ui.screens.Destination
import eu.dotshell.pelo.generic.ui.screens.plan.AllSchedulesSheetContent
import eu.dotshell.pelo.generic.ui.screens.plan.LineDetailsBottomSheet
import eu.dotshell.pelo.generic.ui.screens.plan.LineInfo
import eu.dotshell.pelo.generic.ui.screens.plan.LinesBottomSheet
import eu.dotshell.pelo.generic.ui.screens.plan.AlertReportBottomSheet
import eu.dotshell.pelo.generic.ui.screens.plan.MapStyleSelectionSheet
import eu.dotshell.pelo.generic.ui.screens.plan.StationSheetContent
import eu.dotshell.pelo.generic.ui.screens.plan.itinerary.InlineItinerarySheetContent
import eu.dotshell.pelo.generic.ui.screens.plan.itinerary.ItinerarySearchBarField
import eu.dotshell.pelo.generic.data.local_history.LocalHistoryStorage
import eu.dotshell.pelo.generic.data.telemetry.TelemetryEmitter
import eu.dotshell.pelo.platform.Settings
import eu.dotshell.pelo.generic.ui.screens.settings.ItinerarySettingsScreen
import eu.dotshell.pelo.generic.ui.screens.settings.OfflineSettingsScreen
import eu.dotshell.pelo.generic.ui.screens.settings.SettingsScreen
import eu.dotshell.pelo.generic.ui.screens.settings.TelemetrySettingsScreen
import eu.dotshell.pelo.generic.ui.screens.onboarding.TermsConsentGate
import eu.dotshell.pelo.generic.ui.screens.onboarding.TelemetryOptInGate
import eu.dotshell.pelo.generic.ui.screens.settings.about.ContactScreen
import eu.dotshell.pelo.generic.ui.screens.settings.about.CreditsScreen
import eu.dotshell.pelo.generic.ui.screens.settings.about.LegalScreen
import eu.dotshell.pelo.generic.ui.theme.AccentColor
import eu.dotshell.pelo.generic.ui.theme.PeloTheme
import eu.dotshell.pelo.generic.ui.theme.PrimaryColor
import eu.dotshell.pelo.generic.ui.theme.SecondaryColor
import eu.dotshell.pelo.generic.ui.viewmodel.TransportLinesUiState
import eu.dotshell.pelo.generic.ui.viewmodel.TransportStopsUiState
import eu.dotshell.pelo.generic.ui.viewmodel.TransportViewModel
import eu.dotshell.pelo.generic.ui.viewmodel.findStopByCoordinates
import eu.dotshell.pelo.generic.utils.location.GeoPoint
import eu.dotshell.pelo.generic.utils.location.LocationProvider
import eu.dotshell.pelo.generic.service.NavigationModeController
import eu.dotshell.pelo.generic.service.NavigationModeUiState
import eu.dotshell.pelo.generic.ui.screens.plan.NavigationModeOverlay
import eu.dotshell.pelo.generic.ui.screens.plan.buildNavigationModeUiState
import eu.dotshell.pelo.generic.utils.geo.GeometryUtils
import eu.dotshell.pelo.platform.DrawableProvider
import eu.dotshell.pelo.platform.BackHandler
import eu.dotshell.pelo.platform.LocalPlatformContext
import eu.dotshell.pelo.platform.Log
import eu.dotshell.pelo.platform.appVersionName
import eu.dotshell.pelo.platform.ioDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.spatialk.geojson.Position

@Composable
fun App(onNavigationModeChanged: (Boolean) -> Unit = {}) {
    val context = LocalPlatformContext.current
    var viewModel by remember { mutableStateOf<TransportViewModel?>(null) }
    var isInitializing by remember { mutableStateOf(true) }

    LaunchedEffect(context) {
        Log.i("PeloApp", "LaunchedEffect: starting init")
        launch(ioDispatcher) {
            Log.i("PeloApp", "ioDispatcher: start")
            try {
                TransportServiceProvider.initialize(context)
                Log.i("PeloApp", "TransportProvider init done")
                val vm = TransportViewModel(context)
                Log.i("PeloApp", "TransportViewModel constructor done")
                withContext(Dispatchers.Main) {
                    viewModel = vm
                    isInitializing = false
                    Log.i("PeloApp", "viewModel set on Main")
                }
                Log.i("PeloApp", "before raptor init")
                runCatching { vm.raptorRepository.initialize() }
                    .onSuccess { Log.i("PeloApp", "Raptor initialized") }
                    .onFailure { Log.e("PeloApp", "Raptor init failed: ${it.message}") }
                Log.i("PeloApp", "ioDispatcher: done")
            } catch (t: Throwable) {
                Log.e("PeloApp", "Transport data init failed: ${t.message}")
                withContext(Dispatchers.Main) {
                    isInitializing = false
                }
            }
        }
    }

    PeloTheme {
        TermsConsentGate {
            TelemetryOptInGate {
                Box(Modifier.fillMaxSize()) {
                    val vm = viewModel
                    if (vm != null) {
                        RootScaffold(vm, onNavigationModeChanged)
                    } else {
                        Box(Modifier.fillMaxSize().background(Color.White))
                    }

                    if (isInitializing) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .background(
                                    Color.White.copy(alpha = 0.85f),
                                    RoundedCornerShape(999.dp)
                                )
                                .padding(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = PrimaryColor
                            )
                        }
                    }
                }
            }
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

    var selectedLine by remember { mutableStateOf<LineInfo?>(null) }
    var lineDirection by remember { mutableIntStateOf(0) }
    var selectedStation by remember { mutableStateOf<StationInfo?>(null) }
    var allSchedules by remember { mutableStateOf<AllSchedulesInfo?>(null) }
    var showAddFavoriteDialog by remember { mutableStateOf(false) }
    var addFavoriteInitialStopName by remember { mutableStateOf<String?>(null) }

    var showAlertReport by remember { mutableStateOf(false) }
    var alertReportInitialStopName by remember { mutableStateOf<String?>(null) }
    var alertReportInitialLines by remember { mutableStateOf<List<String>>(emptyList()) }

    var isCenteredOnUser by remember { mutableStateOf(false) }
    var manualFocusCenter by remember { mutableStateOf<Position?>(null) }
    var manualFocusZoom by remember { mutableStateOf<Double?>(null) }
    var hasFocusedOnLive by remember { mutableStateOf(false) }

    val availableDirections by viewModel.availableDirections.collectAsState(initial = emptyList())
    val headsigns by viewModel.headsigns.collectAsState(initial = emptyMap())
    val linesUiState by viewModel.uiState.collectAsState()
    val stopsUiState by viewModel.stopsUiState.collectAsState()
    val userFavorites by viewModel.userFavorites.collectAsState(initial = emptyList())
    val stops = (stopsUiState as? TransportStopsUiState.Success)?.stops
    val selectedLineName = selectedLine?.lineName
    
    var userLocation by remember { mutableStateOf<Position?>(null) }
    var hasCenteredInitially by remember { mutableStateOf(false) }
    val locationProvider = remember { LocationProvider(context) }
    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(
            target = org.maplibre.spatialk.geojson.Position(latitude = 45.75, longitude = 4.85),
            zoom = 12.0,
            bearing = 0.0
        )
    )
    val navigationController = remember(context) { NavigationModeController(context) }
    val navigationState by navigationController.uiState.collectAsState()
    DisposableEffect(locationProvider, navigationController) {
        locationProvider.startUpdates { p ->
            userLocation = Position(latitude = p.latitude, longitude = p.longitude)
            navigationController.onLocationFix(GeoPoint(latitude = p.latitude, longitude = p.longitude))
        }
        onDispose {
            locationProvider.stopUpdates()
            navigationController.dispose()
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == Destination.SETTINGS) {
            hasCenteredInitially = false
        } else if (selectedTab == Destination.PLAN) {
            val loc = userLocation
            if (loc != null) {
                cameraState.position = CameraPosition(
                    target = org.maplibre.spatialk.geojson.Position(latitude = loc.latitude, longitude = loc.longitude),
                    zoom = 18.0,
                    bearing = 0.0,
                    tilt = 0.0
                )
                isCenteredOnUser = true
                hasCenteredInitially = true
            } else {
                cameraState.position = CameraPosition(
                    target = cameraState.position.target,
                    zoom = cameraState.position.zoom,
                    bearing = 0.0,
                    tilt = 0.0
                )
            }
        }
    }

    LaunchedEffect(selectedTab, userLocation) {
        val loc = userLocation
        if (loc != null && selectedTab == Destination.PLAN && !hasCenteredInitially) {
            isCenteredOnUser = true
            hasCenteredInitially = true
        }
    }

    val vehiclePositions by viewModel.vehiclePositions.collectAsState(initial = emptyList())
    val isGlobalLiveEnabled by viewModel.isGlobalLiveEnabled.collectAsState(initial = false)
    val isLiveTrackingEnabled by viewModel.isLiveTrackingEnabled.collectAsState(initial = false)
    val globalVehiclePositions by viewModel.globalVehiclePositions.collectAsState(initial = emptyList())

    LaunchedEffect(selectedLine?.lineName) {
        val ln = selectedLine?.lineName
        if (!ln.isNullOrBlank()) {
            if (!lineRules.isLiveTrackableLine(ln)) {
                if (isLiveTrackingEnabled) viewModel.stopLiveTracking()
                if (isGlobalLiveEnabled) viewModel.stopGlobalLive()
            } else {
                if (isLiveTrackingEnabled) {
                    viewModel.startLiveTracking(ln)
                } else if (isGlobalLiveEnabled) {
                    viewModel.stopGlobalLive()
                    viewModel.startLiveTracking(ln)
                }
            }
        } else {
            if (isLiveTrackingEnabled) {
                viewModel.stopLiveTracking()
                viewModel.toggleGlobalLive()
            }
        }
    }

    val activeVehiclePositions = remember(selectedLineName, isGlobalLiveEnabled, isLiveTrackingEnabled, vehiclePositions, globalVehiclePositions) {
        if (!selectedLineName.isNullOrBlank()) {
            if (isLiveTrackingEnabled) vehiclePositions else emptyList()
        } else {
            if (isGlobalLiveEnabled) globalVehiclePositions else emptyList()
        }
    }

    LaunchedEffect(isGlobalLiveEnabled, isLiveTrackingEnabled) {
        if (!isGlobalLiveEnabled && !isLiveTrackingEnabled) {
            hasFocusedOnLive = false
            manualFocusCenter = null
            manualFocusZoom = null
        }
    }



    LaunchedEffect(isGlobalLiveEnabled, isLiveTrackingEnabled, activeVehiclePositions) {
        if ((isGlobalLiveEnabled || isLiveTrackingEnabled) && !hasFocusedOnLive && activeVehiclePositions.isNotEmpty()) {
            manualFocusZoom = 13.0
            hasFocusedOnLive = true
        }
    }

    val vehiclesGeoJson = remember(activeVehiclePositions) {
        if (activeVehiclePositions.isEmpty()) null else toVehiclesGeoJson(activeVehiclePositions)
    }
    val vehicleIconName = remember(selectedLine?.lineName) {
        selectedLine?.lineName?.let { LineIconResolver.getDrawableNameForLineName(it) }
    }

    var itineraryActive by remember { mutableStateOf(false) }
    var activeJourneys by remember { mutableStateOf<List<JourneyResult>>(emptyList()) }
    var selectedJourney by remember { mutableStateOf<JourneyResult?>(null) }

    LaunchedEffect(itineraryActive) {
        if (itineraryActive) {
            if (isLiveTrackingEnabled) viewModel.stopLiveTracking()
            if (isGlobalLiveEnabled) viewModel.stopGlobalLive()
        }
    }
    val itineraryGeoJson by produceState<String?>(
        initialValue = null,
        key1 = activeJourneys,
        key2 = selectedJourney
    ) {
        value = if (activeJourneys.isNotEmpty()) {
            withContext(Dispatchers.Default) {
                toItinerariesGeoJson(activeJourneys, selectedJourney, viewModel)
            }
        } else {
            null
        }
    }
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

    val fabDrawableProvider = DrawableProvider(LocalPlatformContext.current)

    var filteredStopsCollection by remember { mutableStateOf<StopCollection?>(null) }
    LaunchedEffect(stops, selectedLineName, itineraryActive, activeJourneys, selectedJourney) {
        if (stops == null) {
            filteredStopsCollection = null
        } else {
            val collection = withContext(Dispatchers.Default) {
                val lineRules = TransportServiceProvider.getTransportLineRules()
                val finalStops = if (itineraryActive && activeJourneys.isNotEmpty()) {
                    val journeysToDraw = selectedJourney?.let { listOf(it) } ?: activeJourneys
                    val matchedStops = mutableSetOf<StopFeature>()
                    for (journey in journeysToDraw) {
                        for (leg in journey.legs) {
                            val fromStop = stops.find { it.properties.id.toString() == leg.fromStopId }
                                ?: findStopByCoordinates(stops, leg.fromLat, leg.fromLon)
                            if (fromStop != null) {
                                matchedStops.add(fromStop)
                            }
                            val toStop = stops.find { it.properties.id.toString() == leg.toStopId }
                                ?: findStopByCoordinates(stops, leg.toLat, leg.toLon)
                            if (toStop != null) {
                                matchedStops.add(toStop)
                            }
                        }
                    }
                    matchedStops.toList()
                } else if (selectedLineName.isNullOrBlank()) {
                    stops
                } else {
                    val normSelected = lineRules.normalizeForComparison(selectedLineName)
                    stops.filter { stop ->
                        val desserte = stop.properties.desserte
                        if (desserte.isBlank()) return@filter false
                        viewModel.parseLineCodesFromDesserte(desserte)
                            .any { lineRules.normalizeForComparison(it) == normSelected }
                    }
                }
                StopCollection(features = finalStops)
            }
            Log.i("PeloApp", "filteredStopsCollection: before setting on Main")
            filteredStopsCollection = collection
            Log.i("PeloApp", "filteredStopsCollection: set on Main (size=${collection.features.size})")
        }
    }

    val closeSheet = { selectedStation = null; selectedLine = null; allSchedules = null }
    LaunchedEffect(selectedStation?.nom, selectedLine?.lineName) {
        if (selectedStation != null || selectedLine != null) isCenteredOnUser = false
    }
    val closeItinerary = {
        itineraryActive = false
        itinerarySearchTarget = null
        itineraryArrival = null
        itineraryDeparture = null
        itineraryArrivalSeed = null
        itineraryNearby = emptyList()
        activeJourneys = emptyList()
        selectedJourney = null
        onNavigationModeChanged(false)
    }
    fun showStation(name: String, stopId: Int? = null, searchLines: List<String> = emptyList()) {
        closeItinerary()
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
        closeItinerary()
        viewModel.selectLine(name); lineDirection = 0
        selectedLine = LineInfo(lineName = name, currentStationName = ""); selectedStation = null; allSchedules = null
    }
    fun showLineAtStation(lineName: String, stationName: String) {
        closeItinerary()
        viewModel.selectLine(lineName); lineDirection = 0
        selectedLine = LineInfo(lineName = lineName, currentStationName = stationName); selectedStation = null; allSchedules = null
    }
    fun startItinerary(name: String) {
        closeSheet()
        itineraryArrival = null; itineraryDeparture = null
        itineraryArrivalSeed = name
        itineraryActive = true
    }

    LaunchedEffect(selectedStation?.nom, stops) {
        val stName = selectedStation?.nom
        if (!stName.isNullOrBlank() && stops != null) {
            val stop = stops.firstOrNull { it.properties.nom.equals(stName, ignoreCase = true) }
            if (stop != null && stop.geometry.coordinates.size >= 2) {
                manualFocusCenter = Position(latitude = stop.geometry.coordinates[1], longitude = stop.geometry.coordinates[0])
                manualFocusZoom = 18.0
            }
        }
    }

    LaunchedEffect(selectedLine?.lineName, linesUiState) {
        val ln = selectedLine?.lineName
        if (!ln.isNullOrBlank()) {
            val allLines = when (val s = linesUiState) {
                is TransportLinesUiState.Success -> s.lines
                is TransportLinesUiState.PartialSuccess -> s.lines
                else -> null
            }
            val feat = allLines?.firstOrNull { it.properties.lineName.equals(ln, ignoreCase = true) }
            if (feat != null) {
                val points = feat.multiLineStringGeometry.coordinates.flatten()
                if (points.isNotEmpty()) {
                    manualFocusCenter = Position(
                        latitude = points.map { it[1] }.average(),
                        longitude = points.map { it[0] }.average(),
                    )
                    val lats = points.map { it[1] }
                    val lons = points.map { it[0] }
                    val latMin = lats.minOrNull() ?: 45.75
                    val latMax = lats.maxOrNull() ?: 45.75
                    val lonMin = lons.minOrNull() ?: 4.85
                    val lonMax = lons.maxOrNull() ?: 4.85
                    val latDiff = latMax - latMin
                    val lonDiff = lonMax - lonMin
                    val span = maxOf(latDiff, lonDiff)
                    manualFocusZoom = if (span > 0.0001) {
                        val log2Val = kotlin.math.log2(360.0 / span)
                        (log2Val - 1.2).coerceIn(9.5, 15.0)
                    } else {
                        12.0
                    }
                }
            }
        }
    }

    LaunchedEffect(itineraryActive, activeJourneys, selectedJourney) {
        if (itineraryActive && activeJourneys.isNotEmpty()) {
            val journeysToDraw = selectedJourney?.let { listOf(it) } ?: activeJourneys
            val lats = mutableListOf<Double>()
            val lons = mutableListOf<Double>()
            for (journey in journeysToDraw) {
                for (leg in journey.legs) {
                    lats.add(leg.fromLat)
                    lons.add(leg.fromLon)
                    lats.add(leg.toLat)
                    lons.add(leg.toLon)
                    for (stop in leg.intermediateStops) {
                        lats.add(stop.lat)
                        lons.add(stop.lon)
                    }
                }
            }
            if (lats.isNotEmpty()) {
                manualFocusCenter = Position(latitude = lats.average(), longitude = lons.average())
                val latMin = lats.minOrNull() ?: 45.75
                val latMax = lats.maxOrNull() ?: 45.75
                val lonMin = lons.minOrNull() ?: 4.85
                val lonMax = lons.maxOrNull() ?: 4.85
                val latDiff = latMax - latMin
                val lonDiff = lonMax - lonMin
                val span = maxOf(latDiff, lonDiff)
                manualFocusZoom = if (span > 0.0001) {
                    val log2Val = kotlin.math.log2(360.0 / span)
                    (log2Val - 1.2).coerceIn(9.5, 15.0)
                } else {
                    12.0
                }
            }
        }
    }

    LaunchedEffect(navigationState.isActive) {
        if (!navigationState.isActive) {
            cameraState.animateTo(
                CameraPosition(
                    target = cameraState.position.target,
                    zoom = cameraState.position.zoom,
                    bearing = 0.0,
                    tilt = 0.0
                )
            )
        }
    }

    val bottomSheetState = rememberStandardBottomSheetState(initialValue = SheetValue.Hidden, skipHiddenState = false)
    val bsScaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = bottomSheetState)
    val hasSheet = !navigationState.isActive && (itineraryActive || selectedStation != null || selectedLine != null || allSchedules != null)
    val sheetContentKey = "${navigationState.isActive}|$itineraryActive|${selectedStation?.nom}|${selectedLine?.lineName}|${allSchedules?.lineName}"
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

    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val screenHeightDp = with(density) { windowInfo.containerSize.height.toDp() }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topMargin = if (itineraryActive) 290.dp else 320.dp
    val maxSheetHeight = minOf(700.dp, screenHeightDp - topInset - topMargin).coerceAtLeast(130.dp)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (selectedTab == Destination.SETTINGS) {
                    SettingsTab(viewModel, Modifier.fillMaxSize()) { selectedTab = Destination.PLAN }
                } else {
                    val focusCenter = remember(isCenteredOnUser, userLocation, manualFocusCenter, navigationState.isActive) {
                        if (navigationState.isActive && userLocation != null) return@remember userLocation
                        if (isCenteredOnUser && userLocation != null) return@remember userLocation
                        manualFocusCenter
                    }
                    val focusZoom = remember(isCenteredOnUser, manualFocusZoom, navigationState.isActive) {
                        if (navigationState.isActive) return@remember 18.5
                        if (isCenteredOnUser) return@remember 18.0
                        manualFocusZoom
                    }

                    PlanContent(
                        viewModel = viewModel,
                        stops = filteredStopsCollection?.features,
                        userLocation = userLocation,
                        userFavorites = userFavorites,
                        showTopBar = !itineraryActive && !navigationState.isActive,
                        vehiclesGeoJson = vehiclesGeoJson,
                        vehicleIconName = vehicleIconName,
                        focusCenter = focusCenter,
                        focusZoom = focusZoom,
                        cameraState = cameraState,
                        selectedLineName = selectedLine?.lineName,
                        itineraryGeoJson = itineraryGeoJson,
                        filteredStopsCollection = filteredStopsCollection,
                        fabDrawableProvider = fabDrawableProvider,
                        onStopSelected = { nom, id, lns -> showStation(nom, id, lns) },
                        onLineSelected = { name -> showLine(name) },
                        onAddFavoriteClick = {
                            addFavoriteInitialStopName = null
                            showAddFavoriteDialog = true
                        },
                        onItinerarySelected = { name -> startItinerary(name) },
                        isCenteredOnUser = isCenteredOnUser,
                        onFabClick = { isAtTarget ->
                            if (isAtTarget) {
                                alertReportInitialStopName = null
                                alertReportInitialLines = emptyList()
                                showAlertReport = true
                            } else {
                                val loc = userLocation
                                if (loc != null) {
                                    isCenteredOnUser = true
                                    hasCenteredInitially = true
                                }
                            }
                        },
                        onFabReset = {
                            isCenteredOnUser = false
                            manualFocusCenter = null
                            manualFocusZoom = null
                        },
                        showAlertReport = showAlertReport,
                        bsScaffoldState = bsScaffoldState,
                        sheetPeekHeight = if (hasSheet) 130.dp else 0.dp,
                        navigationState = navigationState,
                        sheetContent = {
                            Box(Modifier.heightIn(max = maxSheetHeight)) {
                                val sc = allSchedules
                                val ln = selectedLine
                                val st = selectedStation
                                when {
                                    itineraryActive -> InlineItinerarySheetContent(
                                        viewModel = viewModel,
                                        departureStop = itineraryDeparture,
                                        arrivalStop = itineraryArrival,
                                        maxHeight = maxSheetHeight,
                                        nearbyDepartureStops = itineraryNearby,
                                        onDepartureFallbackSelected = { itineraryDeparture = it },
                                        onJourneysChanged = { activeJourneys = it },
                                        onSelectedJourneyChanged = { selectedJourney = it },
                                        onStartNavigation = { journey ->
                                             val vm = viewModel
                                             if (vm != null) {
                                                 scope.launch {
                                                     val tracePoints = calculateJourneyTrace(journey, vm)
                                                     navigationController.start(journey, tracePoints)
                                                     onNavigationModeChanged(true)
                                                 }
                                             } else {
                                                 navigationController.start(journey)
                                                 onNavigationModeChanged(true)
                                             }
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
                                            if (existing != null) {
                                                viewModel.removeUserFavorite(existing.id)
                                            } else {
                                                addFavoriteInitialStopName = st.nom
                                                showAddFavoriteDialog = true
                                            }
                                        },
                                        onAddFavoriteClick = { stopName ->
                                            addFavoriteInitialStopName = stopName
                                            showAddFavoriteDialog = true
                                        },
                                        onItineraryClick = { stopName -> startItinerary(stopName) },
                                        onReportAlertClick = { stopName, lines ->
                                            alertReportInitialStopName = stopName
                                            alertReportInitialLines = lines
                                            showAlertReport = true
                                        },
                                    )
                                }
                            }
                        }
                    )
                }
            }

            if (!navigationState.isActive) {
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
                                    Destination.SETTINGS -> {
                                        selectedTab = Destination.SETTINGS
                                        showLinesSheet = false
                                        itinerarySearchTarget = null
                                    }
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
        }

        if (itineraryActive && selectedTab != Destination.SETTINGS && !navigationState.isActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
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
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .shadow(4.dp, CircleShape)
                        .background(Color.White, CircleShape)
                        .clickable {
                            val tmp = itineraryDeparture; itineraryDeparture = itineraryArrival; itineraryArrival = tmp
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.SwapVert,
                        contentDescription = "Inverser",
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

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
                initialStopName = addFavoriteInitialStopName,
            )
        }

        if (showAlertReport) {
            val userPos = userLocation
            val nearestStopCandidate = filteredStopsCollection?.features?.minByOrNull { stop ->
                val coords = stop.geometry.coordinates
                if (userPos != null && coords.size >= 2) {
                    val dx = coords[0].toDouble() - userPos.longitude
                    val dy = coords[1].toDouble() - userPos.latitude
                    dx * dx + dy * dy
                } else Double.MAX_VALUE
            }?.let { stop ->
                eu.dotshell.pelo.generic.data.models.search.StationSearchResult(
                    stopName = stop.properties.nom,
                    stopId = stop.properties.id,
                    lines = stop.properties.desserte.split(":")
                )
            }

            val initialStop = alertReportInitialStopName?.let { name ->
                eu.dotshell.pelo.generic.data.models.search.StationSearchResult(
                    stopName = name,
                    stopId = null,
                    lines = alertReportInitialLines
                )
            }
            AlertReportBottomSheet(
                viewModel = viewModel,
                onDismiss = { showAlertReport = false },
                initialStop = initialStop,
                nearestStopCandidate = nearestStopCandidate
            )
        }

        // Navigation mode overlay - displayed when navigation is active
        if (navigationState.isActive) {
            val loc = userLocation
            if (loc != null) {
                val overlayState = buildNavigationModeUiState(
                    journey = navigationState.journey!!,
                    nowSeconds = GeometryUtils.currentTimeInSeconds(),
                    userLocation = GeoPoint(latitude = loc.latitude, longitude = loc.longitude)
                )
                NavigationModeOverlay(
                    state = overlayState,
                    onClose = {
                        navigationController.stop()
                        onNavigationModeChanged(false)
                    },
                    onReportAlert = {
                        val nearestStop = filteredStopsCollection?.features?.minByOrNull { stop ->
                            val coords = stop.geometry.coordinates
                            val userPos = userLocation
                            if (userPos != null && coords.size >= 2) {
                                val dx = coords[0].toDouble() - userPos.longitude
                                val dy = coords[1].toDouble() - userPos.latitude
                                dx * dx + dy * dy
                            } else Double.MAX_VALUE
                        }
                        if (nearestStop != null) {
                            alertReportInitialStopName = nearestStop.properties.nom
                            alertReportInitialLines = nearestStop.properties.desserte.split(":")
                            showAlertReport = true
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

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
    itineraryGeoJson: String?,
    filteredStopsCollection: StopCollection?,
    fabDrawableProvider: DrawableProvider,
    onStopSelected: (String, Int?, List<String>) -> Unit,
    onLineSelected: (String) -> Unit,
    onAddFavoriteClick: () -> Unit,
    onItinerarySelected: (String) -> Unit,
    isCenteredOnUser: Boolean,
    onFabClick: (Boolean) -> Unit,
    onFabReset: () -> Unit,
    showAlertReport: Boolean,
    bsScaffoldState: BottomSheetScaffoldState,
    sheetPeekHeight: Dp,
    sheetContent: @Composable () -> Unit,
    navigationState: NavigationModeUiState,
    cameraState: CameraState,
) {
    val context = LocalPlatformContext.current
    val scope = rememberCoroutineScope()
    var isMapRotated by remember { mutableStateOf(false) }
    LaunchedEffect(cameraState) {
        snapshotFlow { kotlin.math.abs(cameraState.position.bearing) > 1.0 || cameraState.position.tilt > 1.0 }
            .distinctUntilChanged()
            .collect { isMapRotated = it }
    }
    var isAtCenteringTarget by remember { mutableStateOf(false) }
    LaunchedEffect(cameraState, userLocation) {
        snapshotFlow {
            val loc = userLocation
            if (loc != null) {
                val deltaLat = cameraState.position.target.latitude - loc.latitude
                val deltaLon = cameraState.position.target.longitude - loc.longitude
                val dx = deltaLon * 77500.0
                val dy = deltaLat * 111000.0
                val isNear = (dx * dx + dy * dy) < 400.0 // less than 20 meters (20^2 = 400)
                val isZoomed = cameraState.position.zoom >= 17.0
                isNear && isZoomed
            } else {
                false
            }
        }
        .distinctUntilChanged()
        .collect { isAtCenteringTarget = it }
    }
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
        BottomSheetScaffold(
            modifier = Modifier.fillMaxSize(),
            scaffoldState = bsScaffoldState,
            sheetPeekHeight = sheetPeekHeight,
            sheetContent = {
                sheetContent()
            }
        ) {
            Box(Modifier.fillMaxSize()) {
                Log.i("PeloApp", "PlanContent: before MapCanvas")
                MapCanvas(
                    modifier = Modifier.fillMaxSize(),
                    styleUrl = selectedMapStyle.styleUrl,
                    initialLatitude = 45.75,
                    initialLongitude = 4.85,
                    initialZoom = 12.0,
                    centerOn = focusCenter,
                    focusZoom = focusZoom,
                    cameraState = cameraState,
                    bearing = if (navigationState.isActive) navigationState.bearing else (if (isCenteredOnUser) 0.0 else null),
                    lines = mapLines?.let { FeatureCollection(features = it) },
                    stops = filteredStopsCollection,
                    userLocation = userLocation,
                    vehiclesGeoJson = vehiclesGeoJson,
                    vehicleIconName = vehicleIconName,
                    selectedLineName = selectedLineName,
                    itineraryGeoJson = itineraryGeoJson,
                    interactive = !navigationState.isActive,
                    tilt = if (navigationState.isActive) 55.0 else (if (isCenteredOnUser) 0.0 else null),
                    onStopClick = { nom -> onStopSelected(nom, null, emptyList()) },
                    onLineClick = { lineName -> onLineSelected(lineName) },
                    onVehicleClick = { lineName -> onLineSelected(lineName) },
                    onMapMoved = onFabReset,
                )

                if (!showAlertReport && !navigationState.isActive) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = sheetPeekHeight + 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (isMapRotated) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color.Black, CircleShape)
                                    .clickable {
                                        scope.launch {
                                            cameraState.animateTo(
                                                CameraPosition(
                                                    target = cameraState.position.target,
                                                    zoom = cameraState.position.zoom,
                                                    bearing = 0.0,
                                                    tilt = 0.0
                                                )
                                            )
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .graphicsLayer {
                                            rotationZ = -cameraState.position.bearing.toFloat()
                                        }
                                ) {
                                    val width = size.width
                                    val height = size.height
                                    val centerX = width / 2f
                                    val centerY = height / 2f

                                    // Path for the North (Red) pointer (Left half)
                                    val northLeftPath = Path().apply {
                                        moveTo(centerX, 0f)                     // Top tip
                                        lineTo(centerX - width * 0.2f, centerY)  // Middle left
                                        lineTo(centerX, centerY - height * 0.05f)// Center inner
                                        close()
                                    }
                                    // Path for the North (Red) pointer (Right half)
                                    val northRightPath = Path().apply {
                                        moveTo(centerX, 0f)                     // Top tip
                                        lineTo(centerX + width * 0.2f, centerY)  // Middle right
                                        lineTo(centerX, centerY - height * 0.05f)// Center inner
                                        close()
                                    }

                                    // Path for the South (White) pointer (Left half)
                                    val southLeftPath = Path().apply {
                                        moveTo(centerX, height)                 // Bottom tip
                                        lineTo(centerX - width * 0.2f, centerY)  // Middle left
                                        lineTo(centerX, centerY + height * 0.05f)// Center inner
                                        close()
                                    }
                                    // Path for the South (White) pointer (Right half)
                                    val southRightPath = Path().apply {
                                        moveTo(centerX, height)                 // Bottom tip
                                        lineTo(centerX + width * 0.2f, centerY)  // Middle right
                                        lineTo(centerX, centerY + height * 0.05f)// Center inner
                                        close()
                                    }

                                    // Draw North halves
                                    drawPath(northLeftPath, Color(0xFFEF4444))      // Bright Red
                                    drawPath(northRightPath, Color(0xFFDC2626)) // Darker Red for shadow

                                    // Draw South halves
                                    drawPath(southLeftPath, Color(0xFFE5E7EB))      // Light grey
                                    drawPath(southRightPath, Color.White)       // Pure white for highlight
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color.Black, CircleShape)
                                .clickable { onFabClick(isAtCenteringTarget) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isAtCenteringTarget) {
                                Icon(
                                    painter = fabDrawableProvider.getPainter("add_triangle_24px"),
                                    contentDescription = "Signaler une alerte",
                                    tint = Color(0xFFFACC15),
                                    modifier = Modifier.size(30.dp)
                                )
                            } else {
                                Canvas(modifier = Modifier.size(18.dp)) {
                                    val radius = size.minDimension / 2f
                                    drawCircle(
                                        color = Color(0xFF3B82F6),
                                        radius = radius
                                    )
                                    drawCircle(
                                        color = Color.White,
                                        radius = radius,
                                        style = Stroke(width = 3.dp.toPx())
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

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
                                hasVehicles -> Color(0xFFEF4444)
                                isActiveNoVehicles -> Color(0xFF9CA3AF)
                                else -> PrimaryColor
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

                            if (selectedLineName.isNullOrBlank() || lineRules.isLiveTrackableLine(selectedLineName)) {
                                Row(
                                    modifier = Modifier
                                        .shadow(4.dp, RoundedCornerShape(20.dp))
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(buttonColor)
                                        .clickable {
                                            if (isLiveModeEnabled) {
                                                if (isLiveTrackingEnabled) viewModel.stopLiveTracking()
                                                if (isGlobalLiveEnabled) viewModel.stopGlobalLive()
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
                },
            )
        }
    }
}

@Composable
private fun SettingsTab(viewModel: TransportViewModel, modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalPlatformContext.current
    val scope = rememberCoroutineScope()
    val routesStack = remember { mutableStateListOf("root") }
    val currentRoute = routesStack.lastOrNull() ?: "root"
    val navigateTo = { newRoute: String ->
        routesStack.add(newRoute)
        Unit
    }
    val navigateBack = {
        if (routesStack.size > 1) {
            routesStack.removeAt(routesStack.lastIndex)
        } else {
            onBack()
        }
        Unit
    }
    BackHandler(enabled = true) {
        navigateBack()
    }
    Box(modifier) {
        when (currentRoute) {
            "legal" -> LegalScreen(
                legalSections = remember { AppConfigLoader.getConfig().about.legalSections },
                onBackClick = navigateBack,
            )
            "credits" -> CreditsScreen(onBackClick = navigateBack)
            "contact" -> ContactScreen(onBackClick = navigateBack)
            "offline" -> OfflineSettingsScreen(viewModel = viewModel, onBackClick = navigateBack)
            "itinerary" -> {
                val cfg = remember { AppConfigLoader.getConfig().itinerarySettings }
                val prefs = remember { ItineraryPreferencesRepository(context) }
                ItinerarySettingsScreen(
                    screenTitle = cfg.screenTitle,
                    sectionTitle = cfg.sectionTitle,
                    options = cfg.options,
                    onBackClick = navigateBack,
                    onOptionToggle = { key, enabled -> prefs.setOptionEnabled(key, enabled) },
                    getInitialOptionState = { opt -> prefs.isOptionEnabled(opt.key, opt.defaultEnabled) },
                )
            }
            "telemetry" -> {
                val telemetryState = TelemetryEmitter.repository()?.state?.collectAsState(initial = null)?.value
                TelemetrySettingsScreen(
                    snapshot = telemetryState,
                    onBackClick = navigateBack,
                    onWipeHistory = {
                        scope.launch(ioDispatcher) {
                            runCatching {
                                LocalHistoryStorage(context).wipeAll()
                                Settings(context, "pelo_prefs").clear()
                                Settings(context, "pelo_search_history").clear()
                                TelemetryEmitter.wipePendingAndState()
                                withContext(Dispatchers.Main) {
                                    viewModel.loadFavorites()
                                }
                            }
                        }
                    },
                )
            }
            "about" -> SettingsScreen(
                versionName = appVersionName(context),
                onBackClick = navigateBack,
                onItineraryClick = {},
                onLegalClick = { navigateTo("legal") },
                onCreditsClick = { navigateTo("credits") },
                onContactClick = { navigateTo("contact") },
                onOfflineClick = {},
                onTelemetryClick = {},
                isAboutMenu = true
            )
            else -> SettingsScreen(
                versionName = appVersionName(context),
                onBackClick = navigateBack,
                onItineraryClick = { navigateTo("itinerary") },
                onLegalClick = {},
                onCreditsClick = {},
                onContactClick = {},
                onOfflineClick = { navigateTo("offline") },
                onTelemetryClick = { navigateTo("telemetry") },
                onAboutClick = { navigateTo("about") },
                isAboutMenu = false
            )
        }
    }
}
