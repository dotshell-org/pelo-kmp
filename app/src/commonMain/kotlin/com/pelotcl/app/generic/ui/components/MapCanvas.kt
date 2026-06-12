package com.pelotcl.app.generic.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pelotcl.app.generic.data.models.geojson.FeatureCollection
import com.pelotcl.app.generic.data.models.geojson.StopCollection
import com.pelotcl.app.generic.utils.map.toLinesGeoJson
import com.pelotcl.app.generic.utils.map.toStopsGeoJson
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToColor
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
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
            val stopsGeoJson = remember(stops) { stops.toStopsGeoJson() }
            val stopsSource = rememberGeoJsonSource(data = GeoJsonData.JsonString(stopsGeoJson))
            CircleLayer(
                id = "transport-stops",
                source = stopsSource,
                radius = const(3.dp),
                color = const(Color(0xFF1F2937)),
                onClick = { features ->
                    val nom = features.firstOrNull()?.properties?.get("nom")?.jsonPrimitive?.contentOrNull
                    if (nom != null) {
                        onStopClick(nom)
                        ClickResult.Consume
                    } else {
                        ClickResult.Pass
                    }
                },
            )
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
