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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Route
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
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
import eu.dotshell.pelo.generic.data.dataset.DatasetUpdates
import eu.dotshell.pelo.generic.data.models.geojson.FeatureCollection
import eu.dotshell.pelo.generic.data.models.geojson.StopCollection
import eu.dotshell.pelo.generic.data.models.geojson.StopFeature
import eu.dotshell.pelo.generic.data.models.itinerary.ItineraryFieldTarget
import eu.dotshell.pelo.generic.data.models.itinerary.SelectedStop
import eu.dotshell.pelo.generic.data.models.search.AddressSearchResult
import eu.dotshell.pelo.generic.data.models.search.TransportSearchContent
import eu.dotshell.pelo.generic.data.models.stops.Favorite
import eu.dotshell.pelo.generic.data.models.stops.StationInfo
import eu.dotshell.pelo.generic.data.models.ui.AllSchedulesInfo
import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.ItineraryPreferencesRepository
import eu.dotshell.pelo.generic.data.repository.offline.mapstyle.MapStyleRepository
import eu.dotshell.pelo.generic.service.TransportServiceProvider
import eu.dotshell.pelo.generic.utils.graphics.LineIconResolver
import eu.dotshell.pelo.generic.utils.map.MapStyleUtils
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
import eu.dotshell.pelo.generic.ui.screens.settings.SettingsScreen
import eu.dotshell.pelo.generic.ui.screens.settings.TelemetrySettingsScreen
import eu.dotshell.pelo.generic.ui.screens.settings.ThemeSettingsScreen
import eu.dotshell.pelo.generic.ui.screens.onboarding.TermsConsentGate
import eu.dotshell.pelo.generic.ui.screens.onboarding.TelemetryOptInGate
import eu.dotshell.pelo.generic.ui.screens.settings.about.ContactScreen
import eu.dotshell.pelo.generic.ui.screens.settings.about.CreditsScreen
import eu.dotshell.pelo.generic.ui.screens.settings.about.LegalScreen

import eu.dotshell.pelo.generic.ui.theme.AccentColor
import eu.dotshell.pelo.generic.ui.theme.AccentColorShade
import eu.dotshell.pelo.generic.ui.theme.Sand200
import eu.dotshell.pelo.generic.ui.theme.Sand400
import eu.dotshell.pelo.generic.ui.theme.bottomSheetContainerColor
import eu.dotshell.pelo.generic.ui.theme.floatingControlBorder
import eu.dotshell.pelo.generic.ui.theme.isAppInDarkTheme
import eu.dotshell.pelo.generic.ui.theme.PeloAppTheme
import eu.dotshell.pelo.generic.ui.theme.PeloTheme
import eu.dotshell.pelo.generic.ui.theme.ThemeController
import eu.dotshell.pelo.generic.ui.theme.LocalThemeController
import eu.dotshell.pelo.generic.data.repository.offline.theme.ThemeMode
import eu.dotshell.pelo.generic.data.repository.offline.theme.ThemePreferenceRepository
import eu.dotshell.pelo.generic.ui.viewmodel.TransportLinesUiState
import eu.dotshell.pelo.generic.ui.viewmodel.TransportStopsUiState
import eu.dotshell.pelo.generic.ui.viewmodel.TransportViewModel
import eu.dotshell.pelo.generic.ui.viewmodel.findStopByCoordinates
import eu.dotshell.pelo.generic.utils.location.GeoPoint
import eu.dotshell.pelo.generic.utils.location.LocationPermissionSignal
import eu.dotshell.pelo.generic.utils.location.LocationProvider
import eu.dotshell.pelo.generic.utils.location.HeadingProvider
import eu.dotshell.pelo.generic.service.NavigationLocationBus
import eu.dotshell.pelo.generic.service.NavigationModeController
import eu.dotshell.pelo.generic.service.NavigationModePlatform
import eu.dotshell.pelo.generic.service.NavigationNotificationBridge
import eu.dotshell.pelo.generic.service.NavigationSession
import eu.dotshell.pelo.generic.service.NavigationVoicePreference
import eu.dotshell.pelo.generic.ui.screens.plan.NavigationModeOverlay
import eu.dotshell.pelo.generic.ui.screens.plan.NavigationSheetContent
import eu.dotshell.pelo.generic.ui.screens.plan.NavigationVoiceGuidance
import eu.dotshell.pelo.generic.ui.screens.plan.NavigationSheetPeekContentHeight
import eu.dotshell.pelo.generic.ui.screens.plan.SheetDragHandleHeight
import eu.dotshell.pelo.generic.ui.screens.plan.buildNavigationModeUiState
import eu.dotshell.pelo.generic.ui.screens.plan.displayText
import eu.dotshell.pelo.generic.utils.geo.GeometryUtils
import eu.dotshell.pelo.generic.utils.location.LocationPermissionManager
import eu.dotshell.pelo.platform.DrawableProvider
import eu.dotshell.pelo.platform.BackHandler
import eu.dotshell.pelo.platform.LocalPlatformContext
import eu.dotshell.pelo.platform.StringProvider
import eu.dotshell.pelo.platform.Log
import eu.dotshell.pelo.platform.appVersionName
import eu.dotshell.pelo.platform.ioDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.spatialk.geojson.Position
import eu.dotshell.pelo.generic.data.repository.geocoding.GeocodingRepository
import eu.dotshell.pelo.generic.data.models.realtime.alerts.official.TrafficAlert
import eu.dotshell.pelo.generic.utils.navigation.selectNavigationAlert
import io.raptor.Location

