package com.pelotcl.app.generic.ui.screens.plan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.pelotcl.app.R
import com.pelotcl.app.generic.data.models.geojson.Feature
import com.pelotcl.app.generic.data.models.itinerary.ItineraryFieldTarget
import com.pelotcl.app.generic.data.models.itinerary.SelectedStop
import com.pelotcl.app.generic.data.models.navigation.NavigationAlertPrompt
import com.pelotcl.app.generic.data.models.realtime.alerts.community.UserStopAlertsResponse
import com.pelotcl.app.generic.data.models.search.StationSearchResult
import com.pelotcl.app.generic.data.models.search.TransportSearchContent
import com.pelotcl.app.generic.data.models.stops.StationInfo
import com.pelotcl.app.generic.data.models.ui.AllSchedulesInfo
import com.pelotcl.app.generic.data.models.ui.MapFilterState
import com.pelotcl.app.generic.data.models.ui.SheetContentState
import com.pelotcl.app.generic.data.network.mapstyle.MapStyleData
import com.pelotcl.app.generic.data.repository.itinerary.itinerary.JourneyLeg
import com.pelotcl.app.generic.data.repository.itinerary.itinerary.JourneyResult
import com.pelotcl.app.generic.data.repository.offline.mapstyle.MapStyleRepository
import com.pelotcl.app.generic.service.TransportServiceProvider
import com.pelotcl.app.generic.ui.components.MapLibreView
import com.pelotcl.app.generic.ui.components.favorites.AddFavoriteDialog
import com.pelotcl.app.generic.ui.components.search.TransportSearchBar
import com.pelotcl.app.generic.ui.screens.plan.itinerary.InlineItinerarySheetContent
import com.pelotcl.app.generic.ui.screens.plan.itinerary.ItinerarySearchBarField
import com.pelotcl.app.generic.ui.theme.PrimaryColor
import com.pelotcl.app.generic.ui.theme.SecondaryColor
import com.pelotcl.app.generic.ui.theme.Stone800
import com.pelotcl.app.generic.ui.theme.Yellow500
import com.pelotcl.app.generic.ui.viewmodel.TransportLinesUiState
import com.pelotcl.app.generic.ui.viewmodel.TransportStopsUiState
import com.pelotcl.app.generic.ui.viewmodel.TransportViewModel
import com.pelotcl.app.generic.utils.graphics.BitmapUtils.ensureVehicleMarkerImage
import com.pelotcl.app.generic.utils.graphics.BusIconHelper
import com.pelotcl.app.generic.utils.location.LocationHelper.startLocationUpdates
import com.pelotcl.app.generic.utils.location.LocationHelper.stopLocationUpdates
import com.pelotcl.app.generic.utils.navigation.JourneyNavigationManager.buildJourneyAlertSessionKey
import com.pelotcl.app.generic.utils.navigation.JourneyNavigationManager.buildNavigationAlertPrompt
import com.pelotcl.app.generic.utils.navigation.JourneyNavigationManager.buildNavigationAlertQuestion
import com.pelotcl.app.generic.utils.navigation.JourneyNavigationManager.buildNavigationPathPoints
import com.pelotcl.app.generic.utils.map.ItineraryMapManager.clearItineraryLayers
import com.pelotcl.app.generic.utils.geo.GeometryUtils.computeBearingDegrees
import com.pelotcl.app.generic.utils.geo.GeometryUtils.currentTimeInSeconds
import com.pelotcl.app.generic.utils.geo.GeometryUtils.distanceMeters
import com.pelotcl.app.generic.utils.geo.GeometryUtils.findNavigationAxisSegment
import com.pelotcl.app.generic.utils.geo.GeometryUtils.findNearestStopName
import com.pelotcl.app.generic.utils.geo.GeometryUtils.squaredDistance
import com.pelotcl.app.generic.utils.map.ItineraryMapManager.drawItinerariesOnMap
import com.pelotcl.app.generic.utils.map.MapStopsManager.addStopsToMap
import com.pelotcl.app.generic.utils.map.MapStopsManager.filterMapStopsWithSelectedStop
import com.pelotcl.app.generic.utils.navigation.JourneyNavigationManager.computeRemainingJourneySeconds
import com.pelotcl.app.generic.utils.navigation.JourneyNavigationManager.computeRemainingStopsOnLeg
import com.pelotcl.app.generic.utils.navigation.JourneyNavigationManager.computeTransferWaitSeconds
import com.pelotcl.app.generic.utils.navigation.JourneyNavigationManager.findApproachingAlertStop
import com.pelotcl.app.generic.utils.navigation.JourneyNavigationManager.findOverdueNavigationKeyStop
import com.pelotcl.app.generic.utils.navigation.JourneyNavigationManager.findUpcomingNonWalkingLeg
import com.pelotcl.app.generic.utils.navigation.JourneyNavigationManager.formatDurationUntil
import com.pelotcl.app.generic.utils.navigation.JourneyNavigationManager.formatRemainingTime
import com.pelotcl.app.generic.utils.navigation.JourneyNavigationManager.getCurrentAndNextNavigationLeg
import com.pelotcl.app.generic.utils.map.MapLinesManager.hideMapLines
import com.pelotcl.app.generic.utils.navigation.JourneyNavigationManager.isAtCurrentLegTransferStop
import com.pelotcl.app.generic.utils.navigation.JourneyNavigationManager.isNearestJourneyStopTerminus
import com.pelotcl.app.generic.utils.navigation.JourneyNavigationManager.normalizeTimeAroundReference
import com.pelotcl.app.generic.utils.map.MapLinesManager.showAllMapLines
import com.pelotcl.app.generic.utils.map.ItineraryMapManager.zoomToItineraries
import com.pelotcl.app.generic.utils.map.MapLinesManager.zoomToLine
import com.pelotcl.app.generic.utils.map.MapStopsManager.zoomToStop
import com.pelotcl.app.generic.data.models.ui.VehicleMarkerType
import com.pelotcl.app.generic.utils.LineColorHelper
import com.pelotcl.app.generic.utils.map.LineMapManager.filterMapLines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import kotlin.text.equals

const val PRIORITY_STOPS_MIN_ZOOM = 12.5f
const val TRAM_STOPS_MIN_ZOOM = 14.0f
const val SECONDARY_STOPS_MIN_ZOOM = 17.0f
const val SELECTED_STOP_MIN_ZOOM = 9.0f
const val LIVE_MODE_ZOOM_LEVEL = 12.0f // Zoom level for live tracking mode (below PRIORITY_STOPS_MIN_ZOOM to hide stop icons)
const val WALKING_MAX_SPEED_MPS = 2.5
const val LONG_TRANSFER_THRESHOLD_SECONDS = 10 * 60

