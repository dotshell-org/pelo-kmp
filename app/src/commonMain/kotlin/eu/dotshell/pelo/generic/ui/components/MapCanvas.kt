package eu.dotshell.pelo.generic.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import eu.dotshell.pelo.generic.data.models.geojson.FeatureCollection
import eu.dotshell.pelo.generic.data.models.geojson.StopCollection
import eu.dotshell.pelo.generic.utils.map.StopsRenderData
import eu.dotshell.pelo.generic.utils.map.toLinesGeoJson
import eu.dotshell.pelo.generic.utils.map.toStopsGeoJsonByPriority
import eu.dotshell.pelo.platform.DrawableProvider
import eu.dotshell.pelo.platform.LocalPlatformContext
import eu.dotshell.pelo.platform.Log
import eu.dotshell.pelo.platform.FileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.expressions.dsl.Case
import org.maplibre.compose.expressions.dsl.and
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToColor
import org.maplibre.compose.expressions.dsl.convertToNumber
import org.maplibre.compose.expressions.dsl.convertToString
import org.maplibre.compose.expressions.dsl.eq
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.expressions.value.ImageValue
import org.maplibre.compose.expressions.value.StringValue
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.util.ClickResult
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

private const val EMPTY_FEATURE_COLLECTION = """{"type":"FeatureCollection","features":[]}"""
private const val STOP_RENDER_MIN_ZOOM = 12.0
private const val BUS_RENDER_MIN_ZOOM = 16.0

/**
 * Cross-platform map canvas built on maplibre-compose (declarative).
 *
 * # Performance design (iOS)
 *
 * maplibre-compose continuously writes to [cameraState].position at the native render rate
 * (≈20 fps on iOS while tiles are loading). Any composition scope that reads
 * [CameraState.position] therefore recomposes at that rate.
 *
 * To avoid cascading recompositions of [MapCanvas]:
 *  - [cameraState.position] is NEVER read directly in the composition body.
 *    All zoom-threshold logic runs inside [snapshotFlow] collectors (coroutines),
 *    which only write to their own [mutableStateOf] when the threshold actually crosses.
 *  - [painterResource] / [DrawableProvider.getPainter] calls are kept minimal and
 *    guarded so they do not re-execute on every recomposition.
 *  - Stop layers are reduced to exactly 3 [SymbolLayer]s (one per priority tier)
 *    instead of tiers × slots.
 */
