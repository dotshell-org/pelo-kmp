@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pelotcl.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.pelotcl.app.generic.data.models.geojson.FeatureCollection
import com.pelotcl.app.generic.data.models.geojson.StopCollection
import com.pelotcl.app.generic.data.repository.offline.mapstyle.MapStyleCompat
import com.pelotcl.app.generic.service.TransportServiceProvider
import com.pelotcl.app.generic.ui.components.MapCanvas
import com.pelotcl.app.generic.ui.components.search.TransportSearchBar
import com.pelotcl.app.generic.ui.screens.Destination
import com.pelotcl.app.generic.ui.screens.plan.LinesBottomSheet
import com.pelotcl.app.generic.ui.screens.settings.SettingsScreen
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
import com.pelotcl.app.platform.PlatformContext
import com.pelotcl.app.platform.appVersionName
import org.maplibre.spatialk.geojson.Position
import platform.UIKit.UIViewController

/** iOS no-op platform context (PlatformContext is `abstract` to match the Android typealias). */
object IosPlatformContext : PlatformContext()

/** Compose entry point, exported to Swift as `ComposeAppKt.MainViewController()`. */
fun MainViewController(): UIViewController = ComposeUIViewController {
    CompositionLocalProvider(LocalPlatformContext provides IosPlatformContext) {
        App()
    }
}

@Composable
fun App() {
    PeloTheme {
        val viewModel = remember {
            try {
                TransportServiceProvider.initialize(IosPlatformContext)
                TransportViewModel(IosPlatformContext)
            } catch (t: Throwable) {
                Log.e("iosApp", "Transport data init failed: ${t.message}")
                null
            }
        }

        // Raptor backs stop/line search; loading its .bin assets up front (not lazily on the
        // first search) is what makes search work and avoids a mid-search hang.
        LaunchedEffect(viewModel) {
            val vm = viewModel ?: return@LaunchedEffect
            runCatching { vm.raptorRepository.initialize() }
                .onFailure { Log.e("iosApp", "Raptor init failed: ${it.message}") }
        }

        if (viewModel != null) {
            RootScaffold(viewModel)
        } else {
            MapCanvas(modifier = Modifier.fillMaxSize(), styleUrl = MapStyleCompat.POSITRON.styleUrl)
        }
    }
}

@Composable
private fun RootScaffold(viewModel: TransportViewModel) {
    var selectedTab by remember { mutableStateOf(Destination.PLAN) }
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = PrimaryColor) {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = selectedTab == destination,
                        onClick = { selectedTab = destination },
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
        },
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(bottom = innerPadding.calculateBottomPadding())
        when (selectedTab) {
            Destination.PLAN -> PlanContent(viewModel, contentModifier)
            Destination.LINES -> LinesTab(viewModel, contentModifier)
            Destination.SETTINGS -> SettingsTab(viewModel, contentModifier) { selectedTab = Destination.PLAN }
        }
    }
}

@Composable
private fun PlanContent(viewModel: TransportViewModel, modifier: Modifier = Modifier) {
    val linesState by viewModel.uiState.collectAsState()
    val stopsState by viewModel.stopsUiState.collectAsState()
    val lineRules = remember { TransportServiceProvider.getTransportLineRules() }

    val allLines = when (val s = linesState) {
        is TransportLinesUiState.Success -> s.lines
        is TransportLinesUiState.PartialSuccess -> s.lines
        else -> null
    }
    // Only the strong lines (metro/tram/funicular) on the map, like Android — every bus trace is laggy.
    val strongLines = allLines?.filter { lineRules.isStrongLine(it.properties.lineName) }
    val stops = (stopsState as? TransportStopsUiState.Success)?.stops

    var userLocation by remember { mutableStateOf<Position?>(null) }
    val locationProvider = remember { LocationProvider(IosPlatformContext) }
    DisposableEffect(Unit) {
        locationProvider.startUpdates { point ->
            userLocation = Position(latitude = point.latitude, longitude = point.longitude)
        }
        onDispose { locationProvider.stopUpdates() }
    }

    var tappedStopName by remember { mutableStateOf<String?>(null) }

    Box(modifier) {
        MapCanvas(
            modifier = Modifier.fillMaxSize(),
            styleUrl = MapStyleCompat.POSITRON.styleUrl,
            initialLatitude = 45.75,
            initialLongitude = 4.85,
            initialZoom = 12.0,
            lines = strongLines?.let { FeatureCollection(features = it) },
            stops = stops?.let { StopCollection(features = it) },
            userLocation = userLocation,
            onStopClick = { nom -> tappedStopName = nom },
            onLineClick = { lineName -> viewModel.selectLine(lineName) },
        )

        // Black search zone bleeds behind the status bar / Dynamic Island; the field sits below it.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color.Black)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            TransportSearchBar(
                onSearchStops = { q -> viewModel.searchStops(q) },
                onSearchLines = { q -> viewModel.searchLines(q) },
                onStopPrimary = { result -> tappedStopName = result.stopName },
                onLineSelected = { line -> viewModel.selectLine(line.lineName) },
            )
        }
    }

    tappedStopName?.let { nom ->
        val stop = stops?.firstOrNull { it.properties.nom.equals(nom, ignoreCase = true) }
        val lignes = stop?.properties?.desserte.orEmpty()
            .split(',')
            .map { it.trim().substringBefore(':').trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        ModalBottomSheet(onDismissRequest = { tappedStopName = null }) {
            Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 40.dp)) {
                Text(nom, style = MaterialTheme.typography.titleLarge, color = PrimaryColor)
                if (lignes.isNotEmpty()) {
                    Text(
                        "Lignes : ${lignes.joinToString(", ")}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LinesTab(viewModel: TransportViewModel, modifier: Modifier = Modifier) {
    val linesState by viewModel.uiState.collectAsState()
    val stopsState by viewModel.stopsUiState.collectAsState()
    val allLines = remember(linesState, stopsState) { viewModel.getAllAvailableLines() }
    Box(modifier.windowInsetsPadding(WindowInsets.statusBars)) {
        LinesBottomSheet(
            allLines = allLines,
            onLineClick = { lineName -> viewModel.selectLine(lineName) },
            viewModel = viewModel,
        )
    }
}

@Composable
private fun SettingsTab(viewModel: TransportViewModel, modifier: Modifier = Modifier, onBack: () -> Unit) {
    Box(modifier.windowInsetsPadding(WindowInsets.systemBars)) {
        SettingsScreen(
            versionName = appVersionName(IosPlatformContext),
            onBackClick = onBack,
            onItineraryClick = {},
            onLegalClick = {},
            onCreditsClick = {},
            onContactClick = {},
        )
    }
}
