package com.pelotcl.app.generic.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.pelotcl.app.generic.data.models.geojson.FeatureCollection
import com.pelotcl.app.generic.data.models.geojson.StopCollection
import com.pelotcl.app.generic.utils.map.toLinesGeoJson
import com.pelotcl.app.generic.utils.map.toStopsGeoJsonByPriority
import com.pelotcl.app.platform.DrawableProvider
import com.pelotcl.app.platform.LocalPlatformContext
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.map.MapOptions
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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Cross-platform map canvas built on maplibre-compose (declarative).
 *
 * Increment 5: renders real, externally-provided GeoJSON:
 *  - [linesGeoJson]: a GeoJSON FeatureCollection of transport line geometries.
 *  - [userLocation]: the device location, drawn as a blue dot.
 *
 * Per-line colouring (data-driven expression), stops and itinerary layers come
 * next. Built alongside the legacy [MapLibreView]; not yet wired into PlanScreen.
 */
@Composable
fun MapCanvas(
    modifier: Modifier = Modifier,
    styleUrl: String,
    initialLatitude: Double = 45.75,
    initialLongitude: Double = 4.85,
    initialZoom: Double = 10.0,
    cameraState: CameraState = rememberCameraState(
        firstPosition = CameraPosition(
            target = Position(latitude = initialLatitude, longitude = initialLongitude),
            zoom = initialZoom,
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
    centerOn: Position? = null,
    focusZoom: Double? = null,
) {
    val fallbackPainter = remember {
        object : Painter() {
            override val intrinsicSize: Size = Size(14f, 14f)
            override fun DrawScope.onDraw() {
                drawCircle(color = Color(0xFF1F2937))
            }
        }
    }

    LaunchedEffect(centerOn, focusZoom) {
        if (centerOn != null) {
            val targetZoom = focusZoom ?: 13.0
            cameraState.animateTo(
                CameraPosition(target = centerOn, zoom = targetZoom)
            )
        }
    }

    MaplibreMap(
        modifier = modifier,
        baseStyle = BaseStyle.Uri(styleUrl),
        cameraState = cameraState,
        options = MapOptions(gestureOptions = mapGestureOptions(interactive)),
    ) {
        // Unconditional sources to stabilize Compose slots and prevent MLNRedundantSourceException on iOS
        val linesGeoJson = remember(lines) { lines?.toLinesGeoJson() ?: """{"type":"FeatureCollection","features":[]}""" }
        val lineSource = rememberGeoJsonSource(data = GeoJsonData.JsonString(linesGeoJson))

        val context = LocalPlatformContext.current
        val drawableProvider = remember(context) { DrawableProvider(context) }
        val render = remember(stops, selectedLineName) { stops?.toStopsGeoJsonByPriority(selectedLineName) { drawableProvider.hasDrawable(it) } }
        val stopsSource = rememberGeoJsonSource(data = GeoJsonData.JsonString(render?.geoJson ?: """{"type":"FeatureCollection","features":[]}"""))

        val itinerarySource = rememberGeoJsonSource(data = GeoJsonData.JsonString(itineraryGeoJson ?: """{"type":"FeatureCollection","features":[]}"""))

        val vehicleSource = rememberGeoJsonSource(data = GeoJsonData.JsonString(vehiclesGeoJson ?: """{"type":"FeatureCollection","features":[]}"""))

        val userLocationGeoJson = remember(userLocation) {
            if (userLocation != null) {
                """{"type":"Feature","geometry":{"type":"Point","coordinates":[${userLocation.longitude},${userLocation.latitude}]},"properties":{}}"""
            } else {
                """{"type":"FeatureCollection","features":[]}"""
            }
        }
        val userSource = rememberGeoJsonSource(data = GeoJsonData.JsonString(userLocationGeoJson))

        if (lines != null) {
            // Thin visible lines
            LineLayer(
                id = "transport-lines",
                source = lineSource,
                color = feature["color"].convertToColor(),
                width = switch(
                    feature["isMetroOrFunicular"].convertToString(),
                    case("yes", const(3.dp)),
                    fallback = const(1.5.dp)
                ),
                onClick = { features ->
                    val lineName = features.firstOrNull()?.properties?.get("lineName")?.jsonPrimitive?.contentOrNull
                    if (lineName != null) {
                        onLineClick(lineName)
                        ClickResult.Consume
                    } else {
                        ClickResult.Pass
                    }
                },
            )
            // Thick invisible lines to make selecting them much easier on touch screens
            LineLayer(
                id = "transport-lines-tap",
                source = lineSource,
                color = const(Color(0x01000000)), // Tiny alpha to ensure MapLibre hit-tests it
                width = const(24.dp),
                onClick = { features ->
                    val lineName = features.firstOrNull()?.properties?.get("lineName")?.jsonPrimitive?.contentOrNull
                    if (lineName != null) {
                        onLineClick(lineName)
                        ClickResult.Consume
                    } else {
                        ClickResult.Pass
                    }
                },
            )
        }

        if (stops != null && render != null) {
            // Stop GeoJSON: each feature carries a `stop_priority` (2 = metro/funicular/strong bus,
            // 1 = tram, 0 = bus) so stops reveal progressively by zoom, plus an `icon` (line glyph
            // drawable name) so each stop draws its line icon — matching the Android map.
            // Per-feature icon image: embed each line-glyph painter and select it by the feature's
            // `icon` name. image(painter) embeds the bitmap directly, so it works without named-image
            // registration (which maplibre-compose 0.13 can't do on iOS).
            val iconNames = render.iconNames.toList()
            val iconImage: org.maplibre.compose.expressions.ast.Expression<ImageValue>? =
                if (iconNames.isEmpty()) {
                    null
                } else {
                    val cases = ArrayList<Case<StringValue, ImageValue>>(iconNames.size)
                    for (name in iconNames) {
                        val painter = drawableProvider.getPainter(name)
                        cases.add(case(name, image(painter, glyphDpSize(painter, 17f))))
                    }
                    val firstPainter = drawableProvider.getPainter(iconNames.first())
                    val fallback = image(firstPainter, glyphDpSize(firstPainter, 17f))
                    switch(feature["icon"].convertToString(), *cases.toTypedArray(), fallback = fallback)
                }

            val onStop: (String?) -> ClickResult = { nom ->
                if (nom != null) { onStopClick(nom); ClickResult.Consume } else ClickResult.Pass
            }

            if (iconImage != null) {
                // One layer per (priority, slot): priority gates the zoom (metro/funicular always,
                // tram from z13, bus from z16) via minZoom; slot stacks the icons of a multi-line
                // stop vertically by offset — reproducing Android's stacked line icons.
                val slots = (-(render.maxIcons - 1)..(render.maxIcons - 1)).toList()
                // Android thresholds: metro/funicular from z12.5 (so they vanish when zoomed out
                // too far), tram from z14, bus from z17.
                val tiers = listOf(2 to 12.5f, 1 to 14f, 0 to 17f)
                for ((priority, minZoom) in tiers) {
                    for (slot in slots) {
                        SymbolLayer(
                            id = "transport-stops-$priority-$slot",
                            source = stopsSource,
                            minZoom = minZoom,
                            filter = (feature["stop_priority"].convertToNumber() eq const(priority)) and
                                (feature["slot"].convertToNumber() eq const(slot)),
                            iconImage = iconImage,
                            // slot step is 2, so the gap between adjacent icons is 2 * this value;
                            // ~= the glyph height so stacked icons touch with no visible gap.
                            iconOffset = const(DpOffset(0.dp, (slot * 8).dp)),
                            iconAllowOverlap = const(true),
                            onClick = { f -> onStop(f.firstOrNull()?.properties?.get("nom")?.jsonPrimitive?.contentOrNull) },
                        )
                    }
                }
            } else {
                // Fallback: plain dots when no line glyphs are available.
                CircleLayer(
                    id = "transport-stops-bus",
                    source = stopsSource,
                    minZoom = 16f,
                    filter = feature["stop_priority"].convertToNumber() eq const(0),
                    radius = const(3.dp),
                    color = const(Color(0xFF6B7280)),
                    onClick = { f -> onStop(f.firstOrNull()?.properties?.get("nom")?.jsonPrimitive?.contentOrNull) },
                )
                CircleLayer(
                    id = "transport-stops-tram",
                    source = stopsSource,
                    minZoom = 13f,
                    filter = feature["stop_priority"].convertToNumber() eq const(1),
                    radius = const(4.dp),
                    color = const(Color(0xFF1F2937)),
                    onClick = { f -> onStop(f.firstOrNull()?.properties?.get("nom")?.jsonPrimitive?.contentOrNull) },
                )
                CircleLayer(
                    id = "transport-stops-priority",
                    source = stopsSource,
                    filter = feature["stop_priority"].convertToNumber() eq const(2),
                    radius = const(5.dp),
                    color = const(Color(0xFF1F2937)),
                    onClick = { f -> onStop(f.firstOrNull()?.properties?.get("nom")?.jsonPrimitive?.contentOrNull) },
                )
            }
        }

        if (itineraryGeoJson != null) {
            LineLayer(
                id = "itinerary",
                source = itinerarySource,
                color = feature["color"].convertToColor(),
                width = const(6.dp),
            )
        }

        if (vehiclesGeoJson != null) {
            // Circle background for vehicles (color based on line color)
            CircleLayer(
                id = "vehicles-bg",
                source = vehicleSource,
                radius = const(11.dp),
                color = feature["color"].convertToColor(),
                onClick = { f ->
                    val nom = f.firstOrNull()?.properties?.get("lineName")?.jsonPrimitive?.contentOrNull
                    if (nom != null) { onVehicleClick(nom); ClickResult.Consume } else ClickResult.Pass
                },
            )

            // White pictogram icon (bus or tram) centered inside the colored circle
            val vContext = LocalPlatformContext.current
            val vDrawables = remember(vContext) { DrawableProvider(vContext) }
            val busPainter = if (vDrawables.hasDrawable("ic_bus_vehicle")) vDrawables.getPainter("ic_bus_vehicle") else fallbackPainter
            val tramPainter = if (vDrawables.hasDrawable("ic_tramway_vehicle")) vDrawables.getPainter("ic_tramway_vehicle") else fallbackPainter

            val vehicleIconImage = switch(
                feature["markerType"].convertToString(),
                case("TRAM", image(tramPainter, glyphDpSize(tramPainter, 12f))),
                fallback = image(busPainter, glyphDpSize(busPainter, 12f))
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
