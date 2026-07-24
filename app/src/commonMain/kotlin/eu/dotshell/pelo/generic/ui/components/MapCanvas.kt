package eu.dotshell.pelo.generic.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
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
import org.maplibre.compose.expressions.dsl.lte
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.expressions.value.IconRotationAlignment
import org.maplibre.compose.expressions.value.ImageValue
import org.maplibre.compose.expressions.value.StringValue
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.util.ClickResult
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position
import kotlinx.serialization.json.JsonObject
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Geometry
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.compose.sources.getBaseSource
import eu.dotshell.pelo.generic.ui.theme.AccentColor
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture

private const val EMPTY_FEATURE_COLLECTION = """{"type":"FeatureCollection","features":[]}"""
private const val STOP_RENDER_MIN_ZOOM = 12.0
private const val BUS_RENDER_MIN_ZOOM = 16.0

// Remote styles whose descriptor is also bundled, so a cold start skips the descriptor fetch.
// The URL stays authoritative in config.json — it is what the offline downloader pulls tiles
// with — and is mapped to its bundled copy here.
//
// The bundled copies are NOT the upstream descriptors: hospital and school grounds are dropped
// so that land reads as ordinary. Edit them through tools/build_map_styles.py, which also builds
// the dark cuts (separate `asset://` styles). If an asset ever fails to load we fall back to the
// remote URL, which renders correctly but brings those tinted zones back.
private val BUNDLED_STYLE_DESCRIPTORS = mapOf(
    "https://tiles.openfreemap.org/styles/bright" to "bright.json",
    "https://tiles.openfreemap.org/styles/liberty" to "liberty.json",
)

/** The OpenMapTiles vector source both Standard and 3D are built on. Absent from Satellite. */
private const val BASEMAP_VECTOR_SOURCE_ID = "openmaptiles"

/**
 * Source layers holding the basemap's named features: `place` covers localities — hamlets,
 * neighbourhoods, suburbs, towns — and `poi` covers named points such as shops and venues.
 */
private val BASEMAP_PLACE_SOURCE_LAYERS = listOf("place", "poi")

/**
 * How long a finger must stay still on the map before it counts as dropping a pin. Close to the
 * platform long-press, which is what a hold on a map feels like it should cost elsewhere; the
 * stillness requirement, not the duration, is what keeps a pan from becoming a pin.
 */
private const val LONG_PRESS_HOLD_MS = 600L

/** A named basemap feature usable as an itinerary destination. */
private data class NamedPlace(val name: String, val latitude: Double, val longitude: Double)

/**
 * Reads a queried basemap feature as a destination, or null when it has no name or no point
 * geometry — an unnamed or non-point feature is nothing a user could have searched for.
 */