@Composable
fun MapCanvas(
    modifier: Modifier = Modifier,
    styleUrl: String,
    initialLatitude: Double = 45.75,
    initialLongitude: Double = 4.85,
    initialZoom: Double = 10.0,
    initialBearing: Double? = null,
    cameraState: CameraState = rememberCameraState(
        firstPosition = CameraPosition(
            target = Position(latitude = initialLatitude, longitude = initialLongitude),
            zoom = initialZoom,
            bearing = initialBearing ?: 0.0,
        )
    ),
    lines: FeatureCollection? = null,
    stops: StopCollection? = null,
    itineraryGeoJson: String? = null,
    userLocation: Position? = null,
    vehiclesGeoJson: String? = null,
    vehicleIconName: String? = null,
    selectedLineName: String? = null,
    interactive: Boolean = true,
    onStopClick: (stopName: String) -> Unit = {},
    onLineClick: (lineName: String) -> Unit = {},
    onVehicleClick: (lineName: String) -> Unit = {},
    onMapMoved: () -> Unit = {},
    centerOn: Position? = null,
    focusZoom: Double? = null,
    bearing: Double? = null,
    tilt: Double? = null,
) {
    Log.i("MapCanvas", "compose entered, stops=${stops?.features?.size} shouldRenderStops=${!selectedLineName.isNullOrBlank() || initialZoom >= STOP_RENDER_MIN_ZOOM} lines=${lines?.features?.size}")

    val fallbackPainter = remember {
        object : Painter() {
            override val intrinsicSize: Size = Size(14f, 14f)
            override fun DrawScope.onDraw() {
                drawCircle(color = Color(0xFF1F2937))
            }
        }
    }

    var isAnimating by remember { mutableStateOf(false) }

    LaunchedEffect(centerOn, focusZoom, bearing, tilt) {
        if (centerOn != null || focusZoom != null || bearing != null || tilt != null) {
            isAnimating = true
            val targetCenter = centerOn ?: cameraState.position.target
            val targetZoom = focusZoom ?: cameraState.position.zoom
            val targetBearing = bearing ?: cameraState.position.bearing
            val targetTilt = tilt ?: cameraState.position.tilt
            cameraState.animateTo(
                CameraPosition(
                    target = targetCenter,
                    zoom = targetZoom,
                    bearing = targetBearing,
                    tilt = targetTilt
                )
            )
            isAnimating = false
        }
    }

    // Notify parent when the user pans the map (skip the very first emission).
    LaunchedEffect(cameraState, onMapMoved) {
        var isFirst = true
        snapshotFlow { cameraState.position }
            .collect {
                if (isFirst) { isFirst = false; return@collect }
                if (!isAnimating) {
                    onMapMoved()
                }
            }
    }

    // ─── Zoom-threshold logic in coroutines ONLY ────────────────────────────
    // CRITICAL: cameraState.position must NEVER be read in the composition body.
    // Reading it here (even via derivedStateOf) registers a subscription in
    // MapCanvas's composition scope. Because maplibre-compose writes the position
    // on every native render frame (~20 fps), the entire MapCanvas recomposes at
    // that rate, creating the rendering storm seen in the logs.
    //
    // snapshotFlow reads happen in a coroutine (not in composition), so they
    // do NOT create composition subscriptions. distinctUntilChanged() + threshold
    // comparison ensure we only update state when the boolean VALUE actually
    // flips — keeping composition quiet while the camera moves freely.
    // ────────────────────────────────────────────────────────────────────────
    var shouldRenderStops by remember(selectedLineName, itineraryGeoJson) {
        mutableStateOf(
            if (itineraryGeoJson != null) {
                cameraState.position.zoom >= 12.5f
            } else {
                !selectedLineName.isNullOrBlank() || cameraState.position.zoom >= STOP_RENDER_MIN_ZOOM
            }
        )
    }
    LaunchedEffect(cameraState, selectedLineName, itineraryGeoJson) {
        snapshotFlow { cameraState.position.zoom }
            .map { zoom ->
                if (itineraryGeoJson != null) {
                    zoom >= 12.5f
                } else {
                    !selectedLineName.isNullOrBlank() || zoom >= STOP_RENDER_MIN_ZOOM
                }
            }
            .distinctUntilChanged()
            .collect { shouldRenderStops = it }
    }

    var shouldIncludeBus by remember(itineraryGeoJson) {
        mutableStateOf(
            if (itineraryGeoJson != null) {
                cameraState.position.zoom >= 12.5f
            } else {
                cameraState.position.zoom >= BUS_RENDER_MIN_ZOOM
            }
        )
    }
    LaunchedEffect(cameraState, itineraryGeoJson) {
        snapshotFlow { cameraState.position.zoom }
            .map { zoom ->
                if (itineraryGeoJson != null) {
                    zoom >= 12.5f
                } else {
                    zoom >= BUS_RENDER_MIN_ZOOM
                }
            }
            .distinctUntilChanged()
            .collect { shouldIncludeBus = it }
    }

    val context = LocalPlatformContext.current
    val drawableProvider = remember(context) { DrawableProvider(context) }

    val linesGeoJson by produceState(EMPTY_FEATURE_COLLECTION, lines) {
        val result = withContext(Dispatchers.Default) {
            lines?.toLinesGeoJson() ?: EMPTY_FEATURE_COLLECTION
        }
        value = result
    }

    val stopsToRender = if (shouldRenderStops) stops else null
    val render by produceState<StopsRenderData?>(
        initialValue = null,
        key1 = stopsToRender,
        key2 = selectedLineName,
        key3 = shouldIncludeBus,
    ) {
        val result = withContext(Dispatchers.Default) {
            stopsToRender?.toStopsGeoJsonByPriority(
                selectedLineName = selectedLineName,
                hasDrawable = { drawableProvider.hasDrawable(it) },
                shouldIncludeBus = shouldIncludeBus,
            )
        }
        // StopsRenderData is a data class → this assignment only triggers
        // recomposition if the content actually changed.
        value = result
    }

    // ─── Resolve painters in the Compose body (not inside MaplibreMap lambda) ─
    // In maplibre-compose the MaplibreMap content lambda runs in its own inner
    // composition scope. Calling @Composable functions (like painterResource) inside
    // that lambda is unreliable on iOS: the inner scope may recompose at frame rate
    // and the remember-cache for painters becomes detached, causing continuous
    // resource reloads. We resolve all painters here, in the stable outer scope,
    // and pass the resulting plain Painter objects (not state) into the lambda.
    // ─────────────────────────────────────────────────────────────────────────
    val currentRender: StopsRenderData? = render
    // iconNames is re-evaluated only when render's *content* changes (data class ==).
    val iconNames: List<String> = remember(currentRender) {
        currentRender?.iconNames?.toList() ?: emptyList()
    }
    val slots = remember(currentRender) {
        val max = currentRender?.maxIcons ?: 1
        (-(max - 1)..(max - 1)).toList()
    }
    // Pre-load all key/strong-line icons to avoid asynchronous pop-in/loading delay when zooming in.
    val preloadedIconNames = remember {
        listOf(
            "a", "b", "c", "d", "f1", "f2",
            "t1", "t2", "t3", "t4", "t5", "t6", "t7",
            "tb11", "tb12", "navi1", "rx", "brtrx", "mode_bus"
        ).filter { drawableProvider.hasDrawable(it) }
    }
    val allIconNamesToLoad = remember(iconNames, preloadedIconNames) {
        (preloadedIconNames + iconNames).distinct()
    }
    // Resolve one Painter per icon. getPainter uses painterResource internally which
    // is async. On first composition the painters may be in a loading state — that
    // triggers one extra recomposition per painter per batch, after which they are
    // all stable. The remember(iconNames) above prevents re-triggering when the
    // render data object changes but carries identical icon names.
    // iconPainters: the remember(iconNames) block returns a stable mutable map.
    // The for-loop below calls getPainter for each name; getPainter uses
    // painterResource internally which caches via its own remember — on subsequent
    // recompositions with the same iconNames it returns the already-loaded Painter
    // immediately (no state change → no cascade). The cast is safe: the mutable
    // map is only mutated here, in the composition body, never inside a lambda.
    @Suppress("UNCHECKED_CAST")
    val iconPainters = remember(allIconNamesToLoad) { HashMap<String, Painter>(allIconNamesToLoad.size) }
    for (name in allIconNamesToLoad) {
        iconPainters[name] = drawableProvider.getPainter(name)
    }

    // Vehicle painters — call getPainter directly; painterResource caches internally.
    val busPainter: Painter =
        if (drawableProvider.hasDrawable("ic_bus_vehicle")) drawableProvider.getPainter("ic_bus_vehicle")
        else fallbackPainter
    val tramPainter: Painter =
        if (drawableProvider.hasDrawable("ic_tramway_vehicle")) drawableProvider.getPainter("ic_tramway_vehicle")
        else fallbackPainter

    val baseStyle = remember(styleUrl, context) {
        if (styleUrl.startsWith("asset://")) {
            val assetName = styleUrl.removePrefix("asset://")
            val fileSystem = FileSystem(context)
            runCatching {
                val json = fileSystem.readAsset(assetName)
                BaseStyle.Json(json)
            }.getOrElse {
                Log.e("MapCanvas", "Failed to load local asset style: $assetName", it)
                BaseStyle.Uri(styleUrl)
            }
        } else {
            BaseStyle.Uri(styleUrl)
        }
    }

    val mapOptions = remember(interactive) {
        MapOptions(
            gestureOptions = mapGestureOptions(interactive),
            ornamentOptions = OrnamentOptions(
                isScaleBarEnabled = false,
                isAttributionEnabled = false,
                isCompassEnabled = false
            )
        )
    }

    MaplibreMap(
        modifier = modifier,
        baseStyle = baseStyle,
        cameraState = cameraState,
        options = mapOptions,
    ) {
        Log.i("MapCanvas", "MaplibreMap content lambda compose start")
        // key(styleUrl) ensures all layers/sources are rebuilt when the style changes.
        androidx.compose.runtime.key(styleUrl) {
            // ------------------------------------------------------------------
            // Sources — wrapped in remember so native objects are only recreated
            // when the JSON string actually changes.
            // ------------------------------------------------------------------
            val lineSourceData = remember(linesGeoJson) { GeoJsonData.JsonString(linesGeoJson) }
            val lineSource = rememberGeoJsonSource(data = lineSourceData)

            val stopsGeoJson = currentRender?.geoJson ?: EMPTY_FEATURE_COLLECTION
            val stopsSourceData = remember(stopsGeoJson) { GeoJsonData.JsonString(stopsGeoJson) }
            val stopsSource = rememberGeoJsonSource(data = stopsSourceData)

            val itinerarySourceData = remember(itineraryGeoJson) {
                GeoJsonData.JsonString(itineraryGeoJson ?: EMPTY_FEATURE_COLLECTION)
            }
            val itinerarySource = rememberGeoJsonSource(data = itinerarySourceData)

            val vehicleSourceData = remember(vehiclesGeoJson) {
                GeoJsonData.JsonString(vehiclesGeoJson ?: EMPTY_FEATURE_COLLECTION)
            }
            val vehicleSource = rememberGeoJsonSource(data = vehicleSourceData)

            val userLocationGeoJson = remember(userLocation) {
                if (userLocation != null) {
                    """{"type":"Feature","geometry":{"type":"Point","coordinates":[${userLocation.longitude},${userLocation.latitude}]},"properties":{}}"""
                } else {
                    EMPTY_FEATURE_COLLECTION
                }
            }
            val userSourceData = remember(userLocationGeoJson) { GeoJsonData.JsonString(userLocationGeoJson) }
            val userSource = rememberGeoJsonSource(data = userSourceData)

            // ------------------------------------------------------------------
            // Transport lines
            // ------------------------------------------------------------------
            if (lines != null && itineraryGeoJson == null) {
                LineLayer(
                    id = "transport-lines",
                    source = lineSource,
                    color = feature["color"].convertToColor(),
                    width = switch(
                        feature["isMetroOrFunicular"].convertToString(),
                        case("yes", const(4.dp)),
                        fallback = const(2.dp)
                    ),
                    onClick = { features ->
                        val lineName = features.firstOrNull()?.properties?.get("lineName")?.jsonPrimitive?.contentOrNull
                        if (lineName != null) { onLineClick(lineName); ClickResult.Consume } else ClickResult.Pass
                    },
                )
                LineLayer(
                    id = "transport-lines-tap",
                    source = lineSource,
                    color = const(Color(0x01000000)),
                    width = const(24.dp),
                    onClick = { features ->
                        val lineName = features.firstOrNull()?.properties?.get("lineName")?.jsonPrimitive?.contentOrNull
                        if (lineName != null) { onLineClick(lineName); ClickResult.Consume } else ClickResult.Pass
                    },
                )
            }

            // ------------------------------------------------------------------
            // Itinerary legs
            // ------------------------------------------------------------------
            if (itineraryGeoJson != null) {
                LineLayer(
                    id = "itinerary-transit",
                    source = itinerarySource,
                    filter = feature["isWalking"].convertToString() eq const("no"),
                    color = feature["color"].convertToColor(),
                    width = const(4.dp),
                )
                LineLayer(
                    id = "itinerary-walking",
                    source = itinerarySource,
                    filter = feature["isWalking"].convertToString() eq const("yes"),
                    color = feature["color"].convertToColor(),
                    width = const(4.dp),
                    dasharray = const(listOf(2.0, 2.0)),
                )
            }

            // ------------------------------------------------------------------
            // Stop icons
            //
            // All painters were resolved in the outer scope. Here we only build
            // the expression objects (no @Composable calls). Three SymbolLayers
            // (one per priority tier) instead of tiers × slots layers.
            // ------------------------------------------------------------------
            if (stopsToRender != null && currentRender != null && iconPainters.isNotEmpty()) {
                val onStop: (String?) -> ClickResult = { nom ->
                    if (nom != null) { onStopClick(nom); ClickResult.Consume } else ClickResult.Pass
                }

                val cases = ArrayList<Case<StringValue, ImageValue>>(iconPainters.size)
                for ((name, painter) in iconPainters) {
                    cases.add(case(name, image(painter, glyphDpSize(painter, 17f))))
                }
                val fallback = image(
                    iconPainters.values.firstOrNull() ?: fallbackPainter,
                    glyphDpSize(iconPainters.values.firstOrNull() ?: fallbackPainter, 17f)
                )
                val iconImageExpr = switch(
                    feature["icon"].convertToString(),
                    *cases.toTypedArray(),
                    fallback = fallback,
                )

                // Loop over slots to stack multiple icons vertically at multi-line stations (like Bellecour).
                // Priority gates the zoom (metro/funicular from 12.5f, tram from 14f, bus from 17f).
                // String-based priority comparison to avoid numerical conversion mismatches.
                val tiers = listOf(
                    Triple("2", 12.5f, "transport-stops-priority-2"),
                    Triple("1", 14.0f, "transport-stops-priority-1"),
                    Triple("0", 17.0f, "transport-stops-priority-0")
                )
                for ((priority, minZoom, baseId) in tiers) {
                    val actualMinZoom = if (itineraryGeoJson != null) 12.5f else minZoom
                    for (slot in slots) {
                        SymbolLayer(
                            id = "$baseId-$slot",
                            source = stopsSource,
                            minZoom = actualMinZoom,
                            filter = (feature["stop_priority"].convertToString() eq const(priority)) and
                                    (feature["slot"].convertToNumber() eq const(slot)),
                            iconImage = iconImageExpr,
                            iconOffset = const(DpOffset(0.dp, (slot * 8).dp)),
                            iconAllowOverlap = const(true),
                            onClick = { f -> onStop(f.firstOrNull()?.properties?.get("nom")?.jsonPrimitive?.contentOrNull) },
                        )
                    }
                }
            } else if (stopsToRender != null && currentRender != null) {
                // Fallback: plain circles when no line glyphs are available.
                val onStop: (String?) -> ClickResult = { nom ->
                    if (nom != null) { onStopClick(nom); ClickResult.Consume } else ClickResult.Pass
                }
                CircleLayer(
                    id = "transport-stops-bus",
                    source = stopsSource,
                    minZoom = if (itineraryGeoJson != null) 12.5f else 16f,
                    filter = feature["stop_priority"].convertToString() eq const("0"),
                    radius = const(3.dp),
                    color = const(Color(0xFF6B7280)),
                    onClick = { f -> onStop(f.firstOrNull()?.properties?.get("nom")?.jsonPrimitive?.contentOrNull) },
                )
                CircleLayer(
                    id = "transport-stops-tram",
                    source = stopsSource,
                    minZoom = if (itineraryGeoJson != null) 12.5f else 13f,
                    filter = feature["stop_priority"].convertToString() eq const("1"),
                    radius = const(4.dp),
                    color = const(Color(0xFF1F2937)),
                    onClick = { f -> onStop(f.firstOrNull()?.properties?.get("nom")?.jsonPrimitive?.contentOrNull) },
                )
                CircleLayer(
                    id = "transport-stops-priority",
                    source = stopsSource,
                    minZoom = if (itineraryGeoJson != null) 12.5f else 0f,
                    filter = feature["stop_priority"].convertToString() eq const("2"),
                    radius = const(5.dp),
                    color = const(Color(0xFF1F2937)),
                    onClick = { f -> onStop(f.firstOrNull()?.properties?.get("nom")?.jsonPrimitive?.contentOrNull) },
                )
            }

            // ------------------------------------------------------------------
            // Vehicle positions
            // Painters were resolved in the outer scope — no @Composable calls here.
            // ------------------------------------------------------------------
            if (vehiclesGeoJson != null) {
                CircleLayer(
                    id = "vehicles-bg",
                    source = vehicleSource,
                    radius = const(9.dp),
                    color = feature["color"].convertToColor(),
                    onClick = { f ->
                        val nom = f.firstOrNull()?.properties?.get("lineName")?.jsonPrimitive?.contentOrNull
                        if (nom != null) { onVehicleClick(nom); ClickResult.Consume } else ClickResult.Pass
                    },
                )
                val vehicleIconImage = switch(
                    feature["markerType"].convertToString(),
                    case("TRAM", image(tramPainter, glyphDpSize(tramPainter, 11f))),
                    fallback = image(busPainter, glyphDpSize(busPainter, 11f))
                )
                SymbolLayer(
                    id = "vehicles-pictogram",
                    source = vehicleSource,
                    iconImage = vehicleIconImage,
                    iconAllowOverlap = const(true),
                    onClick = { f ->
                        val nom = f.firstOrNull()?.properties?.get("lineName")?.jsonPrimitive?.contentOrNull
                        if (nom != null) { onVehicleClick(nom); ClickResult.Consume } else ClickResult.Pass
                    },
                )
            }

            // ------------------------------------------------------------------
            // User location dot
            // ------------------------------------------------------------------
            if (userLocation != null) {
                CircleLayer(
                    id = "user-location",
                    source = userSource,
                    radius = const(8.dp),
                    color = const(Color(0xFF3B82F6)),
                )
            }
        }
    }
}

/**
 * A [DpSize] that rasterizes [painter] at a fixed [heightDp] with width proportional to the
 * painter's intrinsic aspect ratio (clamped), so glyphs keep their shape instead of squishing.
 */
private fun glyphDpSize(painter: Painter, heightDp: Float): DpSize {
    val size = painter.intrinsicSize
    val ratio = if (size.isSpecified && size.width > 0f && size.height > 0f) {
        (size.width / size.height).coerceIn(0.4f, 2.5f)
    } else {
        1f
    }
    return DpSize((heightDp * ratio).dp, heightDp.dp)
}
