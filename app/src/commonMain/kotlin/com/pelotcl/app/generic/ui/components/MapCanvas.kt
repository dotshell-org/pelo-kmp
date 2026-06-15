package com.pelotcl.app.generic.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
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
    interactive: Boolean = true,
    onStopClick: (stopName: String) -> Unit = {},
    onLineClick: (lineName: String) -> Unit = {},
    onVehicleClick: (lineName: String) -> Unit = {},
    centerOn: Position? = null,
) {
    LaunchedEffect(centerOn) {
        if (centerOn != null) {
            cameraState.animateTo(
                CameraPosition(target = centerOn, zoom = 15.0)
            )
        }
    }

    MaplibreMap(
        modifier = modifier,
        baseStyle = BaseStyle.Uri(styleUrl),
        cameraState = cameraState,
        options = MapOptions(gestureOptions = mapGestureOptions(interactive)),
    ) {
        if (lines != null) {
            val linesGeoJson = remember(lines) { lines.toLinesGeoJson() }
            val lineSource = rememberGeoJsonSource(data = GeoJsonData.JsonString(linesGeoJson))
            LineLayer(
                id = "transport-lines",
                source = lineSource,
                color = feature["color"].convertToColor(),
                width = const(3.dp),
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

        if (stops != null) {
            // Stop GeoJSON: each feature carries a `stop_priority` (2 = metro/funicular/strong bus,
            // 1 = tram, 0 = bus) so stops reveal progressively by zoom, plus an `icon` (line glyph
            // drawable name) so each stop draws its line icon — matching the Android map.
            val context = LocalPlatformContext.current
            val drawableProvider = remember(context) { DrawableProvider(context) }
            val render = remember(stops) { stops.toStopsGeoJsonByPriority { drawableProvider.hasDrawable(it) } }
            val stopsSource = rememberGeoJsonSource(data = GeoJsonData.JsonString(render.geoJson))

            // Per-feature icon image: embed each line-glyph painter and select it by the feature's
            // `icon` name. image(painter) embeds the bitmap directly, so it works without named-image
            // registration (which maplibre-compose 0.13 can't do on iOS).
            val iconNames = render.iconNames.toList()
            val iconImage: org.maplibre.compose.expressions.ast.Expression<ImageValue>? =
                if (iconNames.isEmpty()) {
                    null
                } else {
                    // Rasterize each glyph at its own aspect ratio (fixed height, proportional
                    // width) so non-square line badges keep their shape instead of being squished.
                    fun sizeFor(p: Painter): DpSize {
                        val s = p.intrinsicSize
                        val ratio = if (s.isSpecified && s.width > 0f && s.height > 0f) {
                            (s.width / s.height).coerceIn(0.4f, 2.5f)
                        } else {
                            1f
                        }
                        val height = 19f
                        return DpSize((height * ratio).dp, height.dp)
                    }
                    val cases = ArrayList<Case<StringValue, ImageValue>>(iconNames.size)
                    for (name in iconNames) {
                        val painter = drawableProvider.getPainter(name)
                        cases.add(case(name, image(painter, sizeFor(painter))))
                    }
                    val firstPainter = drawableProvider.getPainter(iconNames.first())
                    val fallback = image(firstPainter, sizeFor(firstPainter))
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
                val tiers = listOf(2 to 0f, 1 to 13f, 0 to 16f)
                for ((priority, minZoom) in tiers) {
                    for (slot in slots) {
                        SymbolLayer(
                            id = "transport-stops-$priority-$slot",
                            source = stopsSource,
                            minZoom = minZoom,
                            filter = (feature["stop_priority"].convertToNumber() eq const(priority)) and
                                (feature["slot"].convertToNumber() eq const(slot)),
                            iconImage = iconImage,
                            iconOffset = const(DpOffset(0.dp, (slot * 10).dp)),
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
            val itinerarySource = rememberGeoJsonSource(data = GeoJsonData.JsonString(itineraryGeoJson))
            LineLayer(
                id = "itinerary",
                source = itinerarySource,
                color = feature["color"].convertToColor(),
                width = const(6.dp),
            )
        }

        if (vehiclesGeoJson != null) {
            // Live vehicles rendered as line-coloured dots (data-driven `color` per feature).
            // The legacy bus/tram glyph markers can be reintroduced once named-image
            // registration in maplibre-compose is settled.
            val vehicleSource = rememberGeoJsonSource(data = GeoJsonData.JsonString(vehiclesGeoJson))
            CircleLayer(
                id = "vehicles",
                source = vehicleSource,
                radius = const(7.dp),
                color = feature["color"].convertToColor(),
                onClick = { features ->
                    val lineName = features.firstOrNull()?.properties?.get("lineName")?.jsonPrimitive?.contentOrNull
                    if (lineName != null) {
                        onVehicleClick(lineName)
                        ClickResult.Consume
                    } else {
                        ClickResult.Pass
                    }
                },
            )
        }

        if (userLocation != null) {
            val userSource = rememberGeoJsonSource(
                data = GeoJsonData.JsonString(
                    """{"type":"Feature","geometry":{"type":"Point","coordinates":[${userLocation.longitude},${userLocation.latitude}]},"properties":{}}"""
                )
            )
            CircleLayer(
                id = "user-location",
                source = userSource,
                radius = const(8.dp),
                color = const(Color(0xFF3B82F6)),
            )
        }
    }
}