private val lineRules get() = TransportServiceProvider.getTransportLineRules()
private fun isMetroTramOrFunicular(lineName: String): Boolean = lineRules.isStrongLine(lineName)
private fun isStrongLine(lineName: String): Boolean = lineRules.isStrongLine(lineName)
private fun isNavigoneLine(lineName: String): Boolean = lineRules.isNavigoneLine(lineName)
private fun isTemporaryBus(lineName: String): Boolean = !lineRules.isStrongLine(lineName)
private fun isLiveTrackableLine(lineName: String): Boolean = lineRules.isLiveTrackableLine(lineName)
private fun getVehicleMarkerType(lineName: String): VehicleMarkerType = lineRules.getVehicleMarkerType(lineName)
private fun areEquivalentLineNames(first: String, second: String): Boolean = lineRules.canonicalRouteName(first) == lineRules.canonicalRouteName(second)
const val LATE_TRANSFER_RECALC_THRESHOLD_SECONDS = 3 * 60
const val AUTO_RECALC_MAX_STOP_IDS = 64
const val NAV_ALERT_APPROACH_DISTANCE_METERS = 140.0
const val NAV_ALERT_APPROACH_TIME_SECONDS = 3 * 60

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: TransportViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                val application = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: throw IllegalStateException("Application not found in CreationExtras")
                @Suppress("UNCHECKED_CAST")
                return TransportViewModel(application) as T
            }
        }
    ),
    onSheetStateChanged: (Boolean) -> Unit = {},
    showLinesSheet: Boolean = false,
    onLinesSheetDismiss: () -> Unit = {},
    searchSelectedStop: StationSearchResult? = null,
    onSearchSelectionHandled: () -> Unit = {},
    itinerarySelectedStopName: String? = null,
    onItinerarySelectionHandled: () -> Unit = {},
    optionsSelectedStop: StationSearchResult? = null,
    onOptionsSelectionHandled: () -> Unit = {},
    initialUserLocation: LatLng? = null,
    isVisible: Boolean = true,
    onMapStyleChanged: (MapStyleData) -> Unit = {},
    isSearchExpanded: Boolean = false,
    onItineraryModeChanged: (Boolean) -> Unit = {},
    onNavigationModeChanged: (Boolean) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState(initial = TransportLinesUiState.Loading)
    val stopsUiState by viewModel.stopsUiState.collectAsState(initial = TransportStopsUiState.Loading)
    val favoriteStops by viewModel.favoriteStops.collectAsState(initial = emptySet())
    val vehiclePositions by viewModel.vehiclePositions.collectAsState(initial = emptyList())
    val isLiveTrackingEnabled by viewModel.isLiveTrackingEnabled.collectAsState(initial = false)
    val isOffline by viewModel.isOffline.collectAsState(initial = false)
    val isGlobalLiveEnabled by viewModel.isGlobalLiveEnabled.collectAsState(initial = false)
    val globalVehiclePositions by viewModel.globalVehiclePositions.collectAsState(initial = emptyList())
    val headsigns by viewModel.headsigns.collectAsState(initial = emptyMap())
    val availableDirections by viewModel.availableDirections.collectAsState(initial = emptyList())
    val allSchedules by viewModel.allSchedules.collectAsState(initial = emptyList())
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    // Incremented each time the map style is reloaded, to force LaunchedEffects to re-run
    var mapStyleVersion by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Location state
    var userLocation by remember { mutableStateOf(initialUserLocation) }
    var lastMovementSampleLocation by remember { mutableStateOf<LatLng?>(initialUserLocation) }
    var lastMovementSampleTimeMs by remember {
        mutableStateOf(if (initialUserLocation != null) System.currentTimeMillis() else null)
    }
    var currentMovementSpeedMps by remember { mutableStateOf(0.0) }
    // Center on user immediately if we have initial location, otherwise wait for first location update
    var shouldCenterOnUser by remember { mutableStateOf(initialUserLocation != null) }
    var isCenteredOnUser by remember { mutableStateOf(initialUserLocation != null) }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    fun updateMovementSpeed(newLocation: LatLng) {
        val nowMs = System.currentTimeMillis()
        val previousLocation = lastMovementSampleLocation
        val previousTimeMs = lastMovementSampleTimeMs

        if (previousLocation != null && previousTimeMs != null) {
            val deltaSeconds = (nowMs - previousTimeMs) / 1000.0
            if (deltaSeconds in 1.0..20.0) {
                val deltaMeters = distanceMeters(
                    lat1 = previousLocation.latitude,
                    lon1 = previousLocation.longitude,
                    lat2 = newLocation.latitude,
                    lon2 = newLocation.longitude
                )
                val instantSpeed = (deltaMeters / deltaSeconds).coerceAtLeast(0.0)
                // Smooth noisy GPS spikes while staying reactive.
                currentMovementSpeedMps = (currentMovementSpeedMps * 0.6) + (instantSpeed * 0.4)
            }
        }

        lastMovementSampleLocation = newLocation
        lastMovementSampleTimeMs = nowMs
    }

    // Handle when initial location becomes available from NavBar after first composition
    LaunchedEffect(initialUserLocation) {
        if (initialUserLocation != null && userLocation == null) {
            userLocation = initialUserLocation
            shouldCenterOnUser = true
            isCenteredOnUser = true
        }
    }

    // Map style from settings — re-read when returning to the Plan tab
    // When offline, use the effective style (fallback to a downloaded style if needed)
    val mapStyleRepository = remember {
        MapStyleRepository(
            context,
            TransportServiceProvider.getMapStyleConfig()
        )
    }
    val offlineDataInfo by viewModel.offlineDataInfo.collectAsState(initial = com.pelotcl.app.generic.data.offline.OfflineDataInfo())
    var mapStyleUrl by remember { mutableStateOf(mapStyleRepository.getSelectedStyle().styleUrl) }
    var selectedMapStyle by remember {
        mutableStateOf(
            mapStyleRepository.getEffectiveStyle(
                isOffline,
                offlineDataInfo.downloadedMapStyles
            )
        )
    }
    var isMapStyleMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val isDarkMatterStyle = selectedMapStyle.key == "dark_matter"
    LaunchedEffect(isVisible, isOffline, offlineDataInfo.downloadedMapStyles) {
        if (isVisible) {
            val effectiveStyle = mapStyleRepository.getEffectiveStyle(
                isOffline, offlineDataInfo.downloadedMapStyles
            )
            if (effectiveStyle.styleUrl != mapStyleUrl) {
                mapStyleUrl = effectiveStyle.styleUrl
            }
            selectedMapStyle = effectiveStyle
        }
    }
    LaunchedEffect(selectedMapStyle) {
        onMapStyleChanged(selectedMapStyle)
    }

    var sheetContentState by remember { mutableStateOf<SheetContentState?>(null) }

    // Bottom sheet state for BottomSheetScaffold
    val bottomSheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.Hidden,
        skipHiddenState = false
    )
    val scaffoldSheetState = rememberBottomSheetScaffoldState(
        bottomSheetState = bottomSheetState
    )
    var selectedStation by remember { mutableStateOf<StationInfo?>(null) }
    var selectedLine by remember { mutableStateOf<LineInfo?>(null) }
    var requestedSheetValueForNextContent by remember { mutableStateOf<SheetValue?>(null) }
    var itineraryInitialStopName by remember { mutableStateOf<String?>(null) }
    var itineraryDepartureStop by remember { mutableStateOf<SelectedStop?>(null) }
    var itineraryArrivalStop by remember { mutableStateOf<SelectedStop?>(null) }
    var itineraryDepartureQuery by remember { mutableStateOf("") }
    var itineraryArrivalQuery by remember { mutableStateOf("") }
    var itineraryNearbyDepartureStops by remember { mutableStateOf<List<String>>(emptyList()) }
    var itineraryJourneys by remember { mutableStateOf<List<JourneyResult>>(emptyList()) }
    var selectedItineraryJourney by remember { mutableStateOf<JourneyResult?>(null) }
    var itineraryResultsVersion by remember { mutableIntStateOf(0) }
    var showAlertReportSheet by rememberSaveable { mutableStateOf(false) }
    var alertReportInitialStop by remember { mutableStateOf<StationSearchResult?>(null) }
    var wasInNavigationMode by remember { mutableStateOf(false) }
    var navigationPathPoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var lastLateTransferRecalcKey by remember { mutableStateOf<String?>(null) }
    var isLateTransferRecalculating by remember { mutableStateOf(false) }

    var allSchedulesInfo by remember { mutableStateOf<AllSchedulesInfo?>(null) }

    // Preserve selected direction when navigating to/from schedule details
    var selectedDirection by remember { mutableIntStateOf(0) }
    // One-shot flag to keep an explicit direction chosen from station departures.
    var preserveSelectedDirectionOnce by remember { mutableStateOf(false) }

    var temporaryLoadedBusLines by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showAddFavoriteDialog by remember { mutableStateOf(false) }
    var addFavoriteInitialStopName by remember { mutableStateOf<String?>(null) }

    // Save zoom level before live tracking to restore it when disabled
    var zoomBeforeLiveTracking by remember { mutableStateOf<Double?>(null) }

    val selectedLineNameFromViewModel by viewModel.selectedLineName.collectAsState(initial = null)

    // Track previous sheetContentState to detect transitions
    var previousSheetContentState by remember { mutableStateOf<SheetContentState?>(null) }
    val isSheetExpandedOrExpanding =
        scaffoldSheetState.bottomSheetState.currentValue == SheetValue.Expanded ||
                scaffoldSheetState.bottomSheetState.targetValue == SheetValue.Expanded
    val density = LocalDensity.current
    val navConfiguration = LocalConfiguration.current
    val navHorizontalPaddingPx = with(density) { 24.dp.roundToPx() }
    val navTopPaddingPx = with(density) { (navConfiguration.screenHeightDp.dp * 0.42f).roundToPx() }
    val navBottomPaddingPx = with(density) { (navConfiguration.screenHeightDp.dp * 0.12f).roundToPx() }

    LaunchedEffect(sheetContentState, selectedStation, selectedItineraryJourney) {
        onSheetStateChanged(sheetContentState != null)
        onItineraryModeChanged(
            sheetContentState == SheetContentState.ITINERARY ||
                    sheetContentState == SheetContentState.NAVIGATION
        )
        onNavigationModeChanged(sheetContentState == SheetContentState.NAVIGATION)

        if (sheetContentState == SheetContentState.NAVIGATION) {
            requestedSheetValueForNextContent = null
            scope.launch {
                scaffoldSheetState.bottomSheetState.hide()
            }
            previousSheetContentState = sheetContentState
            return@LaunchedEffect
        }

        val requestedValue = requestedSheetValueForNextContent
        if (requestedValue != null &&
            sheetContentState != null &&
            sheetContentState != previousSheetContentState
        ) {
            scope.launch {
                when (requestedValue) {
                    SheetValue.Expanded -> scaffoldSheetState.bottomSheetState.expand()
                    SheetValue.PartiallyExpanded -> scaffoldSheetState.bottomSheetState.partialExpand()
                    SheetValue.Hidden -> scaffoldSheetState.bottomSheetState.hide()
                }
            }
            return@LaunchedEffect
        }

        // Expand the sheet when selectedItineraryJourney becomes null
        if (selectedItineraryJourney == null && sheetContentState == SheetContentState.ITINERARY) {
            scope.launch {
                scaffoldSheetState.bottomSheetState.expand()
            }
        }

        if (sheetContentState == SheetContentState.STATION &&
            selectedStation != null &&
            previousSheetContentState != SheetContentState.STATION
        ) {
            scope.launch {
                if (previousSheetContentState == SheetContentState.LINE_DETAILS &&
                    isSheetExpandedOrExpanding
                ) {
                    scaffoldSheetState.bottomSheetState.expand()
                } else {
                    scaffoldSheetState.bottomSheetState.partialExpand()
                }
            }
        }
        // Open line details in partially expanded mode by default to preserve
        // visual continuity while still allowing users to fully expand or hide.
        // - from STATION (clicked on a line from station details)
        // - or from null but with a station selected (clicked on a stop with only one line)
        // Don't auto-expand when coming from lines menu (currentStationName is empty)
        if (sheetContentState == SheetContentState.LINE_DETAILS &&
            previousSheetContentState != SheetContentState.LINE_DETAILS &&
            (previousSheetContentState == SheetContentState.STATION ||
                    selectedLine?.currentStationName?.isNotBlank() == true)
        ) {
            scope.launch {
                if (previousSheetContentState == SheetContentState.STATION &&
                    isSheetExpandedOrExpanding
                ) {
                    scaffoldSheetState.bottomSheetState.expand()
                } else {
                    scaffoldSheetState.bottomSheetState.partialExpand()
                }
            }
        }
        // Partial expand (show sheet but collapsed) when clicking directly on a line from the map
        // (coming from null state with no station selected)
        if (sheetContentState == SheetContentState.LINE_DETAILS &&
            previousSheetContentState == null &&
            selectedLine?.currentStationName?.isBlank() == true
        ) {
            scope.launch {
                scaffoldSheetState.bottomSheetState.partialExpand()
            }
        }

        if (sheetContentState == SheetContentState.ITINERARY &&
            previousSheetContentState != SheetContentState.ITINERARY
        ) {
            scope.launch {
                // Itinerary opens expanded by default.
                scaffoldSheetState.bottomSheetState.expand()
            }
        }

        // Keep transition history in sync for the next state change.
        previousSheetContentState = sheetContentState
    }

    var itinerarySearchTarget by remember { mutableStateOf<ItineraryFieldTarget?>(null) }
    var itinerarySearchFocusNonce by remember { mutableIntStateOf(0) }

    // Initialize itinerary defaults when opening inline itinerary mode:
    // - arrival = selected stop used to launch itinerary
    // - departure = nearest stop to current user location
    LaunchedEffect(sheetContentState, itineraryInitialStopName) {
        if (sheetContentState != SheetContentState.ITINERARY) return@LaunchedEffect
        // Let bottom-sheet opening animation start before running heavier itinerary initialization.
        kotlinx.coroutines.yield()

        val locationAtOpen = userLocation
        val stopsAtOpen = (stopsUiState as? TransportStopsUiState.Success)?.stops

        if (itineraryArrivalStop == null) {
            val arrivalName = itineraryInitialStopName?.takeIf { it.isNotBlank() }
            if (arrivalName != null) {
                val ids = viewModel.raptorRepository.resolveStopIdsByName(arrivalName)
                if (ids.isNotEmpty()) {
                    itineraryArrivalStop = SelectedStop(name = arrivalName, stopIds = ids)
                }
            }
        }

        if (itineraryDepartureStop == null) {
            if (locationAtOpen != null) {
                val nearestStops = viewModel.raptorRepository.findNearestStops(
                    latitude = locationAtOpen.latitude,
                    longitude = locationAtOpen.longitude,
                    limit = 5
                )
                val nearestStopNames = nearestStops.map { it.name }.distinct()
                itineraryNearbyDepartureStops = nearestStopNames

                val nearestStopName = nearestStopNames.firstOrNull()
                    ?: stopsAtOpen?.let { findNearestStopName(locationAtOpen, it) }
                if (!nearestStopName.isNullOrBlank()) {
                    val ids = viewModel.raptorRepository.resolveStopIdsByName(nearestStopName)
                    if (ids.isNotEmpty()) {
                        itineraryDepartureStop = SelectedStop(name = nearestStopName, stopIds = ids)
                    }
                }
            }
        }
    }

    // Auto-hide the bottom sheet when content state is null but sheet is still visible
    // This happens when navigating away (e.g. to Settings) and back: the sheet's visual state
    // (rememberSaveable) is restored as Expanded/PartiallyExpanded, but content state (remember)
    // resets to null, leaving an empty expanded sheet.
    LaunchedEffect(sheetContentState, scaffoldSheetState.bottomSheetState.currentValue) {
        if (sheetContentState == null &&
            scaffoldSheetState.bottomSheetState.currentValue != SheetValue.Hidden
        ) {
            scaffoldSheetState.bottomSheetState.hide()
        }
    }

    var previousSheetValue by remember { mutableStateOf<SheetValue?>(null) }
    LaunchedEffect(scaffoldSheetState.bottomSheetState.currentValue) {
        val current = scaffoldSheetState.bottomSheetState.currentValue
        val previous = previousSheetValue

        if (current != previous) {
            val justBecameHidden =
                previous != null && previous != SheetValue.Hidden && current == SheetValue.Hidden

            if (justBecameHidden && sheetContentState != SheetContentState.NAVIGATION) {
                sheetContentState = null
                selectedStation = null
                selectedLine = null
            }

            previousSheetValue = current
        }
    }

    // Additional effect to handle sheet dismissal by swipe or other means
    LaunchedEffect(scaffoldSheetState.bottomSheetState.isVisible) {
        if (
            !scaffoldSheetState.bottomSheetState.isVisible &&
            sheetContentState != null &&
            sheetContentState != SheetContentState.NAVIGATION
        ) {
            sheetContentState = null
            selectedStation = null
            selectedLine = null
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.any { it.value }
        if (granted) {
            startLocationUpdates(fusedLocationClient) { location ->
                if (userLocation == null) {
                    shouldCenterOnUser = true
                }
                updateMovementSpeed(location)
                userLocation = location
            }
        }
    }

    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            startLocationUpdates(fusedLocationClient) { location ->
                if (!shouldCenterOnUser && userLocation == null) {
                    shouldCenterOnUser = true
                }
                updateMovementSpeed(location)
                userLocation = location
            }
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Stop location updates when the composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            stopLocationUpdates(fusedLocationClient)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadAllLines()
        viewModel.preloadStops()
    }


    // Track the number of lines currently displayed to avoid unnecessary map updates
    var lastDisplayedLinesCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(uiState, mapInstance, mapStyleVersion) {
        val map = mapInstance ?: return@LaunchedEffect
        // Extract lines from both Success and PartialSuccess states
        val lines: List<Feature> = when (val state = uiState) {
            is TransportLinesUiState.Success -> state.lines
            is TransportLinesUiState.PartialSuccess -> state.lines
            else -> return@LaunchedEffect
        }

        // Skip if no new lines to display
        if (lines.isEmpty()) return@LaunchedEffect

        // Only update map if we have new lines (optimization to avoid redundant updates)
        if (lines.size == lastDisplayedLinesCount) return@LaunchedEffect

        // Prepare GeoJSON in background
        val allLinesGeoJson = withContext(Dispatchers.Default) {
            val featuresMeta = JsonObject().apply {
                addProperty("type", "FeatureCollection")
                val featuresArray = JsonArray()
                lines.forEach { lineFeature ->
                    val featObj = JsonObject()
                    featObj.addProperty("type", "Feature")

                    val geomObj = JsonObject()
                    geomObj.addProperty("type", lineFeature.geometry.type)
                    val coordsArray = JsonArray()
                    lineFeature.geometry.coordinates.forEach { segment ->
                        val segmentArray = JsonArray()
                        segment.forEach { point ->
                            val pointArray = JsonArray()
                            point.forEach { c -> pointArray.add(c) }
                            segmentArray.add(pointArray)
                        }
                        coordsArray.add(segmentArray)
                    }
                    geomObj.add("coordinates", coordsArray)
                    featObj.add("geometry", geomObj)

                    val propsObj = JsonObject()
                    propsObj.addProperty("ligne", lineFeature.properties.lineName)
                    propsObj.addProperty("nom_trace", lineFeature.properties.traceName)
                    propsObj.addProperty("couleur", LineColorHelper.getColorForLine(lineFeature))
                    // Determine line width property based on type
                    val upperName = lineFeature.properties.lineName.uppercase()
                    val width = when {
                        lineFeature.properties.transportType == "BAT" || isNavigoneLine(upperName) -> 2f
                        lineFeature.properties.transportType == "TRA" || lineFeature.properties.transportType == "TRAM" || upperName.startsWith(
                            "TB"
                        ) -> 2f

                        else -> 4f
                    }
                    propsObj.addProperty("line_width", width)
                    featObj.add("properties", propsObj)

                    featuresArray.add(featObj)
                }
                add("features", featuresArray)
            }
            featuresMeta.toString()
        }

        // Update Map on Main Thread
        map.getStyle { style ->
            val sourceId = "all-lines-source"
            val layerId = "all-lines-layer"

            // Clean up individual layers if they exist (migration)
            lines.forEach { feature ->
                val oldLayerId = "layer-${feature.properties.lineName}-${feature.properties.traceCode}"
                val oldSourceId = "line-${feature.properties.lineName}-${feature.properties.traceCode}"
                style.getLayer(oldLayerId)?.let { style.removeLayer(it) }
                style.getSource(oldSourceId)?.let { style.removeSource(it) }
            }

            // Check if source already exists (for incremental updates)
            val existingSource = style.getSource(sourceId) as? GeoJsonSource
            if (existingSource != null) {
                existingSource.setGeoJson(allLinesGeoJson)
            } else {
                style.getLayer(layerId)?.let { style.removeLayer(it) }
                style.addSource(GeoJsonSource(sourceId, allLinesGeoJson))

                val lineLayer = LineLayer(layerId, sourceId).apply {
                    setProperties(
                        PropertyFactory.visibility("visible"),
                        PropertyFactory.lineColor(Expression.get("couleur")),
                        PropertyFactory.lineWidth(Expression.get("line_width")),
                        PropertyFactory.lineOpacity(0.8f),
                        PropertyFactory.lineCap("round"),
                        PropertyFactory.lineJoin("round")
                    )
                }

                // Ensure lines are below stops
                val firstStopLayer = style.layers.find { it.id.startsWith("transport-stops-layer") }
                if (firstStopLayer != null) {
                    style.addLayerBelow(lineLayer, firstStopLayer.id)
                } else {
                    style.addLayer(lineLayer)
                }
            }
        }

        lastDisplayedLinesCount = lines.size
    }

    // Handle selection from Search Bar
    LaunchedEffect(searchSelectedStop, stopsUiState, mapInstance) {
        if (searchSelectedStop != null && mapInstance != null && stopsUiState is TransportStopsUiState.Success) {
            val allStops = (stopsUiState as TransportStopsUiState.Success).stops

            val targetStop = searchSelectedStop.stopId?.let { selectedId ->
                allStops.find { it.properties.id == selectedId }
            } ?: allStops.find {
                it.properties.nom.equals(searchSelectedStop.stopName, ignoreCase = true)
            }

            if (targetStop != null) {
                val lines = BusIconHelper.getAllLinesForStop(targetStop)
                val stationInfo = StationInfo(
                    nom = targetStop.properties.nom,
                    lignes = lines,
                    desserte = targetStop.properties.desserte,
                    stopIds = listOf(targetStop.properties.id)
                )

                if (sheetContentState == SheetContentState.LINE_DETAILS || sheetContentState == SheetContentState.ALL_SCHEDULES) {
                    selectedLine?.let { lineInfo ->
                        if (!isMetroTramOrFunicular(lineInfo.lineName)) {
                            viewModel.removeLineFromLoaded(lineInfo.lineName)
                        }
                    }
                    selectedLine = null
                    sheetContentState = null
                    delay(100)
                }

                zoomToStop(mapInstance!!, stationInfo.nom, allStops)

                selectedStation = stationInfo
                sheetContentState = SheetContentState.STATION

                onSearchSelectionHandled()
            }
        }
    }

    // Handle itinerary selection from top search bar to keep continuity in PlanScreen
    LaunchedEffect(itinerarySelectedStopName) {
        if (!itinerarySelectedStopName.isNullOrBlank()) {
            itineraryDepartureStop = null
            itineraryDepartureQuery = ""
            itineraryNearbyDepartureStops = emptyList()
            itineraryInitialStopName = itinerarySelectedStopName
            itineraryArrivalQuery = itinerarySelectedStopName
            sheetContentState = SheetContentState.ITINERARY
            itineraryArrivalStop =
                SelectedStop(name = itinerarySelectedStopName, stopIds = emptyList())
            val ids = viewModel.raptorRepository.resolveStopIdsByName(itinerarySelectedStopName)
            if (itineraryInitialStopName == itinerarySelectedStopName) {
                itineraryArrivalStop =
                    SelectedStop(name = itinerarySelectedStopName, stopIds = ids)
            }
            onItinerarySelectionHandled()
        }
    }

    // Handle selection from stop options (target) - mimic map click behavior
    LaunchedEffect(optionsSelectedStop, stopsUiState, mapInstance) {
        if (optionsSelectedStop != null && mapInstance != null && stopsUiState is TransportStopsUiState.Success) {
            val allStops = (stopsUiState as TransportStopsUiState.Success).stops

            val targetStop = optionsSelectedStop.stopId?.let { selectedId ->
                allStops.find { it.properties.id == selectedId }
            } ?: allStops.find {
                it.properties.nom.equals(optionsSelectedStop.stopName, ignoreCase = true)
            }

            if (targetStop != null) {
                val lines = BusIconHelper.getAllLinesForStop(targetStop)
                val stationInfo = StationInfo(
                    nom = targetStop.properties.nom,
                    lignes = lines,
                    desserte = targetStop.properties.desserte,
                    stopIds = listOf(targetStop.properties.id)
                )

                if (sheetContentState == SheetContentState.LINE_DETAILS || sheetContentState == SheetContentState.ALL_SCHEDULES) {
                    selectedLine?.let { lineInfo ->
                        if (!isMetroTramOrFunicular(lineInfo.lineName)) {
                            viewModel.removeLineFromLoaded(lineInfo.lineName)
                        }
                    }
                    selectedLine = null
                    sheetContentState = null
                    delay(100)
                }

                zoomToStop(mapInstance!!, stationInfo.nom, allStops)

                selectedStation = stationInfo
                sheetContentState = SheetContentState.STATION

                onOptionsSelectionHandled()
            }
        }
    }

    LaunchedEffect(stopsUiState, mapInstance, mapStyleVersion) {
        val map = mapInstance ?: return@LaunchedEffect

        when (val state = stopsUiState) {
            is TransportStopsUiState.Success -> {
                addStopsToMap(map, state.stops, context, onStationClick = { clickedStationInfo ->
                    scope.launch {
                        if (sheetContentState == SheetContentState.LINE_DETAILS || sheetContentState == SheetContentState.ALL_SCHEDULES) {
                            selectedLine?.let { lineInfo ->
                                val lineName = lineInfo.lineName
                                if (!isMetroTramOrFunicular(lineName)) {
                                    viewModel.removeLineFromLoaded(lineName)
                                }
                            }

                            selectedLine = null
                            sheetContentState = null

                            scaffoldSheetState.bottomSheetState.partialExpand()

                            delay(300)
                        }

                        selectedStation = clickedStationInfo
                        sheetContentState = SheetContentState.STATION
                    }
                }, onLineClick = { lineName ->
                    scope.launch {
                        // Cancel pending operations and clear states from previous line to prevent OOM
                        viewModel.resetLineDetailState()

                        // Close any existing sheet content
                        if (sheetContentState == SheetContentState.LINE_DETAILS || sheetContentState == SheetContentState.ALL_SCHEDULES) {
                            selectedLine?.let { lineInfo ->
                                val currentLineName = lineInfo.lineName
                                if (!isMetroTramOrFunicular(currentLineName)) {
                                    viewModel.removeLineFromLoaded(currentLineName)
                                }
                            }
                        }

                        selectedLine = LineInfo(
                            lineName = lineName,
                            currentStationName = ""
                        )

                        if (!isMetroTramOrFunicular(lineName)) {
                            viewModel.addLineToLoaded(lineName)
                            if (isTemporaryBus(lineName)) {
                                temporaryLoadedBusLines = temporaryLoadedBusLines + lineName
                            }
                            delay(100)
                        }

                        sheetContentState = SheetContentState.LINE_DETAILS
                    }
                }, scope = scope, viewModel = viewModel)
            }

            else -> {}
        }
    }

    LaunchedEffect(sheetContentState, selectedLine) {
        if (sheetContentState == SheetContentState.LINE_DETAILS && selectedLine != null) {
            val lineName = selectedLine!!.lineName

            if (!isMetroTramOrFunicular(lineName)) {
                viewModel.addLineToLoaded(lineName)
                if (isTemporaryBus(lineName)) {
                    temporaryLoadedBusLines = temporaryLoadedBusLines + lineName
                }
            }
        }
    }

    // Reset direction when line or stop changes (not when navigating to/from schedule details)
    LaunchedEffect(selectedLine?.lineName, selectedLine?.currentStationName) {
        if (!preserveSelectedDirectionOnce) {
            selectedDirection = 0
        }
    }

    LaunchedEffect(sheetContentState) {
        if (sheetContentState != SheetContentState.LINE_DETAILS && sheetContentState != SheetContentState.ALL_SCHEDULES && temporaryLoadedBusLines.isNotEmpty()) {
            temporaryLoadedBusLines.forEach { busLine ->
                viewModel.removeLineFromLoaded(busLine)
            }
            temporaryLoadedBusLines = emptySet()
        }

        if (sheetContentState == SheetContentState.NAVIGATION) {
            scaffoldSheetState.bottomSheetState.hide()
        }

        if (
            sheetContentState != SheetContentState.ITINERARY &&
            sheetContentState != SheetContentState.NAVIGATION
        ) {
            itineraryJourneys = emptyList()
            selectedItineraryJourney = null
        }
    }

    LaunchedEffect(selectedItineraryJourney) {
        if (selectedItineraryJourney != null) {
            itinerarySearchTarget = null
        }
    }

    LaunchedEffect(
        mapInstance,
        mapStyleVersion,
        sheetContentState,
        itineraryJourneys,
        selectedItineraryJourney,
        isMapStyleMenuExpanded
    ) {
        if (isMapStyleMenuExpanded) return@LaunchedEffect
        val map = mapInstance ?: return@LaunchedEffect

        if (
            sheetContentState != SheetContentState.ITINERARY &&
            sheetContentState != SheetContentState.NAVIGATION
        ) {
            map.getStyle { style ->
                clearItineraryLayers(style)
            }
            return@LaunchedEffect
        }

        hideMapLines(map)
        val journeysToDraw = when (sheetContentState) {
            SheetContentState.NAVIGATION -> {
                selectedItineraryJourney?.let { listOf(it) } ?: emptyList()
            }
            else -> itineraryJourneys
        }
        drawItinerariesOnMap(
            map = map,
            journeys = journeysToDraw,
            selectedJourney = selectedItineraryJourney,
            viewModel = viewModel
        )
    }

    LaunchedEffect(
        mapInstance,
        sheetContentState,
        itineraryResultsVersion,
        isMapStyleMenuExpanded
    ) {
        if (isMapStyleMenuExpanded) return@LaunchedEffect
        if (
            sheetContentState != SheetContentState.ITINERARY &&
            sheetContentState != SheetContentState.NAVIGATION
        ) return@LaunchedEffect
        val map = mapInstance ?: return@LaunchedEffect
        val journeysToZoom = when (sheetContentState) {
            SheetContentState.NAVIGATION -> {
                selectedItineraryJourney?.let { listOf(it) } ?: emptyList()
            }
            else -> itineraryJourneys
        }
        if (journeysToZoom.isEmpty()) return@LaunchedEffect

        zoomToItineraries(map, journeysToZoom)
    }

    LaunchedEffect(sheetContentState, selectedItineraryJourney) {
        if (sheetContentState != SheetContentState.NAVIGATION) {
            navigationPathPoints = emptyList()
            return@LaunchedEffect
        }

        val journey = selectedItineraryJourney
        if (journey == null) {
            navigationPathPoints = emptyList()
            return@LaunchedEffect
        }

        navigationPathPoints = withContext(Dispatchers.IO) {
            buildNavigationPathPoints(journey, viewModel)
        }
    }

    LaunchedEffect(mapInstance, sheetContentState, userLocation, navigationPathPoints) {
        val isInNavigationMode = sheetContentState == SheetContentState.NAVIGATION
        val map = mapInstance

        if (map == null) {
            wasInNavigationMode = isInNavigationMode
            return@LaunchedEffect
        }

        if (isInNavigationMode) {
            val currentUserLocation = userLocation
            val target = currentUserLocation ?: map.cameraPosition.target
            val axisSegment = if (currentUserLocation != null) {
                findNavigationAxisSegment(currentUserLocation, navigationPathPoints)
            } else {
                null
            }
            val bearing = if (axisSegment != null) {
                computeBearingDegrees(axisSegment.first, axisSegment.second)
            } else {
                map.cameraPosition.bearing
            }

            map.setPadding(
                navHorizontalPaddingPx,
                navTopPaddingPx,
                navHorizontalPaddingPx,
                navBottomPaddingPx
            )

            val navigationCamera = CameraPosition.Builder(map.cameraPosition)
                .target(target)
                .zoom(maxOf(map.cameraPosition.zoom, 17.5))
                .tilt(60.0)
                .bearing(bearing)
                .build()

            map.animateCamera(
                CameraUpdateFactory.newCameraPosition(navigationCamera),
                1000
            )
        } else if (!isInNavigationMode && wasInNavigationMode) {
            map.setPadding(0, 0, 0, 0)
            val resetCamera = CameraPosition.Builder(map.cameraPosition)
                .tilt(0.0)
                .bearing(0.0)
                .build()

            // Force immediate reset to true north + 2D when exiting navigation.
            map.moveCamera(CameraUpdateFactory.newCameraPosition(resetCamera))
        }

        wasInNavigationMode = isInNavigationMode
    }

    // Keep LIVE mode active while switching between global and per-line context.
    LaunchedEffect(
        selectedLine?.lineName,
        sheetContentState,
        isLiveTrackingEnabled,
        isGlobalLiveEnabled,
        isOffline
    ) {
        if (isOffline) return@LaunchedEffect

        val isLiveModeEnabled = isLiveTrackingEnabled || isGlobalLiveEnabled
        if (!isLiveModeEnabled) return@LaunchedEffect

        val isLineContext =
            sheetContentState == SheetContentState.LINE_DETAILS || sheetContentState == SheetContentState.ALL_SCHEDULES
        val selectedTrackableLine =
            selectedLine?.lineName?.takeIf { isLineContext && isLiveTrackableLine(it) }
        val selectedNotTrackableLine =
            selectedLine?.lineName?.takeIf { isLineContext && !isLiveTrackableLine(it) }

        if (selectedTrackableLine != null) {
            if (isGlobalLiveEnabled) {
                viewModel.startLiveTracking(selectedTrackableLine)
            }
        } else if (selectedNotTrackableLine != null) {
            if (isLiveTrackingEnabled) {
                viewModel.stopLiveTracking()
            }
            if (isGlobalLiveEnabled) {
                viewModel.stopGlobalLive()
            }
        } else {
            if (isLiveTrackingEnabled) {
                viewModel.stopLiveTracking()
            }
            if (!isGlobalLiveEnabled) {
                viewModel.toggleGlobalLive()
            }
        }
    }

    // Auto-zoom out when live tracking is enabled, restore zoom when disabled
    LaunchedEffect(isLiveTrackingEnabled) {
        val map = mapInstance ?: return@LaunchedEffect
        if (isLiveTrackingEnabled) {
            val currentZoom = map.cameraPosition.zoom
            // Save current zoom level before zooming out
            // Only zoom out if current zoom is higher than LIVE_MODE_ZOOM_LEVEL
            if (currentZoom > LIVE_MODE_ZOOM_LEVEL) {
                map.animateCamera(
                    CameraUpdateFactory.zoomTo(LIVE_MODE_ZOOM_LEVEL.toDouble()),
                    500 // Animation duration in ms
                )
            }
        } else {
            // Restore previous zoom level when live tracking is disabled
            zoomBeforeLiveTracking?.let { savedZoom ->
                map.animateCamera(
                    CameraUpdateFactory.zoomTo(savedZoom),
                    500 // Animation duration in ms
                )
            }
        }
    }

    // Update vehicle markers on the map when vehicle positions change
    LaunchedEffect(
        vehiclePositions,
        mapInstance,
        selectedLine,
        mapStyleVersion,
        isMapStyleMenuExpanded
    ) {
        if (isMapStyleMenuExpanded) return@LaunchedEffect
        val map = mapInstance ?: return@LaunchedEffect
        val positions = vehiclePositions
        val line = selectedLine

        // Ajouter un délai pour éviter les mises à jour trop fréquentes
        delay(100)

        map.getStyle { style ->
            // Remove existing vehicle layers and sources
            style.getLayer("vehicle-positions-layer")?.let { style.removeLayer(it) }
            style.getSource("vehicle-positions-source")?.let { style.removeSource(it) }

            if (positions.isEmpty() || line == null) return@getStyle

            // Create GeoJSON for vehicle positions
            val vehiclesGeoJson = JsonObject().apply {
                addProperty("type", "FeatureCollection")
                val featuresArray = JsonArray()
                positions.forEach { vehicle ->
                    val feature = JsonObject().apply {
                        addProperty("type", "Feature")
                        val geometry = JsonObject().apply {
                            addProperty("type", "Point")
                            val coords = JsonArray()
                            coords.add(vehicle.longitude)
                            coords.add(vehicle.latitude)
                            add("coordinates", coords)
                        }
                        add("geometry", geometry)
                        val props = JsonObject().apply {
                            addProperty("vehicleId", vehicle.vehicleId)
                            addProperty("lineName", vehicle.lineName)
                            addProperty("destination", vehicle.destinationName ?: "")
                        }
                        add("properties", props)
                    }
                    featuresArray.add(feature)
                }
                add("features", featuresArray)
            }.toString()

            // Add source
            val source = GeoJsonSource("vehicle-positions-source", vehiclesGeoJson)
            style.addSource(source)

            val markerColor = LineColorHelper.getColorForLineString(line.lineName)
            val markerType = getVehicleMarkerType(line.lineName)
            val iconName = "vehicle-marker-line-${markerType.name.lowercase()}-${
                Integer.toHexString(markerColor)
            }"
            ensureVehicleMarkerImage(
                mapStyle = style,
                context = context,
                iconName = iconName,
                color = markerColor,
                markerType = markerType,
                size = 72
            )

            // Add symbol layer with bus marker
            val symbolLayer =
                SymbolLayer("vehicle-positions-layer", "vehicle-positions-source").apply {
                    setProperties(
                        PropertyFactory.iconImage(iconName),
                        PropertyFactory.iconSize(1.0f),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true)
                    )
                }
            style.addLayer(symbolLayer)
        }
    }

    // Global live map: render ALL vehicles with per-line colored markers
    LaunchedEffect(globalVehiclePositions, mapInstance, mapStyleVersion, isMapStyleMenuExpanded) {
        if (isMapStyleMenuExpanded) return@LaunchedEffect
        val map = mapInstance ?: return@LaunchedEffect
        val positions = globalVehiclePositions

        // Ajouter un délai pour éviter les mises à jour trop fréquentes
        delay(100)

        map.getStyle { style ->
            // Clean up existing global layers/sources
            style.getLayer("global-vehicle-positions-layer")?.let { style.removeLayer(it) }
            style.getSource("global-vehicle-positions-source")?.let { style.removeSource(it) }

            if (positions.isEmpty()) return@getStyle

            // Generate colored/type-specific marker icons per unique (type,color)
            val iconCache = mutableMapOf<String, String>()

            // Build GeoJSON with per-vehicle icon property
            val vehiclesGeoJson = JsonObject().apply {
                addProperty("type", "FeatureCollection")
                val featuresArray = JsonArray()
                positions.forEach { vehicle ->
                    val lineColor = LineColorHelper.getColorForLineString(vehicle.lineName)
                    val markerType = getVehicleMarkerType(vehicle.lineName)
                    val cacheKey = "${markerType.name}-${lineColor}"
                    val iconName = iconCache.getOrPut(cacheKey) {
                        val name = "global-vehicle-marker-${markerType.name.lowercase()}-${
                            Integer.toHexString(lineColor)
                        }"
                        ensureVehicleMarkerImage(
                            mapStyle = style,
                            context = context,
                            iconName = name,
                            color = lineColor,
                            markerType = markerType,
                            size = 56
                        )
                        name
                    }

                    val feature = JsonObject().apply {
                        addProperty("type", "Feature")
                        val geometry = JsonObject().apply {
                            addProperty("type", "Point")
                            val coords = JsonArray()
                            coords.add(vehicle.longitude)
                            coords.add(vehicle.latitude)
                            add("coordinates", coords)
                        }
                        add("geometry", geometry)
                        val props = JsonObject().apply {
                            addProperty("vehicleId", vehicle.vehicleId)
                            addProperty("lineName", vehicle.lineName)
                            addProperty("destination", vehicle.destinationName ?: "")
                            addProperty("icon", iconName)
                        }
                        add("properties", props)
                    }
                    featuresArray.add(feature)
                }
                add("features", featuresArray)
            }.toString()

            val source = GeoJsonSource("global-vehicle-positions-source", vehiclesGeoJson)
            style.addSource(source)

            val symbolLayer = SymbolLayer(
                "global-vehicle-positions-layer",
                "global-vehicle-positions-source"
            ).apply {
                setProperties(
                    PropertyFactory.iconImage(Expression.get("icon")),
                    PropertyFactory.iconSize(0.85f),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true)
                )
            }
            style.addLayer(symbolLayer)
        }
    }

    // Auto-zoom when global live is toggled
    var zoomBeforeGlobalLive by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(isGlobalLiveEnabled) {
        val map = mapInstance ?: return@LaunchedEffect
        if (isGlobalLiveEnabled) {
            val currentZoom = map.cameraPosition.zoom
            if (currentZoom > 11.0) {
                map.animateCamera(CameraUpdateFactory.zoomTo(11.0), 500)
            }
        } else {
            zoomBeforeGlobalLive?.let { savedZoom ->
                map.animateCamera(CameraUpdateFactory.zoomTo(savedZoom), 500)
            }
        }
    }

    LaunchedEffect(showLinesSheet, sheetContentState) {
        if (!showLinesSheet && sheetContentState != SheetContentState.LINE_DETAILS && sheetContentState != SheetContentState.ALL_SCHEDULES && temporaryLoadedBusLines.isNotEmpty()) {
            temporaryLoadedBusLines.forEach { busLine ->
                viewModel.removeLineFromLoaded(busLine)
            }
            temporaryLoadedBusLines = emptySet()
        }
    }

    // Use snapshotFlow with debounce to avoid overwhelming the map when user changes stations rapidly.
    // collectLatest automatically cancels previous collection when new values arrive.
    @OptIn(FlowPreview::class)
    LaunchedEffect(mapInstance, mapStyleVersion, isMapStyleMenuExpanded, lastDisplayedLinesCount) {
        if (isMapStyleMenuExpanded) return@LaunchedEffect
        val map = mapInstance ?: return@LaunchedEffect

        snapshotFlow {
            // Capture all relevant state as a tuple
            MapFilterState(
                sheetContentState = sheetContentState,
                selectedLine = selectedLine,
                uiState = uiState,
                stopsUiState = stopsUiState
            )
        }
            .debounce(300)
            .distinctUntilChanged()
            .collectLatest { filterState ->
                // This block is automatically cancelled if a new state arrives
                // Extract lines from both Success and PartialSuccess states
                val lines: List<Feature> = when (val state = filterState.uiState) {
                    is TransportLinesUiState.Success -> state.lines
                    is TransportLinesUiState.PartialSuccess -> state.lines
                    else -> return@collectLatest
                }

                val currentSelectedLine = filterState.selectedLine
                val currentSheetState = filterState.sheetContentState

                if ((currentSheetState == SheetContentState.LINE_DETAILS || currentSheetState == SheetContentState.ALL_SCHEDULES) && currentSelectedLine != null) {
                    val selectedName = currentSelectedLine.lineName
                    val hasSelectedInState =
                        lines.any { areEquivalentLineNames(it.properties.lineName, selectedName) }

                    if (!hasSelectedInState && isMetroTramOrFunicular(selectedName)) {
                        viewModel.reloadStrongLines()
                    }

                    filterMapLines(map, lines, currentSelectedLine.lineName)

                    val selectedStopName =
                        currentSelectedLine.currentStationName.takeIf { it.isNotBlank() }
                    when (val stopsState = filterState.stopsUiState) {
                        is TransportStopsUiState.Success -> {
                            filterMapStopsWithSelectedStop(
                                map,
                                currentSelectedLine.lineName,
                                selectedStopName,
                                stopsState.stops,
                                lines,
                                viewModel
                            )

                            if (selectedStopName != null) {
                                zoomToStop(map, selectedStopName, stopsState.stops)
                            } else {
                                zoomToLine(map, lines, currentSelectedLine.lineName)
                            }
                        }

                        else -> {}
                    }
                } else if (
                    currentSheetState == SheetContentState.ITINERARY ||
                    currentSheetState == SheetContentState.NAVIGATION
                ) {
                    hideMapLines(map)
                } else {
                    showAllMapLines(map, lines)
                }
            }
    }

    // Observe selection from viewModel (e.g. when Lines screen clicks a line)
    LaunchedEffect(selectedLineNameFromViewModel) {
        val name = selectedLineNameFromViewModel
        if (!name.isNullOrEmpty()) {
            selectedLine = LineInfo(
                lineName = name,
                currentStationName = ""
            )

            // if not a strong line, add it to loaded lines
            if (!isMetroTramOrFunicular(name)) {
                viewModel.addLineToLoaded(name)
                if (isTemporaryBus(name)) {
                    temporaryLoadedBusLines = temporaryLoadedBusLines + name
                }
                delay(100)
            }

            sheetContentState = SheetContentState.LINE_DETAILS
            viewModel.clearSelectedLine()
        }
    }

    val bottomPadding = contentPadding.calculateBottomPadding()
    val configuration = LocalConfiguration.current
    val itinerarySearchOverlayHeight = 174.dp
    val itinerarySheetSafetyOffset = 90.dp
    val itinerarySearchReserved = remember(sheetContentState, selectedItineraryJourney) {
        if (sheetContentState == SheetContentState.ITINERARY && selectedItineraryJourney == null) {
            itinerarySearchOverlayHeight
        } else {
            0.dp
        }
    }
    val itinerarySheetMaxHeight =
        (configuration.screenHeightDp.dp - itinerarySearchReserved - bottomPadding - itinerarySheetSafetyOffset)
            .coerceAtLeast(280.dp)

    // Handle back button press - close sheets/selections before exiting app
    BackHandler(enabled = sheetContentState != null || selectedLine != null || selectedStation != null || itinerarySearchTarget != null) {
        when {
            itinerarySearchTarget != null -> {
                // If search overlay is active, dismiss it first
                itinerarySearchTarget = null
            }
            sheetContentState == SheetContentState.ALL_SCHEDULES -> {
                requestedSheetValueForNextContent = if (isSheetExpandedOrExpanding) {
                    SheetValue.Expanded
                } else {
                    SheetValue.PartiallyExpanded
                }
                allSchedulesInfo = null
                sheetContentState = SheetContentState.LINE_DETAILS
            }

            sheetContentState == SheetContentState.ITINERARY -> {
                sheetContentState = null
                itineraryInitialStopName = null
                itineraryDepartureStop = null
                itineraryArrivalStop = null
                itineraryDepartureQuery = ""
                itineraryArrivalQuery = ""
            }
            sheetContentState == SheetContentState.NAVIGATION -> {
                requestedSheetValueForNextContent = SheetValue.Expanded
                sheetContentState = SheetContentState.ITINERARY
            }
            // If viewing line details, go back to station (if came from station) or close
            sheetContentState == SheetContentState.LINE_DETAILS -> {
                // Clean up temporary bus lines
                selectedLine?.let { lineInfo ->
                    val lineName = lineInfo.lineName
                    if (!isMetroTramOrFunicular(lineName)) {
                        viewModel.removeLineFromLoaded(lineName)
                    }
                }
                if (selectedStation != null) {
                    // Go back to station view when line details were opened from a stop
                    selectedLine = null
                    sheetContentState = SheetContentState.STATION
                } else {
                    // Close everything
                    selectedLine = null
                    selectedStation = null
                    sheetContentState = null
                }
            }
            // If viewing station, close it
            sheetContentState == SheetContentState.STATION -> {
                selectedStation = null
                sheetContentState = null
            }
            // Default: close any selection
            else -> {
                selectedLine = null
                selectedStation = null
                sheetContentState = null
            }
        }
    }

    val stationCollapsedPeekHeight = bottomPadding + 300.dp
    val itineraryCollapsedPeekHeight = bottomPadding + 100.dp
    val peekHeight = when (sheetContentState) {
        SheetContentState.LINE_DETAILS -> stationCollapsedPeekHeight
        SheetContentState.ALL_SCHEDULES -> stationCollapsedPeekHeight
        SheetContentState.STATION -> stationCollapsedPeekHeight
        SheetContentState.ITINERARY -> itineraryCollapsedPeekHeight
        SheetContentState.NAVIGATION -> 0.dp
        else -> 0.dp
    }
    val unifiedSheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

    BottomSheetScaffold(
        scaffoldState = scaffoldSheetState,
        sheetPeekHeight = peekHeight,
        sheetShape = unifiedSheetShape,
        modifier = modifier,
        sheetContainerColor = SecondaryColor,
        sheetContent = {
            if (sheetContentState == SheetContentState.NAVIGATION) {
                Spacer(modifier = Modifier.height(0.dp))
            } else {
                Column(
                    modifier = Modifier
                        .padding(bottom = bottomPadding)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (sheetContentState) {
                        SheetContentState.LINE_DETAILS -> {
                            if (selectedLine != null) {
                                LineDetailsSheetContent(
                                    lineInfo = selectedLine!!,
                                    viewModel = viewModel,
                                    selectedDirection = selectedDirection,
                                    onDirectionChange = { newDirection ->
                                        selectedDirection = newDirection
                                    },
                                    onBackToStation = {
                                        selectedLine?.let { lineInfo ->
                                            val lineName = lineInfo.lineName
                                            if (!isMetroTramOrFunicular(lineName)) {
                                                viewModel.removeLineFromLoaded(lineName)
                                            }
                                        }

                                        if (selectedStation != null) {
                                            requestedSheetValueForNextContent =
                                                if (isSheetExpandedOrExpanding) {
                                                    SheetValue.Expanded
                                                } else {
                                                    SheetValue.PartiallyExpanded
                                                }
                                            selectedLine = null
                                            sheetContentState = SheetContentState.STATION
                                        } else {
                                            scope.launch {
                                                scaffoldSheetState.bottomSheetState.hide()
                                            }
                                            selectedLine = null
                                            selectedStation = null
                                            sheetContentState = null
                                        }
                                    },
                                    onLineClick = { lineName ->
                                        // Cancel pending operations and clear states from previous line to prevent OOM
                                        viewModel.resetLineDetailState()

                                        selectedLine = LineInfo(
                                            lineName = lineName,
                                            currentStationName = selectedLine?.currentStationName ?: ""
                                        )

                                        if (!isMetroTramOrFunicular(lineName)) {
                                            scope.launch {
                                                viewModel.addLineToLoaded(lineName)
                                                if (isTemporaryBus(lineName)) {
                                                    temporaryLoadedBusLines =
                                                        temporaryLoadedBusLines + lineName
                                                }
                                                delay(100)
                                                sheetContentState = SheetContentState.LINE_DETAILS
                                            }
                                        } else {
                                            sheetContentState = SheetContentState.LINE_DETAILS
                                        }
                                    },
                                    onStopClick = { stopName ->
                                        // Clear schedule state to prevent stale "Aucun horaire" message
                                        viewModel.clearScheduleState()

                                        // Preserve current direction when navigating to another stop
                                        // from the line details stops list.
                                        preserveSelectedDirectionOnce = true

                                        // Keep station state aligned with the last stop selected from line details,
                                        // so Back returns to this stop instead of the initial one.
                                        val matchingStop =
                                            (stopsUiState as? TransportStopsUiState.Success)
                                                ?.stops
                                                ?.find {
                                                    it.properties.nom.equals(
                                                        stopName,
                                                        ignoreCase = true
                                                    )
                                                }
                                        selectedStation = if (matchingStop != null) {
                                            StationInfo(
                                                nom = matchingStop.properties.nom,
                                                lignes = BusIconHelper.getAllLinesForStop(matchingStop),
                                                desserte = matchingStop.properties.desserte,
                                                stopIds = listOf(matchingStop.properties.id)
                                            )
                                        } else {
                                            StationInfo(
                                                nom = stopName,
                                                lignes = selectedStation?.lignes ?: emptyList(),
                                                desserte = selectedStation?.desserte ?: "",
                                                stopIds = selectedStation?.stopIds ?: emptyList()
                                            )
                                        }

                                        selectedLine = LineInfo(
                                            lineName = selectedLine!!.lineName,
                                            currentStationName = selectedStation?.nom ?: stopName
                                        )
                                        scope.launch {
                                            scaffoldSheetState.bottomSheetState.partialExpand()
                                        }
                                    },
                                    onShowAllSchedules = { lineName, directionName, schedules ->
                                        requestedSheetValueForNextContent =
                                            if (isSheetExpandedOrExpanding) {
                                                SheetValue.Expanded
                                            } else {
                                                SheetValue.PartiallyExpanded
                                            }
                                        allSchedulesInfo = AllSchedulesInfo(
                                            lineName = lineName,
                                            directionName = directionName,
                                            schedules = schedules,
                                            availableDirections = availableDirections,
                                            headsigns = headsigns
                                        )
                                        sheetContentState = SheetContentState.ALL_SCHEDULES
                                    },
                                    onItineraryClick = { stopName ->
                                        requestedSheetValueForNextContent = SheetValue.Expanded
                                        itineraryDepartureStop = null
                                        itineraryDepartureQuery = ""
                                        itineraryNearbyDepartureStops = emptyList()
                                        itineraryInitialStopName = stopName
                                        itineraryArrivalQuery = stopName
                                        itineraryArrivalStop = SelectedStop(
                                            name = stopName,
                                            stopIds = emptyList()
                                        )
                                        sheetContentState = SheetContentState.ITINERARY
                                        scope.launch {
                                            val ids =
                                                viewModel.raptorRepository.resolveStopIdsByName(stopName)
                                            if (itineraryInitialStopName == stopName) {
                                                itineraryArrivalStop = SelectedStop(
                                                    name = stopName,
                                                    stopIds = ids)
                                            }
                                        }
                                    },
                                    onHeaderClick = {
                                        scope.launch {
                                            scaffoldSheetState.bottomSheetState.expand()
                                        }
                                    },
                                    favoriteStops = favoriteStops,
                                    onToggleFavoriteStop = { viewModel.toggleFavoriteStop(it) },
                                    onHeaderLineCountChanged = { _ -> }
                                )
                            }
                        }

                        SheetContentState.STATION -> {
                            if (selectedStation != null) {
                                StationSheetContent(
                                    stationInfo = selectedStation!!,
                                    viewModel = viewModel,
                                    onDismiss = {
                                        scope.launch {
                                            scaffoldSheetState.bottomSheetState.hide()
                                        }
                                        sheetContentState = null
                                    },
                                    onDepartureClick = { lineName, directionId, _ ->
                                        // Cancel pending operations and clear states from previous line to prevent OOM
                                        viewModel.resetLineDetailState()
                                        val shouldKeepExpanded =
                                            scaffoldSheetState.bottomSheetState.currentValue == SheetValue.Expanded ||
                                                    scaffoldSheetState.bottomSheetState.targetValue == SheetValue.Expanded
                                        requestedSheetValueForNextContent = if (shouldKeepExpanded) {
                                            SheetValue.Expanded
                                        } else {
                                            SheetValue.PartiallyExpanded
                                        }

                                        preserveSelectedDirectionOnce = true
                                        selectedDirection = directionId

                                        selectedLine = LineInfo(
                                            lineName = lineName,
                                            currentStationName = selectedStation?.nom ?: ""
                                        )

                                        if (!isMetroTramOrFunicular(lineName)) {
                                            scope.launch {
                                                viewModel.addLineToLoaded(lineName)
                                                if (isTemporaryBus(lineName)) {
                                                    temporaryLoadedBusLines =
                                                        temporaryLoadedBusLines + lineName
                                                }
                                                delay(100)
                                                sheetContentState = SheetContentState.LINE_DETAILS
                                            }
                                        } else {
                                            sheetContentState = SheetContentState.LINE_DETAILS
                                        }
                                    },
                                    isFavoriteStop = favoriteStops.any {
                                        it.equals(
                                            selectedStation!!.nom,
                                            ignoreCase = true
                                        )
                                    },
                                    onToggleFavoriteStop = {
                                        viewModel.toggleFavoriteStop(
                                            selectedStation!!.nom
                                        )
                                    },
                                    onAddFavoriteClick = { stopName ->
                                        addFavoriteInitialStopName = stopName
                                        showAddFavoriteDialog = true
                                        requestedSheetValueForNextContent = null
                                        selectedLine = null
                                        selectedStation = null
                                        sheetContentState = null
                                        scope.launch {
                                            scaffoldSheetState.bottomSheetState.hide()
                                        }
                                    },
                                    onItineraryClick = { stopName ->
                                        requestedSheetValueForNextContent = SheetValue.Expanded
                                        itineraryDepartureStop = null
                                        itineraryDepartureQuery = ""
                                        itineraryNearbyDepartureStops = emptyList()
                                        itineraryInitialStopName = stopName
                                        itineraryArrivalQuery = stopName
                                        itineraryArrivalStop = SelectedStop(
                                            name = stopName,
                                            stopIds = emptyList()
                                        )
                                        sheetContentState = SheetContentState.ITINERARY
                                        scope.launch {
                                            val ids =
                                                viewModel.raptorRepository.resolveStopIdsByName(stopName)
                                            if (itineraryInitialStopName == stopName) {
                                                itineraryArrivalStop = SelectedStop(
                                                    name = stopName,
                                                    stopIds = ids)
                                            }
                                        }
                                    },
                                    onReportAlertClick = { stopName, lines ->
                                        alertReportInitialStop = StationSearchResult(
                                            stopName = stopName,
                                            lines = lines,
                                            stopId = selectedStation?.stopIds?.firstOrNull() ?: 0
                                        )
                                        showAlertReportSheet = true
                                        scope.launch {
                                            scaffoldSheetState.bottomSheetState.hide()
                                        }
                                    }
                                )
                            }
                        }

                        SheetContentState.ALL_SCHEDULES -> {
                            if (allSchedulesInfo != null) {
                                val schedulesForCurrentDirection =
                                    allSchedules.ifEmpty { allSchedulesInfo!!.schedules }
                                val resolvedAllSchedulesInfo = allSchedulesInfo!!.copy(
                                    directionName = headsigns[selectedDirection]
                                        ?: allSchedulesInfo!!.directionName,
                                    schedules = schedulesForCurrentDirection
                                )
                                val allSchedulesDirections =
                                    allSchedulesInfo!!.availableDirections.ifEmpty {
                                        availableDirections
                                    }
                                val allSchedulesHeadsigns = allSchedulesInfo!!.headsigns.ifEmpty {
                                    headsigns
                                }
                                AllSchedulesSheetContent(
                                    allSchedulesInfo = resolvedAllSchedulesInfo,
                                    lineInfo = selectedLine!!,
                                    selectedDirection = selectedDirection,
                                    availableDirections = allSchedulesDirections,
                                    headsigns = allSchedulesHeadsigns,
                                    onDirectionChange = { newDirection ->
                                        selectedDirection = newDirection
                                        selectedLine?.currentStationName?.takeIf { it.isNotBlank() }
                                            ?.let { stopName ->
                                                scope.launch {
                                                    viewModel.loadSchedulesForDirection(
                                                        lineName = selectedLine!!.lineName,
                                                        stopName = stopName,
                                                        directionId = newDirection
                                                    )
                                                }
                                            }
                                    },
                                    onBack = {
                                        requestedSheetValueForNextContent =
                                            if (isSheetExpandedOrExpanding) {
                                                SheetValue.Expanded
                                            } else {
                                                SheetValue.PartiallyExpanded
                                            }
                                        sheetContentState = SheetContentState.LINE_DETAILS
                                    }
                                )
                            }
                        }

                        SheetContentState.ITINERARY -> {
                            InlineItinerarySheetContent(
                                viewModel = viewModel,
                                departureStop = itineraryDepartureStop,
                                arrivalStop = itineraryArrivalStop,
                                maxHeight = itinerarySheetMaxHeight,
                                nearbyDepartureStops = itineraryNearbyDepartureStops,
                                onDepartureFallbackSelected = { fallbackDeparture ->
                                    itineraryDepartureStop = fallbackDeparture
                                },
                                onJourneysChanged = { journeys ->
                                    itineraryJourneys = journeys
                                    itineraryResultsVersion++
                                },
                                onSelectedJourneyChanged = { journey ->
                                    selectedItineraryJourney = journey
                                },
                                onStartNavigation = { journey ->
                                    selectedItineraryJourney = journey
                                    requestedSheetValueForNextContent = null
                                    sheetContentState = SheetContentState.NAVIGATION
                                    scope.launch {
                                        scaffoldSheetState.bottomSheetState.hide()
                                    }
                                },
                                onClose = {
                                    scope.launch {
                                        scaffoldSheetState.bottomSheetState.hide()
                                    }
                                    itineraryInitialStopName = null
                                    itineraryDepartureStop = null
                                    itineraryArrivalStop = null
                                    itineraryDepartureQuery = ""
                                    itineraryArrivalQuery = ""
                                    itineraryNearbyDepartureStops = emptyList()
                                    sheetContentState = null
                                },
                                onRequestExpandSheet = {
                                    requestedSheetValueForNextContent = SheetValue.Expanded
                                }
                            )
                        }

                        SheetContentState.NAVIGATION -> {}
                        null -> {}
                    }
                }
            }
        }
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            val isNavigationMode = sheetContentState == SheetContentState.NAVIGATION
            val navigationNowSeconds by produceState(
                initialValue = currentTimeInSeconds(),
                key1 = isNavigationMode,
                key2 = selectedItineraryJourney
            ) {
                value = currentTimeInSeconds()
                while (isNavigationMode && selectedItineraryJourney != null) {
                    delay(10_000)
                    value = currentTimeInSeconds()
                }
            }

            // Pre-compute nearest stop for alert button to avoid O(n) search on click
            val nearestAlertStop by remember {
                derivedStateOf {
                    if (sheetContentState != SheetContentState.NAVIGATION || userLocation == null) null
                    else {
                        val stops = (stopsUiState as? TransportStopsUiState.Success)?.stops
                        stops?.minByOrNull { stop ->
                            val coords = stop.geometry.coordinates
                            if (coords.size >= 2) squaredDistance(
                                lat1 = userLocation!!.latitude,
                                lon1 = userLocation!!.longitude,
                                lat2 = coords[1],
                                lon2 = coords[0]
                            ) else Double.MAX_VALUE
                        }
                    }
                }
            }

            var showAlertDialog by remember { mutableStateOf(false) }
            var currentAlertStop by remember { mutableStateOf<JourneyLeg?>(null) }
            var currentAlertPrompt by remember { mutableStateOf<NavigationAlertPrompt?>(null) }
            var isSubmittingNavigationAlert by remember { mutableStateOf(false) }
            var answeredAlertStopsInSession by remember { mutableStateOf<Set<String>>(emptySet()) }
            var navigationAlertSessionKey by remember { mutableStateOf<String?>(null) }
            var cachedJourneyAlerts by remember { mutableStateOf<UserStopAlertsResponse>(emptyMap()) }
            var isJourneyAlertsLoaded by remember { mutableStateOf(false) }
            var showRecalculatedJourneyChoice by remember { mutableStateOf(false) }
            var recalculatedJourneyChoices by remember { mutableStateOf<List<JourneyResult>>(emptyList()) }

            LaunchedEffect(isNavigationMode, selectedItineraryJourney) {
                if (!isNavigationMode) {
                    cachedJourneyAlerts = emptyMap()
                    isJourneyAlertsLoaded = false
                    showRecalculatedJourneyChoice = false
                    recalculatedJourneyChoices = emptyList()
                    return@LaunchedEffect
                }

                val journey = selectedItineraryJourney ?: return@LaunchedEffect
                val journeySessionKey = buildJourneyAlertSessionKey(journey)
                if (navigationAlertSessionKey != journeySessionKey) {
                    navigationAlertSessionKey = journeySessionKey
                    answeredAlertStopsInSession = emptySet()
                    showAlertDialog = false
                    currentAlertStop = null
                    currentAlertPrompt = null
                    isSubmittingNavigationAlert = false
                    isJourneyAlertsLoaded = false
                    showRecalculatedJourneyChoice = false
                    recalculatedJourneyChoices = emptyList()
                }

                if (isJourneyAlertsLoaded) return@LaunchedEffect

                val allStopNames = journey.legs
                    .flatMap { leg -> listOf(leg.fromStopName, leg.toStopName) }
                    .distinct()
                cachedJourneyAlerts = viewModel.userStopAlertsRepository.getUserStopAlerts(allStopNames)
                isJourneyAlertsLoaded = true
            }

            LaunchedEffect(
                isNavigationMode,
                selectedItineraryJourney,
                navigationNowSeconds,
                userLocation
            ) {
                if (!isNavigationMode) {
                    lastLateTransferRecalcKey = null
                    isLateTransferRecalculating = false
                    showAlertDialog = false
                    currentAlertStop = null
                    currentAlertPrompt = null
                    isSubmittingNavigationAlert = false
                    answeredAlertStopsInSession = emptySet()
                    navigationAlertSessionKey = null
                    cachedJourneyAlerts = emptyMap()
                    isJourneyAlertsLoaded = false
                    showRecalculatedJourneyChoice = false
                    recalculatedJourneyChoices = emptyList()
                    return@LaunchedEffect
                }
                val journey = selectedItineraryJourney ?: return@LaunchedEffect
                val journeySessionKey = buildJourneyAlertSessionKey(journey)
                if (navigationAlertSessionKey != journeySessionKey) {
                    navigationAlertSessionKey = journeySessionKey
                    answeredAlertStopsInSession = emptySet()
                    showAlertDialog = false
                    currentAlertStop = null
                    currentAlertPrompt = null
                    isSubmittingNavigationAlert = false
                    isJourneyAlertsLoaded = false
                    showRecalculatedJourneyChoice = false
                    recalculatedJourneyChoices = emptyList()
                }
                if (!isJourneyAlertsLoaded) return@LaunchedEffect
                val alerts = cachedJourneyAlerts

                val (currentLeg, nextLeg) =
                    getCurrentAndNextNavigationLeg(journey, navigationNowSeconds, userLocation)
                if (currentLeg == null || nextLeg == null) return@LaunchedEffect

                val approachingStop = findApproachingAlertStop(
                    journey = journey,
                    currentLeg = currentLeg,
                    nextLeg = nextLeg,
                    userLocation = userLocation,
                    nowSeconds = navigationNowSeconds
                )

                if (approachingStop != null &&
                    alerts.containsKey(approachingStop.toStopName) &&
                    approachingStop.toStopName !in answeredAlertStopsInSession
                ) {
                    currentAlertPrompt = buildNavigationAlertPrompt(
                        alerts = alerts,
                        stopName = approachingStop.toStopName
                    )
                    if (currentAlertPrompt == null) return@LaunchedEffect
                    currentAlertStop = approachingStop
                    showAlertDialog = true
                }
                val remainingSeconds = computeRemainingJourneySeconds(journey, navigationNowSeconds)
                val atTerminus = isNearestJourneyStopTerminus(journey, userLocation)
                if (remainingSeconds < 60 && atTerminus) {
                    requestedSheetValueForNextContent = SheetValue.Expanded
                    sheetContentState = SheetContentState.ITINERARY
                    return@LaunchedEffect
                }

                val overdueKeyStop = findOverdueNavigationKeyStop(
                    journey = journey,
                    userLocation = userLocation,
                    nowSeconds = navigationNowSeconds
                )
                if (overdueKeyStop == null || isLateTransferRecalculating) {
                    return@LaunchedEffect
                }

                val recalcKey =
                    "${journey.departureTime}_${journey.arrivalTime}_${overdueKeyStop.stopId}_${overdueKeyStop.deadlineSeconds}_${overdueKeyStop.type}"
                if (lastLateTransferRecalcKey == recalcKey) return@LaunchedEffect

                lastLateTransferRecalcKey = recalcKey
                isLateTransferRecalculating = true
                try {
                    val departureIds =
                        overdueKeyStop.stopId.toIntOrNull()?.let { listOf(it) } ?: emptyList()
                    if (departureIds.isEmpty()) return@LaunchedEffect

                    val destinationName =
                        itineraryArrivalStop?.name?.takeIf { it.isNotBlank() }
                            ?: journey.legs.lastOrNull { !it.isWalking }?.toStopName
                    val destinationIds = when {
                        itineraryArrivalStop?.stopIds?.isNotEmpty() == true ->
                            itineraryArrivalStop!!.stopIds
                                .distinct()
                                .take(AUTO_RECALC_MAX_STOP_IDS)

                        destinationName != null -> viewModel.raptorRepository.resolveStopIdsByName(
                            stopName = destinationName,
                            maxIds = AUTO_RECALC_MAX_STOP_IDS
                        )

                        else -> emptyList()
                    }
                    if (destinationIds.isEmpty()) return@LaunchedEffect

                    val recalculatedJourneys = withContext(Dispatchers.IO) {
                        viewModel.raptorRepository.getOptimizedPaths(
                            originStopIds = departureIds.toList(),
                            destinationStopIds = destinationIds,
                            departureTimeSeconds = navigationNowSeconds
                        )
                    }

                    when {
                        recalculatedJourneys.isEmpty() -> {
                            requestedSheetValueForNextContent = SheetValue.Expanded
                            sheetContentState = SheetContentState.ITINERARY
                        }

                        recalculatedJourneys.size == 1 -> {
                            itineraryJourneys = recalculatedJourneys
                            itineraryResultsVersion++
                            selectedItineraryJourney = recalculatedJourneys.first()
                            showRecalculatedJourneyChoice = false
                            recalculatedJourneyChoices = emptyList()
                        }

                        else -> {
                            recalculatedJourneyChoices = recalculatedJourneys
                            showRecalculatedJourneyChoice = true
                        }
                    }
                } finally {
                    isLateTransferRecalculating = false
                }
            }

            if (showRecalculatedJourneyChoice && recalculatedJourneyChoices.isNotEmpty()) {
                AlertDialog(
                    onDismissRequest = {
                        showRecalculatedJourneyChoice = false
                        recalculatedJourneyChoices = emptyList()
                    },
                    title = {
                        Text("Plusieurs itinéraires disponibles")
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            recalculatedJourneyChoices.forEachIndexed { index, option ->
                                TextButton(
                                    onClick = {
                                        itineraryJourneys = recalculatedJourneyChoices
                                        itineraryResultsVersion++
                                        selectedItineraryJourney = option
                                        showRecalculatedJourneyChoice = false
                                        recalculatedJourneyChoices = emptyList()
                                    }
                                ) {
                                    Text(
                                        text = "${index + 1}. ${option.formatDepartureTime()} → ${option.formatArrivalTime()} (${option.durationMinutes} min)"
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = {
                            showRecalculatedJourneyChoice = false
                            recalculatedJourneyChoices = emptyList()
                        }) {
                            Text("Garder l'actuel")
                        }
                    }
                )
            }

            MapLibreView(
                modifier = Modifier.fillMaxSize(),
                initialPosition = LatLng(45.75, 4.85),
                initialZoom = 12.0,
                styleUrl = mapStyleUrl,
                onMapReady = { map ->
                    if (mapInstance === map) {
                        // Same map instance → style was reloaded, bump version to re-trigger LaunchedEffects
                        mapStyleVersion++
                    } else {
                        mapInstance = map
                        // Add listener to detect when user moves the map
                        map.addOnCameraMoveStartedListener { reason ->
                            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                                isCenteredOnUser = false
                            }
                        }
                    }
                },
                userLocation = userLocation,
                centerOnUserLocation = shouldCenterOnUser,
                isInteractive = true
            )

            if (
                !isNavigationMode &&
                (uiState is TransportLinesUiState.Loading || stopsUiState is TransportStopsUiState.Loading)
            ) {
                // Show skeleton loading instead of spinner for better UX
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF3B82F6)
                    )
                }
            }

            // Navigation alert validation dialog (progressive, per-stop)
            if (showAlertDialog && currentAlertStop != null && currentAlertPrompt != null) {
                AlertDialog(
                    onDismissRequest = {
                        currentAlertStop?.toStopName?.let { stopName ->
                            answeredAlertStopsInSession = answeredAlertStopsInSession + stopName
                        }
                        isSubmittingNavigationAlert = false
                        currentAlertPrompt = null
                        showAlertDialog = false
                    },
                    title = {
                        Text(text = "Alerte à ${currentAlertStop?.toStopName}")
                    },
                    text = {
                        val prompt = currentAlertPrompt
                        val message = prompt?.let(::buildNavigationAlertQuestion)
                            ?: "Confirmez-vous cette alerte ?"
                        Text(message)
                    },
                    confirmButton = {
                        Button(
                            enabled = !isSubmittingNavigationAlert,
                            onClick = {
                                val stop = currentAlertStop ?: return@Button
                                val prompt = currentAlertPrompt ?: return@Button
                                if (isSubmittingNavigationAlert) return@Button
                                isSubmittingNavigationAlert = true
                                scope.launch {
                                    val result = submitUserAlert(
                                        alertTypeId = prompt.alertTypeId,
                                        stopName = stop.toStopName,
                                        stopIdFallback = stop.toStopId.toIntOrNull(),
                                        lineId = null
                                    )
                                    if (result.isSuccess) {
                                        Toast.makeText(
                                            context,
                                            "Alerte envoyée avec succès",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            result.errorMessage
                                                ?: "Erreur lors de l'envoi de l'alerte",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    answeredAlertStopsInSession =
                                        answeredAlertStopsInSession + stop.toStopName
                                    currentAlertPrompt = null
                                    showAlertDialog = false
                                    isSubmittingNavigationAlert = false
                                }
                            }
                        ) {
                            Text("Oui")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            currentAlertStop?.toStopName?.let { stopName ->
                                answeredAlertStopsInSession = answeredAlertStopsInSession + stopName
                            }
                            isSubmittingNavigationAlert = false
                            currentAlertPrompt = null
                            showAlertDialog = false
                        }) {
                            Text("Non")
                        }
                    }
                )
            }

            // Recenter button
            if (!isNavigationMode && userLocation != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 120.dp, end = 16.dp)
                ) {
                    if (isCenteredOnUser) {
                        // Show alert report icon when centered on user
                        Icon(
                            painter = painterResource(id = R.drawable.add_triangle_24px),
                            contentDescription = "Signaler une alerte",
                            tint = Yellow500,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(PrimaryColor)
                                .clickable {
                                    // Reset initial stop to ensure search page is shown first
                                    // (unless in navigation mode where nearest stop is auto-selected)
                                    if (sheetContentState != SheetContentState.NAVIGATION) {
                                        alertReportInitialStop = null
                                    }
                                    showAlertReportSheet = true
                                }
                                .padding(12.dp)
                        )
                    } else {
                        // Show recenter button when not centered on user
                        FloatingActionButton(
                            onClick = {
                                userLocation?.let { location ->
                                    mapInstance?.animateCamera(
                                        CameraUpdateFactory.newLatLngZoom(location, 17.0),
                                        1000
                                    )
                                    isCenteredOnUser = true
                                }
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .then(
                                    if (isDarkMatterStyle && !isSearchExpanded) {
                                        Modifier
                                            .clip(CircleShape)
                                            .border(1.dp, Color.Gray, CircleShape)
                                    } else {
                                        Modifier
                                    }
                                ),
                            containerColor = PrimaryColor,
                            shape = CircleShape,
                            elevation = FloatingActionButtonDefaults.elevation(
                                defaultElevation = 0.dp
                            )
                        ) {
                            Canvas(
                                modifier = Modifier.size(24.dp)
                            ) {
                                drawCircle(
                                    color = Color(0xFF3B82F6),
                                    radius = size.minDimension / 2.5f
                                )
                                drawCircle(
                                    color = SecondaryColor,
                                    radius = size.minDimension / 2.5f,
                                    style = Stroke(width = 7f)
                                )
                            }
                        }
                    }
                }
            }

            // Cache navigation leg pair to avoid redundant O(n) computation during composition
            val navigationLegPair by remember {
                derivedStateOf {
                    if (sheetContentState == SheetContentState.NAVIGATION) {
                        selectedItineraryJourney?.let {
                            getCurrentAndNextNavigationLeg(it, navigationNowSeconds, userLocation)
                        } ?: (null to null)
                    } else {
                        null to null
                    }
                }
            }

            if (isNavigationMode) {
                val currentJourney = selectedItineraryJourney
                val (currentLeg, nextLeg) = navigationLegPair
                val shouldChangeLineInMainCard =
                    if (currentJourney != null && currentLeg != null && nextLeg != null) {
                        val reference = currentJourney.departureTime
                        val nowNormalized =
                            normalizeTimeAroundReference(navigationNowSeconds, reference)
                        val legDepartureNormalized =
                            normalizeTimeAroundReference(currentLeg.departureTime, reference)
                        val vehicleLikelyAlreadyDeparted =
                            currentMovementSpeedMps > WALKING_MAX_SPEED_MPS
                        val isWaitingForVehicle =
                            nowNormalized < legDepartureNormalized && !vehicleLikelyAlreadyDeparted
                        val transferWaitSeconds = computeTransferWaitSeconds(
                            currentLeg = currentLeg,
                            nextLeg = nextLeg,
                            journeyReferenceSeconds = reference
                        )
                        val shouldSplitTransferInstructions =
                            transferWaitSeconds > LONG_TRANSFER_THRESHOLD_SECONDS
                        !isWaitingForVehicle && isAtCurrentLegTransferStop(
                            journey = currentJourney,
                            currentLeg = currentLeg,
                            userLocation = userLocation
                        ) && !shouldSplitTransferInstructions
                    } else {
                        false
                    }
                val upcomingLeg =
                    if (currentJourney != null && currentLeg != null) {
                        val offset = if (shouldChangeLineInMainCard) 2 else 1
                        findUpcomingNonWalkingLeg(
                            journey = currentJourney,
                            currentLeg = currentLeg,
                            offsetFromCurrent = offset
                        )
                    } else {
                        null
                    }
                val topMainShape = if (upcomingLeg != null) {
                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 0.dp, bottomEnd = 20.dp)
                } else {
                    RoundedCornerShape(20.dp)
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 32.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(132.dp)
                            .clip(topMainShape)
                            .background(PrimaryColor)
                    ) {
                        if (currentJourney != null && currentLeg != null) {
                            val reference = currentJourney.departureTime
                            val nowNormalized =
                                normalizeTimeAroundReference(navigationNowSeconds, reference)
                            val legDepartureNormalized =
                                normalizeTimeAroundReference(currentLeg.departureTime, reference)
                            val remainingStopsOnCurrentLeg = computeRemainingStopsOnLeg(
                                leg = currentLeg,
                                userLocation = userLocation
                            )
                            val vehicleLikelyAlreadyDeparted =
                                currentMovementSpeedMps > WALKING_MAX_SPEED_MPS
                            val isWaitingForVehicle =
                                nowNormalized < legDepartureNormalized && !vehicleLikelyAlreadyDeparted
                            val hasCorrespondence = nextLeg != null
                            val transferWaitSeconds = if (nextLeg != null) {
                                computeTransferWaitSeconds(
                                    currentLeg = currentLeg,
                                    nextLeg = nextLeg,
                                    journeyReferenceSeconds = reference
                                )
                            } else {
                                0
                            }
                            val shouldSplitTransferInstructions =
                                transferWaitSeconds > LONG_TRANSFER_THRESHOLD_SECONDS
                            val shouldChangeLine = !isWaitingForVehicle &&
                                    hasCorrespondence &&
                                    !shouldSplitTransferInstructions &&
                                    isAtCurrentLegTransferStop(
                                        journey = currentJourney,
                                        currentLeg = currentLeg,
                                        userLocation = userLocation
                                    )
                            val displayedLeg =
                                if (shouldChangeLine && hasCorrespondence) nextLeg else currentLeg
                            val routeName = displayedLeg.routeName ?: ""
                            val directionValue =
                                displayedLeg.direction?.takeIf { it.isNotBlank() } ?: "?"
                            val directionText = "Direction $directionValue"
                            val actionText = if (isWaitingForVehicle) {
                                val remainingBeforeDeparture = formatDurationUntil(
                                    nowNormalizedSeconds = nowNormalized,
                                    targetNormalizedSeconds = legDepartureNormalized
                                )
                                "Dans $remainingBeforeDeparture, monter à ${currentLeg.fromStopName}"
                            } else {
                                val remainingStops = remainingStopsOnCurrentLeg
                                val targetStopName = currentLeg.toStopName.ifBlank { "l'arrêt suivant" }
                                val actionVerb =
                                    if (shouldChangeLine) {
                                        "changer de ligne à $targetStopName"
                                    } else {
                                        "descendre à $targetStopName"
                                    }
                                if (remainingStops <= 0) {
                                    "Au prochain arrêt, $actionVerb"
                                } else {
                                    val stopWord = if (remainingStops == 1) "arrêt" else "arrêts"
                                    "Dans $remainingStops $stopWord, $actionVerb"
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (shouldChangeLine) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(1.dp)
                                    ) {
                                        NavigationLineIcon(
                                            lineName = currentLeg.routeName ?: "",
                                            size = 36.dp
                                        )
                                        Icon(
                                            imageVector = Icons.Filled.ArrowDownward,
                                            contentDescription = null,
                                            tint = Color(0xFF9CA3AF),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        NavigationLineIcon(
                                            lineName = routeName,
                                            size = 36.dp
                                        )
                                    }
                                } else {
                                    NavigationLineIcon(
                                        lineName = routeName,
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                        size = 44.dp
                                    )
                                }

                                Column(
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = directionText,
                                        color = Color(0xFF9CA3AF),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = actionText,
                                        color = SecondaryColor,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                    if (upcomingLeg != null) {
                        val nextRouteName = upcomingLeg.routeName ?: ""
                        val nextIconRes = BusIconHelper.getResourceIdForLine(context, nextRouteName)
                        val nextFallbackColor =
                            Color(LineColorHelper.getColorForLineString(nextRouteName))
                        Box(
                            modifier = Modifier
                                .wrapContentWidth()
                                .wrapContentHeight()
                                .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                                .background(Stone800)
                        ) {
                            Row(
                                modifier = Modifier
                                    .wrapContentWidth()
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "A suivre",
                                        fontSize = 16.sp,
                                        color = SecondaryColor,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (nextIconRes != 0) {
                                        Image(
                                            painter = painterResource(id = nextIconRes),
                                            contentDescription = null,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(nextFallbackColor),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = nextRouteName.ifBlank { "?" }.take(2),
                                                color = SecondaryColor,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .offset(y = bottomPadding)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(108.dp)
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                            .background(PrimaryColor)
                            .padding(bottom = 12.dp)
                    ) {
                        selectedItineraryJourney?.let { journey ->
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = formatRemainingTime(
                                        departureTimeSeconds = journey.departureTime,
                                        arrivalTimeSeconds = journey.arrivalTime,
                                        nowSeconds = navigationNowSeconds
                                    ),
                                    color = SecondaryColor,
                                    style = MaterialTheme.typography.headlineMedium
                                )
                                Text(
                                    text = journey.formatArrivalTime(),
                                    color = Color(0xFF9CA3AF),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Retour",
                            tint = SecondaryColor,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 20.dp)
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Stone800)
                                .clickable {
                                    requestedSheetValueForNextContent = SheetValue.Expanded
                                    sheetContentState = SheetContentState.ITINERARY
                                }
                                .padding(8.dp)
                        )
                        Icon(
                            painter = painterResource(id = R.drawable.add_triangle_24px),
                            contentDescription = "Signaler une alerte",
                            tint = Yellow500,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 20.dp)
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Stone800)
                                .clickable {
                                    // Use pre-computed nearest stop in navigation mode
                                    val stop = nearestAlertStop
                                    alertReportInitialStop = if (stop != null) {
                                        StationSearchResult(
                                            stopName = stop.properties.nom,
                                            lines = viewModel.parseLineCodesFromDesserte(stop.properties.desserte),
                                            stopId = stop.properties.id
                                        )
                                    } else {
                                        null
                                    }
                                    showAlertReportSheet = true
                                }
                                .padding(10.dp)
                        )
                    }
                }
            }

            // Unified LIVE button (global when no selected bus line, line-specific otherwise)
            val isLineContext =
                sheetContentState == SheetContentState.LINE_DETAILS || sheetContentState == SheetContentState.ALL_SCHEDULES
            // When a sheet is open, place controls where favorites row usually sits.
            val controlsTopPadding = if (sheetContentState != null) 100.dp else 146.dp
            val selectedTrackableLineName =
                selectedLine?.lineName?.takeIf { isLineContext && isLiveTrackableLine(it) }
            val hasSelectedNotTrackableLine =
                selectedLine?.lineName?.let { isLineContext && !isLiveTrackableLine(it) } == true
            val showLiveButton = !isOffline && !hasSelectedNotTrackableLine

            if (sheetContentState != SheetContentState.ITINERARY && !isNavigationMode) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(
                            top = controlsTopPadding,
                            end = 12.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { isMapStyleMenuExpanded = true },
                        border = if (isDarkMatterStyle && !isSearchExpanded) BorderStroke(
                            1.dp,
                            Color.Gray
                        ) else null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryColor
                        ),
                        shape = CircleShape,
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 4.dp
                        ),
                        contentPadding = PaddingValues(
                            top = 6.dp,
                            bottom = 6.dp
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Layers,
                            contentDescription = "Layers",
                            tint = SecondaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    AnimatedVisibility(
                        visible = showLiveButton,
                        enter = fadeIn(),
                        exit = fadeOut()
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
                        val showLiveBorder =
                            isDarkMatterStyle && buttonColor == PrimaryColor && !isSearchExpanded
                        Button(
                            onClick = {
                                if (isLiveModeEnabled) {
                                    if (isLiveTrackingEnabled) {
                                        viewModel.stopLiveTracking()
                                    }
                                    if (isGlobalLiveEnabled) {
                                        viewModel.stopGlobalLive()
                                    }
                                } else {
                                    selectedTrackableLineName?.let { lineName ->
                                        viewModel.startLiveTracking(lineName)
                                    } ?: viewModel.toggleGlobalLive()
                                }
                            },
                            border = if (showLiveBorder) BorderStroke(1.dp, Color.Gray) else null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = buttonColor
                            ),
                            shape = RoundedCornerShape(20.dp),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 4.dp
                            ),
                            contentPadding = PaddingValues(
                                start = 15.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 8.dp
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Always show dot, animate when active with vehicles
                                Canvas(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .graphicsLayer { translationY = dotOffset }
                                ) {
                                    drawCircle(color = SecondaryColor)
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "LIVE",
                                    fontWeight = FontWeight.Bold,
                                    color = SecondaryColor
                                )
                            }
                        }
                    }
                }
            }

            if (sheetContentState == SheetContentState.ITINERARY && selectedItineraryJourney == null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(top = 8.dp, start = 10.dp, end = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            ItinerarySearchBarField(
                                selectedStop = itineraryDepartureStop,
                                onClick = {
                                    itinerarySearchTarget = ItineraryFieldTarget.DEPARTURE
                                    // If a stop is selected, always reopen with its full name.
                                    // This prevents reverting to a previous partial query after choosing a result.
                                    itineraryDepartureQuery = itineraryDepartureStop?.name
                                        ?: itineraryDepartureQuery.ifBlank { "" }
                                    itinerarySearchFocusNonce++
                                },
                                icon = Icons.Default.MyLocation,
                                placeholder = "Arret de depart"
                            )

                            ItinerarySearchBarField(
                                modifier = Modifier.offset(y = (-18).dp),
                                selectedStop = itineraryArrivalStop,
                                onClick = {
                                    itinerarySearchTarget = ItineraryFieldTarget.ARRIVAL
                                    // If a stop is selected, always reopen with its full name.
                                    // This prevents reverting to a previous partial query after choosing a result.
                                    itineraryArrivalQuery = itineraryArrivalStop?.name
                                        ?: itineraryArrivalQuery.ifBlank { "" }
                                    itinerarySearchFocusNonce++
                                },
                                icon = Icons.Default.Search,
                                placeholder = "Arret d'arrivee"
                            )
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offset(y = 10.dp)
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(SecondaryColor)
                                .clickable {
                                    val previousDeparture = itineraryDepartureStop
                                    itineraryDepartureStop = itineraryArrivalStop
                                    itineraryArrivalStop = previousDeparture
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = "Inverser",
                                tint = PrimaryColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

        }
    }

    if (sheetContentState == SheetContentState.ITINERARY && itinerarySearchTarget != null) {
        val isDepartureSearch = itinerarySearchTarget == ItineraryFieldTarget.DEPARTURE
        val overlayQuery = if (isDepartureSearch) itineraryDepartureQuery else itineraryArrivalQuery

        TransportSearchBar(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize(),
            content = TransportSearchContent.STOPS_ONLY,
            showHistory = false,
            startExpanded = true,
            showDarkOutline = false,
            searchPlaceholder = if (isDepartureSearch) {
                "Rechercher un depart"
            } else {
                "Rechercher une arrivee"
            },
            query = overlayQuery,
            onQueryChange = { newValue ->
                if (isDepartureSearch) {
                    itineraryDepartureQuery = newValue
                } else {
                    itineraryArrivalQuery = newValue
                }
            },
            focusNonce = itinerarySearchFocusNonce,
            onExpandedChange = { expanded ->
                if (!expanded) {
                    // When search bar is collapsed, reset the search target to dismiss the overlay
                    itinerarySearchTarget = null
                }
            },
            onStopPrimary = { result ->
                scope.launch {
                    val stopIds = viewModel.raptorRepository.resolveStopIdsByName(result.stopName)
                    val selectedStop = SelectedStop(
                        name = result.stopName,
                        stopIds = stopIds
                    )
                    if (isDepartureSearch) {
                        itineraryDepartureStop = selectedStop
                        itineraryDepartureQuery = ""
                    } else {
                        itineraryArrivalStop = selectedStop
                        itineraryArrivalQuery = ""
                    }
                    // Reset search target after selection
                    itinerarySearchTarget = null
                }
            }
        )
    }

    if (isMapStyleMenuExpanded) {
        MapStyleSelectionSheet(
            isOffline = isOffline,
            downloadedMapStyles = offlineDataInfo.downloadedMapStyles,
            selectedMapStyle = selectedMapStyle,
            onDismiss = { isMapStyleMenuExpanded = false },
            onStyleSelected = { style ->
                mapStyleRepository.saveSelectedStyle(style)
                val effectiveStyle = mapStyleRepository.getEffectiveStyle(
                    isOffline,
                    offlineDataInfo.downloadedMapStyles
                )
                selectedMapStyle = effectiveStyle
                mapStyleUrl = effectiveStyle.styleUrl
            }
        )
    }

    LaunchedEffect(shouldCenterOnUser) {
        if (shouldCenterOnUser) {
            shouldCenterOnUser = false
        }
    }

    if (showLinesSheet) {
        val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        LaunchedEffect(true) {
            if (!showLinesSheet) {
                modalBottomSheetState.hide()
            }
        }

        ModalBottomSheet(
            onDismissRequest = onLinesSheetDismiss,
            containerColor = SecondaryColor,
            sheetState = modalBottomSheetState
        ) {
            LinesBottomSheet(
                allLines = viewModel.getAllAvailableLines(),
                onLineClick = { lineName ->
                    // Cancel pending operations and clear states from previous line to prevent OOM
                    viewModel.resetLineDetailState()

                    onLinesSheetDismiss()

                    if (!isMetroTramOrFunicular(lineName)) {
                        scope.launch {
                            viewModel.addLineToLoaded(lineName)
                            if (isTemporaryBus(lineName)) {
                                temporaryLoadedBusLines = temporaryLoadedBusLines + lineName
                            }
                            delay(100)

                            selectedLine = LineInfo(
                                lineName = lineName,
                                currentStationName = ""
                            )
                            sheetContentState = SheetContentState.LINE_DETAILS
                            delay(50)
                            scaffoldSheetState.bottomSheetState.partialExpand()
                        }
                    } else {
                        scope.launch {
                            val currentLines = when (val currentState = uiState) {
                                is TransportLinesUiState.Success -> currentState.lines
                                is TransportLinesUiState.PartialSuccess -> currentState.lines
                                else -> emptyList()
                            }
                            val isLoaded = currentLines.any {
                                areEquivalentLineNames(it.properties.lineName, lineName)
                            }

                            if (!isLoaded) {
                                viewModel.addLineToLoaded(lineName)
                                delay(100)
                            }

                            selectedLine = LineInfo(
                                lineName = lineName,
                                currentStationName = ""
                            )
                            sheetContentState = SheetContentState.LINE_DETAILS
                            delay(50)
                            scaffoldSheetState.bottomSheetState.partialExpand()
                        }
                    }
                },
                viewModel = viewModel
            )
        }
    }

    if (showAddFavoriteDialog) {
        AddFavoriteDialog(
            onDismiss = {
                // ...
            },
            onFavoriteCreated = { name, iconName, stopName ->
                viewModel.addUserFavorite(name, iconName, stopName)
            },
            viewModel = viewModel,
            initialStopName = addFavoriteInitialStopName
        )
    }

    if (showAlertReportSheet) {
        val nearestStopCandidate = userLocation?.let { location ->
            val stops = (stopsUiState as? TransportStopsUiState.Success)?.stops
            val nearestStop = stops?.minByOrNull { stop ->
                val coords = stop.geometry.coordinates
                if (coords.size >= 2) {
                    squaredDistance(
                        lat1 = location.latitude,
                        lon1 = location.longitude,
                        lat2 = coords[1],
                        lon2 = coords[0]
                    )
                } else {
                    Double.MAX_VALUE
                }
            }

            nearestStop?.let { stop ->
                StationSearchResult(
                    stopName = stop.properties.nom,
                    lines = BusIconHelper.getAllLinesForStop(stop),
                    stopId = stop.properties.id
                )
            }
        }

        AlertReportBottomSheet(
            viewModel = viewModel,
            onDismiss = { showAlertReportSheet = false },
            initialStop = alertReportInitialStop,
            nearestStopCandidate = nearestStopCandidate
        )
    }
}