private fun Feature<Geometry, JsonObject?>.toNamedPlace(): NamedPlace? {
    val name = properties?.get("name")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        ?: return null
    val point = geometry as? Point ?: return null
    return NamedPlace(name, point.coordinates.latitude, point.coordinates.longitude)
}

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
    heading: Float? = null,
    vehiclesGeoJson: String? = null,
    vehicleIconName: String? = null,
    selectedLineName: String? = null,
    interactive: Boolean = true,
    onStopClick: (stopName: String) -> Unit = {},
    onLineClick: (lineName: String) -> Unit = {},
    onVehicleClick: (lineName: String) -> Unit = {},
    /** A named place or POI of the vector basemap was tapped. Never fires on Satellite. */
    onBasemapPlaceClick: (name: String, latitude: Double, longitude: Double) -> Unit = { _, _, _ -> },
    /** The user held a finger still on the map for [LONG_PRESS_HOLD_MS]. */
    onMapLongPress: (latitude: Double, longitude: Double) -> Unit = { _, _ -> },
    /** Marker for a point the user dropped, drawn above the transport layers. */
    droppedPin: Position? = null,
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
                drawCircle(color = Color.Black)
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
    // Resolve one Painter per icon. getPainter() is @Composable (painterResource internally,
    // which caches via its own remember, so identical iconNames return the loaded Painter with no
    // cascade). The icon set varies with the visible lines, so each call is wrapped in key(name):
    // without it, calling a @Composable a variable number of times across recompositions corrupts
    // positional memoization / the slot table.
    val iconPainters = remember(allIconNamesToLoad) { HashMap<String, Painter>(allIconNamesToLoad.size) }
    allIconNamesToLoad.forEach { name ->
        key(name) {
            iconPainters[name] = drawableProvider.getPainter(name)
        }
    }

    // Vehicle painters — call getPainter directly; painterResource caches internally. Each
    // pictogram ships a white and a near-black cut so it can contrast with any line colour.
    val busPainter: Painter =
        if (drawableProvider.hasDrawable("ic_bus_vehicle")) drawableProvider.getPainter("ic_bus_vehicle")
        else fallbackPainter
    val tramPainter: Painter =
        if (drawableProvider.hasDrawable("ic_tramway_vehicle")) drawableProvider.getPainter("ic_tramway_vehicle")
        else fallbackPainter
    val busDarkPainter: Painter =
        if (drawableProvider.hasDrawable("ic_bus_vehicle_dark")) drawableProvider.getPainter("ic_bus_vehicle_dark")
        else busPainter
    val tramDarkPainter: Painter =
        if (drawableProvider.hasDrawable("ic_tramway_vehicle_dark")) drawableProvider.getPainter("ic_tramway_vehicle_dark")
        else tramPainter

    // Direction cone for the user-location dot (resolved here, in the stable outer scope).
    val conePainter: Painter? =
        if (drawableProvider.hasDrawable("user_heading_cone")) drawableProvider.getPainter("user_heading_cone")
        else null

    val baseStyle = remember(styleUrl, context) {
        // Styles shipped as a local descriptor render from the bundled JSON so cold start skips the
        // style-descriptor network fetch. `asset://` styles (the dark cuts, Satellite) carry the
        // file name directly; the remote light styles keep their http URL in config — authoritative
        // for offline tile download, and MapLibre still serves offline-cached tiles matched by tile
        // URL — but resolve to their bundled descriptor here. Falls back to the network URL if the
        // asset can't be read.
        val assetName = when {
            styleUrl.startsWith("asset://") -> styleUrl.removePrefix("asset://")
            else -> BUNDLED_STYLE_DESCRIPTORS[styleUrl]
        }
        if (assetName != null) {
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

    // Drop-a-pin gesture. Watched in the Initial pass and never consumed, so the map still
    // receives every pan, pinch and tap — this only notices a finger that stays put. Detected
    // here rather than through MapLibre's own long-click so the threshold is ours to tune and
    // so the press is required to stay within touch slop.
    val density = LocalDensity.current
    val longPressModifier = if (interactive) {
        Modifier.pointerInput(cameraState, onMapLongPress, density) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val origin = down.position
                val heldStill = withTimeoutOrNull(LONG_PRESS_HOLD_MS) {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        val abandoned = change == null ||
                            !change.pressed ||
                            event.changes.size > 1 ||
                            (change.position - origin).getDistance() > viewConfiguration.touchSlop
                        if (abandoned) break
                    }
                } == null
                if (heldStill) {
                    val projection = cameraState.projection
                    if (projection != null) {
                        val offset = with(density) { DpOffset(origin.x.toDp(), origin.y.toDp()) }
                        val target = projection.positionFromScreenLocation(offset)
                        onMapLongPress(target.latitude, target.longitude)
                    }
                }
            }
        }
    } else {
        Modifier
    }

    MaplibreMap(
        modifier = modifier.then(longPressModifier),
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

            val droppedPinGeoJson = remember(droppedPin) {
                if (droppedPin != null) {
                    """{"type":"Feature","geometry":{"type":"Point","coordinates":[${droppedPin.longitude},${droppedPin.latitude}]},"properties":{}}"""
                } else {
                    EMPTY_FEATURE_COLLECTION
                }
            }
            val droppedPinSourceData = remember(droppedPinGeoJson) { GeoJsonData.JsonString(droppedPinGeoJson) }
            val droppedPinSource = rememberGeoJsonSource(data = droppedPinSourceData)

            // ------------------------------------------------------------------
            // Basemap places
            //
            // Place and POI labels belong to the vector basemap, not to any app layer, so
            // they carry no click handler. Binding transparent hit targets to the same
            // source makes them tappable. Declared before every other app layer so they sit
            // lowest: a tap that also lands on a stop, a line or a vehicle goes there first.
            //
            // Satellite has no vector source, so [getBaseSource] returns null and these are
            // simply not added — the feature is Standard and 3D only.
            // ------------------------------------------------------------------
            val basemapSource = getBaseSource(BASEMAP_VECTOR_SOURCE_ID)
            if (basemapSource != null) {
                BASEMAP_PLACE_SOURCE_LAYERS.forEach { placeSourceLayer ->
                    CircleLayer(
                        id = "basemap-place-tap-$placeSourceLayer",
                        source = basemapSource,
                        sourceLayer = placeSourceLayer,
                        color = const(Color.Transparent),
                        // Generous enough to catch the label as well as its anchor point.
                        radius = const(18.dp),
                        onClick = { features ->
                            val hit = features.firstNotNullOfOrNull { it.toNamedPlace() }
                            if (hit != null) {
                                onBasemapPlaceClick(hit.name, hit.latitude, hit.longitude)
                                ClickResult.Consume
                            } else {
                                // Unnamed feature: let the tap fall through to the layers below.
                                ClickResult.Pass
                            }
                        },
                    )
                }
            }

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
                    // Fully transparent: an almost-invisible black (0x01000000) stacks
                    // up where dozens of lines share a corridor and reads as a fat
                    // dark halo around every line in the all-lines mode.
                    color = const(Color.Transparent),
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
                // Coordinate endpoints (address / GPS point): same blue as the location dot
                CircleLayer(
                    id = "itinerary-endpoints",
                    source = itinerarySource,
                    filter = feature["endpoint"].convertToString() eq const("yes"),
                    radius = const(6.dp),
                    color = const(Color(0xFF3B82F6)),
                    strokeColor = const(Color.White),
                    strokeWidth = const(2.dp),
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
                            // Stay out of the global collision index: otherwise every
                            // live-vehicle source update re-runs symbol placement and
                            // makes all transport icons flash.
                            iconIgnorePlacement = const(true),
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
                    color = const(Color.Black),
                    onClick = { f -> onStop(f.firstOrNull()?.properties?.get("nom")?.jsonPrimitive?.contentOrNull) },
                )
                CircleLayer(
                    id = "transport-stops-tram",
                    source = stopsSource,
                    minZoom = if (itineraryGeoJson != null) 12.5f else 13f,
                    filter = feature["stop_priority"].convertToString() eq const("1"),
                    radius = const(4.dp),
                    color = const(Color.Black),
                    onClick = { f -> onStop(f.firstOrNull()?.properties?.get("nom")?.jsonPrimitive?.contentOrNull) },
                )
                CircleLayer(
                    id = "transport-stops-priority",
                    source = stopsSource,
                    minZoom = if (itineraryGeoJson != null) 12.5f else 0f,
                    filter = feature["stop_priority"].convertToString() eq const("2"),
                    radius = const(5.dp),
                    color = const(Color.Black),
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
                // Vehicles are drawn in their line's own colour, which across an operator's
                // palette ranges from near-black to pure yellow. `markerStyle` carries the
                // variant that reads on this line's dot; the white pictogram stays the default.
                val vehicleIconImage = switch(
                    feature["markerStyle"].convertToString(),
                    case("TRAM", image(tramPainter, glyphDpSize(tramPainter, 11f))),
                    case("TRAM_DARK", image(tramDarkPainter, glyphDpSize(tramDarkPainter, 11f))),
                    case("BUS_DARK", image(busDarkPainter, glyphDpSize(busDarkPainter, 11f))),
                    fallback = image(busPainter, glyphDpSize(busPainter, 11f))
                )
                SymbolLayer(
                    id = "vehicles-pictogram",
                    source = vehicleSource,
                    iconImage = vehicleIconImage,
                    iconAllowOverlap = const(true),
                    iconIgnorePlacement = const(true),
                    onClick = { f ->
                        val nom = f.firstOrNull()?.properties?.get("lineName")?.jsonPrimitive?.contentOrNull
                        if (nom != null) { onVehicleClick(nom); ClickResult.Consume } else ClickResult.Pass
                    },
                )
            }

            // ------------------------------------------------------------------
            // User location: direction cone (below the dot) + dot
            // ------------------------------------------------------------------
            if (userLocation != null && heading != null && conePainter != null) {
                SymbolLayer(
                    id = "user-heading",
                    source = userSource,
                    iconImage = image(conePainter, DpSize(72.dp, 72.dp)),
                    iconRotate = const(heading),
                    // Map-aligned so the cone points at the real-world heading even when the map is
                    // rotated; anchored at its apex so it pivots around the dot.
                    iconRotationAlignment = const(IconRotationAlignment.Map),
                    iconAnchor = const(SymbolAnchor.Bottom),
                    iconAllowOverlap = const(true),
                )
            }
            if (userLocation != null) {
                CircleLayer(
                    id = "user-location",
                    source = userSource,
                    radius = const(8.dp),
                    color = const(Color(0xFF3B82F6)),
                )
            }

            // ------------------------------------------------------------------
            // Dropped pin — last, so it sits above every other marker.
            // ------------------------------------------------------------------
            if (droppedPin != null) {
                CircleLayer(
                    id = "dropped-pin",
                    source = droppedPinSource,
                    radius = const(9.dp),
                    color = const(AccentColor),
                    strokeColor = const(Color.White),
                    strokeWidth = const(3.dp),
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
