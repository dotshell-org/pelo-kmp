package com.pelotcl.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.pelotcl.app.generic.data.models.geojson.FeatureCollection
import com.pelotcl.app.generic.data.models.geojson.StopCollection
import com.pelotcl.app.generic.data.repository.offline.mapstyle.MapStyleCompat
import com.pelotcl.app.generic.service.TransportServiceProvider
import com.pelotcl.app.generic.ui.components.MapCanvas
import com.pelotcl.app.generic.ui.components.search.TransportSearchBar
import com.pelotcl.app.generic.utils.location.LocationProvider
import org.maplibre.spatialk.geojson.Position
import com.pelotcl.app.generic.ui.theme.PeloTheme
import com.pelotcl.app.generic.ui.viewmodel.TransportLinesUiState
import com.pelotcl.app.generic.ui.viewmodel.TransportStopsUiState
import com.pelotcl.app.generic.ui.viewmodel.TransportViewModel
import com.pelotcl.app.platform.LocalPlatformContext
import com.pelotcl.app.platform.Log
import com.pelotcl.app.platform.PlatformContext
import platform.UIKit.UIViewController

/**
 * iOS no-op platform context. On Android, PlatformContext is android.content.Context;
 * on iOS the platform actuals (FileSystem, Settings, …) don't need a real context, so a
 * single shared instance is enough. PlatformContext is `abstract` (to match the Android
 * typealias to the abstract android.content.Context), hence this concrete singleton.
 */
object IosPlatformContext : PlatformContext()

/**
 * Compose entry point, exported to Swift as `ComposeAppKt.MainViewController()`.
 * The iosApp Xcode target wraps this UIViewController in SwiftUI.
 */
fun MainViewController(): UIViewController = ComposeUIViewController {
    CompositionLocalProvider(LocalPlatformContext provides IosPlatformContext) {
        App()
    }
}

/**
 * Shared root composable. Being assembled incrementally on iOS from the already-common
 * Plan building blocks (MapCanvas, TransportSearchBar, …), verified on the simulator, until
 * it reaches parity with the androidMain PlanScreen orchestrator (§9.3–§9.5).
 *
 * `TransportServiceProvider.initialize` replaces the Android `PeloApplication.onCreate`
 * bootstrap (there is no Application on iOS).
 */
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

        if (viewModel != null) {
            PlanContent(viewModel)
        } else {
            MapCanvas(
                modifier = Modifier.fillMaxSize(),
                styleUrl = MapStyleCompat.POSITRON.styleUrl,
            )
        }
    }
}

@Composable
private fun PlanContent(viewModel: TransportViewModel) {
    val linesState by viewModel.uiState.collectAsState()
    val stopsState by viewModel.stopsUiState.collectAsState()

    val lines = when (val s = linesState) {
        is TransportLinesUiState.Success -> s.lines
        is TransportLinesUiState.PartialSuccess -> s.lines
        else -> null
    }
    val stops = (stopsState as? TransportStopsUiState.Success)?.stops

    var userLocation by remember { mutableStateOf<Position?>(null) }
    val locationProvider = remember { LocationProvider(IosPlatformContext) }
    DisposableEffect(Unit) {
        locationProvider.startUpdates { point ->
            userLocation = Position(latitude = point.latitude, longitude = point.longitude)
        }
        onDispose { locationProvider.stopUpdates() }
    }

    Box(Modifier.fillMaxSize()) {
        MapCanvas(
            modifier = Modifier.fillMaxSize(),
            styleUrl = MapStyleCompat.POSITRON.styleUrl,
            initialLatitude = 45.75,
            initialLongitude = 4.85,
            initialZoom = 12.0,
            lines = lines?.let { FeatureCollection(features = it) },
            stops = stops?.let { StopCollection(features = it) },
            userLocation = userLocation,
            onLineClick = { lineName -> viewModel.selectLine(lineName) },
        )

        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            TransportSearchBar(
                onSearchStops = { q -> viewModel.searchStops(q) },
                onSearchLines = { q -> viewModel.searchLines(q) },
                onStopPrimary = { },
                onLineSelected = { line -> viewModel.selectLine(line.lineName) },
            )
        }
    }
}