@Composable
fun App(
    onNavigationModeChanged: (Boolean) -> Unit = {},
    onConsentAccepted: () -> Unit = {},
) {
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

                // Over-the-air timetable check: after init, and gated to at most once a
                // day on an unmetered network. Downloads (if any) apply at the NEXT cold
                // start, so this can never disturb the session that just loaded.
                runCatching { DatasetUpdates.forApp(context)?.maybeCheck() }
                    .onFailure { Log.w("PeloApp", "Dataset update check skipped: ${it.message}") }
                Log.i("PeloApp", "ioDispatcher: done")
            } catch (t: Throwable) {
                Log.e("PeloApp", "Transport data init failed: ${t.message}")
                withContext(Dispatchers.Main) {
                    isInitializing = false
                }
            }
        }
    }

    val themeRepo = remember(context) { ThemePreferenceRepository(context) }
    var themeMode by remember { mutableStateOf(themeRepo.getThemeMode()) }
    val darkTheme = when (themeMode) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    CompositionLocalProvider(
        LocalThemeController provides ThemeController(
            themeMode = themeMode,
            setThemeMode = { newMode ->
                themeMode = newMode
                themeRepo.saveThemeMode(newMode)
            }
        )
    ) {
        PeloTheme(darkTheme = darkTheme) {
            TermsConsentGate(onConsentSatisfied = onConsentAccepted) {
                TelemetryOptInGate {
                    Box(Modifier.fillMaxSize()) {
                        val vm = viewModel
                        if (vm != null) {
                            RootScaffold(vm, onNavigationModeChanged)
                        } else {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                        }

                        if (isInitializing) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                        RoundedCornerShape(999.dp)
                                    )
                                    .padding(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
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
    // Device heading (degrees clockwise from north) for the direction cone on the location dot;
    // null until the compass reports (or on devices without a magnetometer).
    var heading by remember { mutableStateOf<Float?>(null) }
    var hasCenteredInitially by remember { mutableStateOf(false) }
    val locationProvider = remember { LocationProvider(context) }
    val headingProvider = remember { HeadingProvider(context) }
    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(
            target = org.maplibre.spatialk.geojson.Position(latitude = 45.75, longitude = 4.85),
            zoom = 12.0,
            bearing = 0.0
        )
    )
    val navigationController = remember(context) { NavigationModeController(context) }
    val navigationSession by navigationController.session.collectAsState()
    val isNavigating = navigationSession.isActive
    // True while the camera tracks the traveller. Panning the map drops out of it; the recentre
    // button puts it back. Navigation used to lock the map instead, which left no way to look
    // ahead at the route.
    var isFollowingUser by remember { mutableStateOf(true) }
    // Set while the journey trace is being resolved, so "Start" reads as busy instead of dead.
    var isStartingNavigation by remember { mutableStateOf(false) }
    var navigationBlockedMessage by remember { mutableStateOf(false) }
    var isVoiceGuidanceEnabled by remember(context) {
        mutableStateOf(NavigationVoicePreference.isEnabled(context))
    }
    var isRerouting by remember { mutableStateOf(false) }
    var rerouteDismissed by remember { mutableStateOf(false) }
    var rerouteFailed by remember { mutableStateOf(false) }
    var shownNavigationAlert by remember { mutableStateOf<TrafficAlert?>(null) }
    val trafficAlerts by viewModel.trafficAlerts.collectAsState(initial = emptyList())
    // Dismissing the prompt silences it for this departure only: coming back onto the route and
    // leaving it again is a new situation, and worth asking about again.
    LaunchedEffect(navigationSession.progress.isOffRoute) {
        if (!navigationSession.progress.isOffRoute) rerouteDismissed = false
    }
    DisposableEffect(navigationController) {
        onDispose { navigationController.dispose() }
    }
    // iOS needs the accuracy and background-updates switch flipped on the live stream; on Android
    // the foreground service covers it and this is a no-op.
    DisposableEffect(locationProvider, isNavigating) {
        locationProvider.setNavigationMode(isNavigating)
        onDispose { }
    }
    val onLocationFix: (GeoPoint) -> Unit = { p ->
        userLocation = Position(latitude = p.latitude, longitude = p.longitude)
        navigationController.onLocationFix(p)
    }
    // Fixes from the platform's own background stream (Android's navigation foreground service).
    // Collected unconditionally: it is the only source still delivering once the app leaves the
    // screen, which is where the session used to go stale.
    LaunchedEffect(navigationController) {
        NavigationLocationBus.fixes.collect(onLocationFix)
    }
    // Re-subscribe to location whenever the permission is (re)granted — e.g. right after the user
    // accepts the runtime prompt — so a fix is picked up without restarting the app.
    // Navigation wants roughly a fix a second so the camera tracks instead of lurching — unless
    // the platform already runs its own navigation-grade stream, in which case a second
    // high-accuracy one alongside it would just be double the battery for the same data.
    val locationPermissionGranted by LocationPermissionSignal.granted.collectAsState()
    val wantsFastFixes = isNavigating && !NavigationModePlatform.ownsLocationStream
    DisposableEffect(locationProvider, locationPermissionGranted, wantsFastFixes) {
        locationProvider.startUpdates(
            intervalMillis = if (wantsFastFixes) 1_000L else 5_000L,
            onLocation = onLocationFix,
        )
        onDispose {
            locationProvider.stopUpdates()
        }
    }
    // Compass updates for the direction cone. The provider already smooths and rate-limits, so we
    // set the state directly. Started alongside location (the cone only shows with the dot) and
    // stopped on dispose to release the sensor.
    DisposableEffect(headingProvider, locationPermissionGranted) {
        headingProvider.startUpdates { deg ->
            heading = deg
        }
        onDispose {
            headingProvider.stopUpdates()
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

    // Keyed on linesUiState as well as the positions: vehicle dots take their colour from the
    // operator palette registered when the lines load, so a stream that starts first has to be
    // re-serialized once that data lands or the dots keep the per-mode fallback colour.
    val vehiclesGeoJson = remember(activeVehiclePositions, linesUiState) {
        if (activeVehiclePositions.isEmpty()) null else toVehiclesGeoJson(activeVehiclePositions)
    }
    val vehicleIconName = remember(selectedLine?.lineName) {
        selectedLine?.lineName?.let { LineIconResolver.getDrawableNameForLineName(it) }
    }

    var itineraryActive by remember { mutableStateOf(false) }
    var activeJourneys by remember { mutableStateOf<List<JourneyResult>>(emptyList()) }
    var selectedJourney by remember { mutableStateOf<JourneyResult?>(null) }
    // Marker for a point the user picked by holding the map. Cleared with the itinerary.
    var droppedPin by remember { mutableStateOf<Position?>(null) }
    // Resolved in composition: the string accessor is @Composable and cannot be called from
    // the coroutine that names the pin.
    val droppedPinLabel = StringProvider(context)["dropped_pin"]

    LaunchedEffect(itineraryActive) {
        if (!itineraryActive) droppedPin = null
    }

    LaunchedEffect(itineraryActive) {
        if (itineraryActive) {
            if (isLiveTrackingEnabled) viewModel.stopLiveTracking()
            if (isGlobalLiveEnabled) viewModel.stopGlobalLive()
        }
    }
    // Walking dashes are drawn against the basemap, so they follow the theme rather than the
    // palette: black on the light map, white on the dark one.
    val walkingPathColor = if (isAppInDarkTheme()) "#FFFFFF" else "#000000"
    val itineraryGeoJson by produceState<String?>(
        initialValue = null,
        key1 = activeJourneys,
        key2 = selectedJourney,
        key3 = walkingPathColor
    ) {
        if (activeJourneys.isEmpty()) {
            value = null
            return@produceState
        }
        // Instant first paint: cached street walk paths where available, straight lines otherwise
        value = withContext(Dispatchers.Default) {
            toItinerariesGeoJson(
                activeJourneys, selectedJourney, viewModel,
                fetchWalkingPaths = false, walkingColor = walkingPathColor,
            )
        }
        // Background refinement: fetch the missing street paths, then update only on change
        val refined = withContext(Dispatchers.Default) {
            toItinerariesGeoJson(
                activeJourneys, selectedJourney, viewModel,
                fetchWalkingPaths = true, walkingColor = walkingPathColor,
            )
        }
        if (refined != value) {
            value = refined
        }
    }
    var itineraryDeparture by remember { mutableStateOf<SelectedStop?>(null) }
    var itineraryArrival by remember { mutableStateOf<SelectedStop?>(null) }
    var itineraryNearby by remember { mutableStateOf<List<String>>(emptyList()) }
    var itinerarySearchTarget by remember { mutableStateOf<ItineraryFieldTarget?>(null) }
    var itineraryArrivalSeed by remember { mutableStateOf<String?>(null) }
    // Captured at composable scope: string resources aren't readable inside LaunchedEffect
    val myPositionLabel = StringProvider(context)["my_position"]
    LaunchedEffect(itineraryActive, itineraryArrivalSeed) {
        if (!itineraryActive) return@LaunchedEffect
        val arrivalName = itineraryArrivalSeed
        if (arrivalName != null) {
            runCatching { viewModel.raptorRepository.resolveStopIdsByName(arrivalName) }
                .getOrDefault(emptyList()).takeIf { it.isNotEmpty() }
                ?.let { itineraryArrival = SelectedStop(name = arrivalName, stopIds = it) }
        }
        val loc = userLocation
        if (loc != null && itineraryDeparture == null) {
            // Departure = the actual GPS point: raptor walks to every stop in range natively.
            // Nearby stop names are still collected for the stop-departure fallback UI.
            itineraryDeparture = SelectedStop(
                name = myPositionLabel,
                stopIds = emptyList(),
                lat = loc.latitude,
                lon = loc.longitude
            )
            val nearest = runCatching { viewModel.raptorRepository.findNearestStops(loc.latitude, loc.longitude, 5) }
                .getOrDefault(emptyList())
            itineraryNearby = nearest.map { it.name }.distinct()
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
    val stopNavigation = {
        navigationController.stop()
        onNavigationModeChanged(false)
        isFollowingUser = true
    }
    val closeItinerary = {
        // Tearing down the itinerary while guidance runs would pull the route out from under it —
        // and, through onNavigationModeChanged(false), shut the foreground service down too.
        if (isNavigating) stopNavigation()
        itineraryActive = false
        itinerarySearchTarget = null
        itineraryArrival = null
        itineraryDeparture = null
        itineraryArrivalSeed = null
        itineraryNearby = emptyList()
        activeJourneys = emptyList()
        selectedJourney = null
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
    fun startItineraryToAddress(address: AddressSearchResult) {
        closeSheet()
        itineraryDeparture = null
        itineraryArrivalSeed = null
        itineraryArrival = SelectedStop(
            name = address.label,
            stopIds = emptyList(),
            lat = address.lat,
            lon = address.lon
        )
        itineraryActive = true
    }

    /**
     * Drops a pin where the user held the map and routes to it. The point is named by reverse
     * geocoding, which is best-effort: a failure (offline, rate limited, nowhere in particular)
     * falls back to a generic label rather than leaving the user with nothing.
     */
    fun startItineraryToDroppedPin(latitude: Double, longitude: Double) {
        droppedPin = Position(latitude = latitude, longitude = longitude)
        scope.launch {
            val geocoded = GeocodingRepository.getInstance().reverseGeocode(latitude, longitude)
            startItineraryToAddress(
                geocoded ?: AddressSearchResult(
                    label = droppedPinLabel,
                    detail = null,
                    lat = latitude,
                    lon = longitude,
                )
            )
        }
    }

    LaunchedEffect(selectedStation?.nom, selectedLine?.currentStationName, stops) {
        val stName = selectedStation?.nom ?: selectedLine?.currentStationName
        if (!stName.isNullOrBlank() && stops != null) {
            val stop = if (selectedLine?.lineName != null) {
                viewModel.getStopsFeaturesForLine(selectedLine!!.lineName)
                    .firstOrNull { it.properties.nom.equals(stName, ignoreCase = true) }
                    ?: stops.firstOrNull { it.properties.nom.equals(stName, ignoreCase = true) }
            } else {
                stops.firstOrNull { it.properties.nom.equals(stName, ignoreCase = true) }
            }
            if (stop != null && stop.geometry.coordinates.size >= 2) {
                manualFocusCenter = Position(latitude = stop.geometry.coordinates[1], longitude = stop.geometry.coordinates[0])
                manualFocusZoom = 18.0
            }
        }
    }

    LaunchedEffect(selectedLine?.lineName, linesUiState) {
        val ln = selectedLine?.lineName
        if (!ln.isNullOrBlank() && selectedLine?.currentStationName.isNullOrBlank()) {
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

    LaunchedEffect(isNavigating) {
        if (!isNavigating) {
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

    // Read through a snapshot so the guard below sees the live value: the callback is captured
    // once, when the sheet state is created.
    val isNavigatingNow = rememberUpdatedState(isNavigating)
    val bottomSheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.Hidden,
        skipHiddenState = false,
        // Navigation's summary is the sheet's peek area, so dismissing it would take the stop
        // button and the countdown off screen with it.
        confirmValueChange = { target -> !(isNavigatingNow.value && target == SheetValue.Hidden) },
    )
    val bsScaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = bottomSheetState)
    val hasSheet = isNavigating || itineraryActive || selectedStation != null || selectedLine != null || allSchedules != null
    val sheetContentKey = "$isNavigating|$itineraryActive|${selectedStation?.nom}|${selectedLine?.lineName}|${allSchedules?.lineName}"
    LaunchedEffect(sheetContentKey) {
        when {
            // Navigation opens collapsed: the map is the point, the breakdown is on demand.
            isNavigating -> bottomSheetState.partialExpand()
            hasSheet -> bottomSheetState.expand()
            else -> bottomSheetState.hide()
        }
    }
    // A hidden sheet normally means "the user dismissed the itinerary". Entering navigation also
    // hides it — without this guard that read as a dismissal and tore the journey down one frame
    // after guidance started, taking the route line and the foreground service with it.
    LaunchedEffect(bottomSheetState.currentValue, isNavigating) {
        if (bottomSheetState.currentValue == SheetValue.Hidden && !isNavigating) {
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
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topMargin = if (itineraryActive) 290.dp else 320.dp
    val maxSheetHeight = minOf(700.dp, screenHeightDp - topInset - topMargin).coerceAtLeast(130.dp)
    // Navigation's sheet is not a peek-and-glance panel: expanded it takes the whole screen bar
    // the status bar, covering the instruction card and everything else.
    val navigationSheetMaxHeight = (screenHeightDp - topInset).coerceAtLeast(240.dp)
    // Drag handle + summary row + gesture inset: what stays on screen with the sheet collapsed.
    val navigationPeekHeight = SheetDragHandleHeight + NavigationSheetPeekContentHeight + bottomInset

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (selectedTab == Destination.SETTINGS) {
                    SettingsTab(viewModel, Modifier.fillMaxSize()) { selectedTab = Destination.PLAN }
                } else {
                    // While navigating, the camera only chases the traveller as long as they have
                    // not taken the map somewhere themselves.
                    val isNavigationFollowing = isNavigating && isFollowingUser
                    val focusCenter = remember(isCenteredOnUser, userLocation, manualFocusCenter, isNavigationFollowing) {
                        if (isNavigationFollowing && userLocation != null) return@remember userLocation
                        if (isCenteredOnUser && userLocation != null) return@remember userLocation
                        manualFocusCenter
                    }
                    val focusZoom = remember(isCenteredOnUser, manualFocusZoom, isNavigationFollowing) {
                        if (isNavigationFollowing) return@remember 18.0
                        if (isCenteredOnUser) return@remember 18.0
                        manualFocusZoom
                    }

                    PlanContent(
                        viewModel = viewModel,
                        stops = filteredStopsCollection?.features,
                        userLocation = userLocation,
                        heading = heading,
                        userFavorites = userFavorites,
                        showTopBar = !itineraryActive && !isNavigating,
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
                        onAddressItinerarySelected = { address -> startItineraryToAddress(address) },
                        onMapLongPress = { lat, lon -> startItineraryToDroppedPin(lat, lon) },
                        droppedPin = droppedPin,
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
                            // Panning during navigation hands the camera to the traveller; the
                            // recentre button in the overlay hands it back.
                            if (isNavigating) isFollowingUser = false
                        },
                        showAlertReport = showAlertReport,
                        bsScaffoldState = bsScaffoldState,
                        sheetPeekHeight = when {
                            isNavigating -> navigationPeekHeight
                            hasSheet -> 130.dp
                            else -> 0.dp
                        },
                        navigationSession = navigationSession,
                        isNavigationFollowing = isNavigationFollowing,
                        sheetContent = {
                            Box(
                                Modifier.heightIn(
                                    max = if (isNavigating) navigationSheetMaxHeight else maxSheetHeight
                                )
                            ) {
                                val sc = allSchedules
                                val ln = selectedLine
                                val st = selectedStation
                                val navigationJourney = navigationSession.journey
                                val navigationUiState = remember(navigationSession) {
                                    buildNavigationModeUiState(navigationSession)
                                }
                                when {
                                    // Navigation owns the sheet outright: collapsed it is the
                                    // journey summary, pulled up it is the full breakdown.
                                    isNavigating && navigationJourney != null && navigationUiState != null ->
                                        NavigationSheetContent(
                                            state = navigationUiState,
                                            journey = navigationJourney,
                                            onStop = stopNavigation,
                                            onReportAlert = {
                                                val nearestStop = nearestStopTo(
                                                    userLocation,
                                                    filteredStopsCollection?.features,
                                                )
                                                alertReportInitialStopName = nearestStop?.properties?.nom
                                                alertReportInitialLines = nearestStop
                                                    ?.let { viewModel.parseLineCodesFromDesserte(it.properties.desserte) }
                                                    .orEmpty()
                                                showAlertReport = true
                                            },
                                            maxHeight = navigationSheetMaxHeight,
                                            getZoneForStopName = viewModel::getZoneForStopName,
                                        )

                                    itineraryActive -> InlineItinerarySheetContent(
                                        viewModel = viewModel,
                                        departureStop = itineraryDeparture,
                                        arrivalStop = itineraryArrival,
                                        maxHeight = maxSheetHeight,
                                        nearbyDepartureStops = itineraryNearby,
                                        onDepartureFallbackSelected = { itineraryDeparture = it },
                                        onJourneysChanged = { activeJourneys = it },
                                        onSelectedJourneyChanged = { selectedJourney = it },
                                        isStartingNavigation = isStartingNavigation,
                                        onStartNavigation = { journey ->
                                            // Guard the whole thing: the trace fetch is network-bound,
                                            // so without this a slow link turns into repeat taps and
                                            // several sessions racing each other.
                                            if (!isStartingNavigation && !isNavigating) {
                                                if (!LocationPermissionManager
                                                        .hasForegroundLocationPermission(context)
                                                ) {
                                                    LocationPermissionManager
                                                        .requestNavigationPermissions(context)
                                                    navigationBlockedMessage = true
                                                } else {
                                                    isStartingNavigation = true
                                                    scope.launch {
                                                        // Sectioning line geometry is heavy and not
                                                        // suspending — off the main thread it goes.
                                                        val tracePoints = withContext(Dispatchers.Default) {
                                                            runCatching { calculateJourneyTrace(journey, viewModel) }
                                                                .getOrDefault(emptyList())
                                                        }
                                                        navigationController.start(journey, tracePoints)
                                                        isFollowingUser = true
                                                        onNavigationModeChanged(true)
                                                        isStartingNavigation = false
                                                    }
                                                }
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

            if (!isNavigating) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    val tabStrings = StringProvider(LocalPlatformContext.current)
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
                            icon = { Icon(destination.icon, contentDescription = tabStrings[destination.contentDescriptionKey]) },
                            label = { Text(tabStrings[destination.labelKey]) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = AccentColor,
                                // The selected icon sits on the red indicator, so it stays white in
                                // both themes; the label sits on the navbar surface, so it follows it.
                                selectedIconColor = Color.White,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        }

        if (itineraryActive && selectedTab != Destination.SETTINGS && !isNavigating) {
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
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .floatingControlBorder(CircleShape)
                        .clickable {
                            val tmp = itineraryDeparture; itineraryDeparture = itineraryArrival; itineraryArrival = tmp
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.SwapVert,
                        contentDescription = "Inverser",
                        tint = MaterialTheme.colorScheme.onSurface,
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
                onSearchAddresses = { q -> viewModel.searchAddresses(q) },
                onAddressSelected = { address ->
                    val sel = SelectedStop(
                        name = address.label,
                        stopIds = emptyList(),
                        lat = address.lat,
                        lon = address.lon
                    )
                    if (isDeparture) itineraryDeparture = sel else itineraryArrival = sel
                    itinerarySearchTarget = null
                },
                showMyPosition = userLocation != null,
                onMyPositionSelected = {
                    userLocation?.let { loc ->
                        val sel = SelectedStop(
                            name = myPositionLabel,
                            stopIds = emptyList(),
                            lat = loc.latitude,
                            lon = loc.longitude
                        )
                        if (isDeparture) itineraryDeparture = sel else itineraryArrival = sel
                    }
                    itinerarySearchTarget = null
                },
            )
        }

        if (showLinesSheet) {
            val linesSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val allLines = remember(linesUiState, stopsUiState) { viewModel.getAllAvailableLines() }
            ModalBottomSheet(
                onDismissRequest = { showLinesSheet = false },
                containerColor = bottomSheetContainerColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
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
            val nearestStopCandidate = nearestStopTo(userLocation, filteredStopsCollection?.features)
                ?.let { stop ->
                    eu.dotshell.pelo.generic.data.models.search.StationSearchResult(
                        stopName = stop.properties.nom,
                        stopId = stop.properties.id,
                        lines = viewModel.parseLineCodesFromDesserte(stop.properties.desserte)
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

        shownNavigationAlert?.let { alert ->
            val alertStrings = StringProvider(LocalPlatformContext.current)
            AlertDialog(
                onDismissRequest = { shownNavigationAlert = null },
                title = { Text(alert.title) },
                text = { Text(alert.message) },
                confirmButton = {
                    TextButton(onClick = { shownNavigationAlert = null }) {
                        Text(alertStrings["close"])
                    }
                }
            )
        }

        if (rerouteFailed) {
            val rerouteStrings = StringProvider(LocalPlatformContext.current)
            AlertDialog(
                onDismissRequest = { rerouteFailed = false },
                text = { Text(rerouteStrings["nav_reroute_failed"]) },
                confirmButton = {
                    TextButton(onClick = { rerouteFailed = false }) {
                        Text(rerouteStrings["close"])
                    }
                }
            )
        }

        if (navigationBlockedMessage) {
            val blockedStrings = StringProvider(LocalPlatformContext.current)
            AlertDialog(
                onDismissRequest = { navigationBlockedMessage = false },
                text = { Text(blockedStrings["nav_needs_location"]) },
                confirmButton = {
                    TextButton(onClick = { navigationBlockedMessage = false }) {
                        Text(blockedStrings["close"])
                    }
                }
            )
        }

        if (isNavigating) {
            val voiceState = remember(navigationSession) {
                buildNavigationModeUiState(navigationSession)
            }
            if (voiceState != null) {
                NavigationVoiceGuidance(state = voiceState, isEnabled = isVoiceGuidanceEnabled)
            }
        }

        // Navigation overlay. Rendered for the whole of navigation mode, fix or no fix: gating it
        // on a known position hid every control at exactly the moment the tab bar, search bar,
        // sheet and map gestures were already suppressed, which left no way out of the mode.
        //
        // It sits here, above the scaffold, rather than inside the scaffold's body. Putting it in
        // the body — so an expanded sheet would paint over it — scrambled the itinerary polylines
        // on the map, including outside navigation. Hiding it once the sheet commits to expanding
        // gets the same result without touching the map's composition.
        if (isNavigating && bottomSheetState.targetValue != SheetValue.Expanded) {
            val overlayState = remember(navigationSession) {
                buildNavigationModeUiState(navigationSession)
            }
            if (overlayState != null) {
                // Mirror the instruction into the ongoing notification, so a backgrounded session
                // says what to do next instead of repeating a fixed sentence.
                // Keyed on the alert feed as well as the session: the feed refreshes in the
                // background, and a disruption declared mid-journey is exactly the one worth
                // hearing about.
                val navigationAlert = remember(
                    navigationSession.progress.legIndex,
                    navigationSession.journey,
                    trafficAlerts,
                ) {
                    selectNavigationAlert(
                        journey = navigationSession.journey,
                        fromLegIndex = navigationSession.progress.legIndex,
                        alertsForLine = viewModel::getAlertsForLine,
                    )
                }
                val instructionText = overlayState.instruction.displayText()
                LaunchedEffect(instructionText) {
                    NavigationNotificationBridge.setInstruction(instructionText)
                }
                DisposableEffect(Unit) {
                    onDispose { NavigationNotificationBridge.setInstruction(null) }
                }
                NavigationModeOverlay(
                    state = if (rerouteDismissed) overlayState.copy(canReroute = false) else overlayState,
                    showRecenterButton = !isFollowingUser,
                    onRecenter = { isFollowingUser = true },
                    isVoiceEnabled = isVoiceGuidanceEnabled,
                    onToggleVoice = {
                        isVoiceGuidanceEnabled = !isVoiceGuidanceEnabled
                        NavigationVoicePreference.setEnabled(context, isVoiceGuidanceEnabled)
                    },
                    isRerouting = isRerouting,
                    onReroute = {
                        val from = userLocation
                        val journey = navigationSession.journey
                        val destination = journey?.legs?.lastOrNull()
                        if (!isRerouting && from != null && destination != null) {
                            isRerouting = true
                            scope.launch {
                                // Replan from where the traveller actually is, to where they were
                                // always going — the destination survives, the route does not.
                                val replacement = runCatching {
                                    viewModel.getOptimizedPathsForLocations(
                                        origin = Location.Point(from.latitude, from.longitude),
                                        destination = Location.Point(destination.toLat, destination.toLon),
                                        departureTimeSeconds = GeometryUtils.currentTimeInSeconds(),
                                        originLabel = myPositionLabel,
                                        destinationLabel = destination.toStopName,
                                    )
                                }.getOrDefault(emptyList()).firstOrNull()

                                if (replacement != null) {
                                    val trace = withContext(Dispatchers.Default) {
                                        runCatching { calculateJourneyTrace(replacement, viewModel) }
                                            .getOrDefault(emptyList())
                                    }
                                    // Swap the map and the sheet over too, otherwise the guidance
                                    // and the drawn route would describe different journeys.
                                    activeJourneys = listOf(replacement)
                                    selectedJourney = replacement
                                    navigationController.start(replacement, trace)
                                    isFollowingUser = true
                                } else {
                                    rerouteFailed = true
                                }
                                isRerouting = false
                            }
                        }
                    },
                    onDismissReroute = { rerouteDismissed = true },
                    alert = navigationAlert,
                    onAlertClick = { shownNavigationAlert = navigationAlert },
                    sheetPeekHeight = navigationPeekHeight,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Back collapses the journey breakdown first, then leaves navigation. The journey and the
        // itinerary sheet survive it, so re-starting is one tap away.
        BackHandler(enabled = isNavigating) {
            if (bottomSheetState.currentValue == SheetValue.Expanded) {
                scope.launch { bottomSheetState.partialExpand() }
            } else {
                stopNavigation()
            }
        }
    }
}

/**
 * Nearest stop to [position], ranked in metres. Null when there is nothing to measure from:
 * scoring every candidate as infinitely far away otherwise returns whichever stop happens to sit
 * first in the list and presents it to the user as the one they are standing at.
 */
private fun nearestStopTo(position: Position?, stops: List<StopFeature>?): StopFeature? {
    if (position == null || stops.isNullOrEmpty()) return null
    return stops
        .filter { it.geometry.coordinates.size >= 2 }
        .minByOrNull { stop ->
            val coords = stop.geometry.coordinates
            GeometryUtils.squaredMeters(
                lat1 = position.latitude,
                lon1 = position.longitude,
                lat2 = coords[1],
                lon2 = coords[0],
            )
        }
}

@Composable
private fun PlanContent(
    viewModel: TransportViewModel,
    stops: List<StopFeature>?,
    userLocation: Position?,
    heading: Float?,
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
    onAddressItinerarySelected: (AddressSearchResult) -> Unit,
    onMapLongPress: (latitude: Double, longitude: Double) -> Unit,
    droppedPin: Position?,
    isCenteredOnUser: Boolean,
    onFabClick: (Boolean) -> Unit,
    onFabReset: () -> Unit,
    showAlertReport: Boolean,
    bsScaffoldState: BottomSheetScaffoldState,
    sheetPeekHeight: Dp,
    sheetContent: @Composable () -> Unit,
    navigationSession: NavigationSession,
    isNavigationFollowing: Boolean,
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
    val mapStyleConfig = remember { TransportServiceProvider.getMapStyleConfig() }
    val mapStyleRepo = remember { MapStyleRepository(context, mapStyleConfig) }
    var selectedMapStyle by remember { mutableStateOf(mapStyleRepo.getSelectedStyle()) }
    // Standard and 3D are light/dark pairs; the half that renders follows the app theme.
    val effectiveMapStyle = MapStyleUtils.resolveForTheme(selectedMapStyle, isAppInDarkTheme(), mapStyleConfig)
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
    val showAllLines by viewModel.showAllLinesOnMap.collectAsState(initial = false)
    val strongLines = allLines?.filter { lineRules.isStrongLine(it.properties.lineName) }
    val mapLines = remember(strongLines, selectedLineName, allLines, showAllLines) {
        if (allLines == null) return@remember null
        val strongs = strongLines ?: emptyList()
        val selected = if (!selectedLineName.isNullOrBlank()) {
            val normSelected = lineRules.normalizeForComparison(selectedLineName)
            allLines.firstOrNull { lineRules.normalizeForComparison(it.properties.lineName) == normSelected }
        } else null
        when {
            selected != null -> listOf(selected)
            showAllLines -> allLines
            else -> strongs
        }
    }

    Box(Modifier.fillMaxSize()) {
        BottomSheetScaffold(
            modifier = Modifier.fillMaxSize(),
            scaffoldState = bsScaffoldState,
            sheetPeekHeight = sheetPeekHeight,
            sheetContainerColor = bottomSheetContainerColor(),
            sheetContentColor = MaterialTheme.colorScheme.onSurface,
            sheetContent = {
                sheetContent()
            }
        ) {
            Box(Modifier.fillMaxSize()) {
                Log.i("PeloApp", "PlanContent: before MapCanvas")
                MapCanvas(
                    modifier = Modifier.fillMaxSize(),
                    styleUrl = effectiveMapStyle.styleUrl,
                    initialLatitude = 45.75,
                    initialLongitude = 4.85,
                    initialZoom = 12.0,
                    centerOn = focusCenter,
                    focusZoom = focusZoom,
                    cameraState = cameraState,
                    bearing = when {
                        // Only steer the camera while it is actually following; forcing a heading
                        // on a map the traveller panned away would fight them for control.
                        isNavigationFollowing -> navigationSession.bearing
                        isCenteredOnUser -> 0.0
                        else -> null
                    },
                    lines = mapLines?.let { FeatureCollection(features = it) },
                    stops = filteredStopsCollection,
                    userLocation = userLocation,
                    heading = heading,
                    vehiclesGeoJson = vehiclesGeoJson,
                    vehicleIconName = vehicleIconName,
                    selectedLineName = selectedLineName,
                    itineraryGeoJson = itineraryGeoJson,
                    // The map stays gesture-driven throughout navigation. Locking it out meant
                    // no way to look ahead at the route, check an exit, or zoom out to get
                    // oriented — the overlay's recentre button is what returns to following.
                    interactive = true,
                    tilt = when {
                        // Enough perspective to read the road ahead without the near-ground-level
                        // 55° pitch, which on a multi-kilometre transit leg showed one block.
                        isNavigationFollowing -> 45.0
                        isCenteredOnUser -> 0.0
                        else -> null
                    },
                    onStopClick = { nom -> onStopSelected(nom, null, emptyList()) },
                    onLineClick = { lineName -> onLineSelected(lineName) },
                    onVehicleClick = { lineName -> onLineSelected(lineName) },
                    // Tapping a place on the basemap routes to it, exactly as picking that
                    // name out of the search results would — same entry point, same result.
                    onBasemapPlaceClick = { name, latitude, longitude ->
                        onAddressItinerarySelected(
                            AddressSearchResult(
                                label = name,
                                detail = null,
                                lat = latitude,
                                lon = longitude,
                            )
                        )
                    },
                    onMapLongPress = onMapLongPress,
                    droppedPin = droppedPin,
                    onMapMoved = onFabReset,
                )

                if (!showAlertReport && !navigationSession.isActive) {
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
                                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                                    .floatingControlBorder(CircleShape)
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
                                    drawPath(northLeftPath, AccentColor)        // Brand orange
                                    drawPath(northRightPath, AccentColorShade)  // Darker orange for shadow

                                    // Draw South halves
                                    drawPath(southLeftPath, Sand200)            // Light sand
                                    drawPath(southRightPath, Color.White)       // Pure white for highlight
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .shadow(4.dp, CircleShape)
                                .background(MaterialTheme.colorScheme.surface, CircleShape)
                                .floatingControlBorder(CircleShape)
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
                                // Ring on the dark FAB is white; on the white FAB it matches the blue
                                // dot (so the marker reads as a solid blue dot rather than white-on-white).
                                val locationRingColor = if (isAppInDarkTheme()) Color.White else Color(0xFF3B82F6)
                                Canvas(modifier = Modifier.size(18.dp)) {
                                    val radius = size.minDimension / 2f
                                    drawCircle(
                                        color = Color(0xFF3B82F6),
                                        radius = radius
                                    )
                                    drawCircle(
                                        color = locationRingColor,
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
                    .then(if (searchExpanded) Modifier.background(MaterialTheme.colorScheme.surface) else Modifier)
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
                        // Picking an address launches an itinerary towards it right away
                        onSearchAddresses = { q -> viewModel.searchAddresses(q) },
                        onAddressSelected = { address -> onAddressItinerarySelected(address) },
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
                                hasVehicles -> AccentColor
                                isActiveNoVehicles -> Sand400
                                else -> MaterialTheme.colorScheme.surface
                            }
                            // White reads on the orange/sand active states; onSurface on the themed idle state.
                            val buttonContentColor = if (hasVehicles || isActiveNoVehicles) Color.White else MaterialTheme.colorScheme.onSurface

                            Row(
                                modifier = Modifier
                                    .shadow(2.dp, RoundedCornerShape(20.dp))
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .floatingControlBorder(RoundedCornerShape(20.dp))
                                    .clickable { showStyleSheet = true }
                                    .height(40.dp)
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Layers,
                                    contentDescription = "Style de carte",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            if (selectedLineName.isNullOrBlank() || lineRules.isLiveTrackableLine(selectedLineName)) {
                                Row(
                                    modifier = Modifier
                                        .shadow(2.dp, RoundedCornerShape(20.dp))
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(buttonColor)
                                        .floatingControlBorder(RoundedCornerShape(20.dp))
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
                                        drawCircle(color = buttonContentColor)
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "LIVE",
                                        fontWeight = FontWeight.Bold,
                                        color = buttonContentColor,
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

            "itinerary" -> {
                val cfg = remember { AppConfigLoader.getConfig().itinerarySettings }
                val prefs = remember { ItineraryPreferencesRepository(context) }
                val strings = StringProvider(context)
                ItinerarySettingsScreen(
                    screenTitle = strings["itinerary"],
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
                                TelemetryEmitter.wipePendingAndState()
                            }
                        }
                    },
                )
            }
            "theme" -> ThemeSettingsScreen(onBackClick = navigateBack)
            "about" -> {
                val datasetScheduler = remember(context) { DatasetUpdates.forApp(context) }
                SettingsScreen(
                    versionName = appVersionName(context),
                    onBackClick = navigateBack,
                    onItineraryClick = {},
                    onLegalClick = { navigateTo("legal") },
                    onCreditsClick = { navigateTo("credits") },
                    onContactClick = { navigateTo("contact") },
                    onTelemetryClick = {},
                    onThemeClick = {},
                    isAboutMenu = true,
                    onCheckForUpdates = datasetScheduler?.let { scheduler ->
                        { DatasetUpdates.statusStringKey(scheduler.checkNow()) }
                    }
                )
            }
            else -> SettingsScreen(
                versionName = appVersionName(context),
                onBackClick = navigateBack,
                onItineraryClick = { navigateTo("itinerary") },
                onLegalClick = {},
                onCreditsClick = {},
                onContactClick = {},
                onTelemetryClick = { navigateTo("telemetry") },
                onAboutClick = { navigateTo("about") },
                onThemeClick = { navigateTo("theme") },

                isAboutMenu = false
            )
        }
    }
}
