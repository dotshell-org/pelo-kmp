package eu.dotshell.massilia.generic.utils.map

import eu.dotshell.massilia.generic.data.models.geojson.FeatureCollection
import eu.dotshell.massilia.generic.data.models.geojson.StopCollection
import eu.dotshell.massilia.generic.data.models.realtime.vehiclepositions.SimpleVehiclePosition
import eu.dotshell.massilia.generic.service.TransportServiceProvider
import eu.dotshell.massilia.generic.utils.LineColorHelper
import eu.dotshell.massilia.generic.utils.geo.StopsGeoJsonManager
import eu.dotshell.massilia.generic.utils.graphics.LineIconResolver
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import eu.dotshell.massilia.generic.data.repository.itinerary.itinerary.JourneyResult
import eu.dotshell.massilia.generic.ui.viewmodel.TransportViewModel
import eu.dotshell.massilia.generic.data.models.geojson.Feature
import eu.dotshell.massilia.generic.utils.location.GeoPoint

/**
 * Converts a transport [FeatureCollection] (line geometries with MultiLineString
 * coordinates and a non-standard `multiLineStringGeometry` field) into a standard
 * GeoJSON FeatureCollection string suitable for a maplibre-compose GeoJSON source.
 *
 * Each output feature carries `lineName` and a resolved `color` property so a
 * LineLayer can colour lines with a data-driven expression. Replaces the former
 * Gson-based GeoJSON construction.
 */
fun FeatureCollection.toLinesGeoJson(): String = buildJsonObject {
    put("type", "FeatureCollection")
    putJsonArray("features") {
        for (feature in features) {
            addJsonObject {
                put("type", "Feature")
                put("id", feature.id)
                putJsonObject("geometry") {
                    put("type", "MultiLineString")
                    putJsonArray("coordinates") {
                        for (line in feature.multiLineStringGeometry.coordinates) {
                            addJsonArray {
                                for (point in line) {
                                    addJsonArray {
                                        for (coordinate in point) add(coordinate)
                                    }
                                }
                            }
                        }
                    }
                }
                putJsonObject("properties") {
                    put("lineName", feature.properties.lineName)
                    put("color", LineColorHelper.getColorForLine(feature))
                    val lineRules = TransportServiceProvider.getTransportLineRules()
                    val type = lineRules.getTransportType(feature.properties.lineName)
                    put("isMetroOrFunicular", if (type == "Métro" || type == "Funiculaire") "yes" else "no")
                }
            }
        }
    }
}.toString()

/**
 * Converts a [StopCollection] (transport stops, Point geometry) into a standard
 * GeoJSON FeatureCollection string for a maplibre-compose GeoJSON source.
 * Each feature carries `nom` and `desserte` properties.
 */
fun StopCollection.toStopsGeoJson(): String = buildJsonObject {
    put("type", "FeatureCollection")
    putJsonArray("features") {
        for (stop in features) {
            addJsonObject {
                put("type", "Feature")
                put("id", stop.id)
                putJsonObject("geometry") {
                    put("type", "Point")
                    putJsonArray("coordinates") {
                        for (coordinate in stop.geometry.coordinates) add(coordinate)
                    }
                }
                putJsonObject("properties") {
                    put("nom", stop.properties.nom)
                    put("desserte", stop.properties.desserte)
                }
            }
        }
    }
}.toString()

/**
 * Converts live vehicle positions into a GeoJSON FeatureCollection string. Each feature carries
 * `lineName` (for click handling) and `bearing`, and one Point geometry.
 */
fun toVehiclesGeoJson(positions: List<SimpleVehiclePosition>): String = buildJsonObject {
    val lineRules = TransportServiceProvider.getTransportLineRules()
    put("type", "FeatureCollection")
    putJsonArray("features") {
        for (vehicle in positions) {
            addJsonObject {
                put("type", "Feature")
                put("id", vehicle.vehicleId)
                putJsonObject("geometry") {
                    put("type", "Point")
                    putJsonArray("coordinates") {
                        add(vehicle.longitude)
                        add(vehicle.latitude)
                    }
                }
                putJsonObject("properties") {
                    put("lineName", vehicle.lineName)
                    vehicle.bearing?.let { put("bearing", it) }
                    put("color", LineColorHelper.getColorForLineStringAux(vehicle.lineName))
                    put("markerType", lineRules.getVehicleMarkerType(vehicle.lineName).name)
                }
            }
        }
    }
}.toString()

/**
 * Result of [toStopsGeoJsonByPriority]: the GeoJSON, the distinct icon drawable names used (to build
 * an `iconImage` expression), and the largest number of icons stacked on a single stop (so the
 * caller can create one offset layer per `slot`).
 */
data class StopsRenderData(val geoJson: String, val iconNames: Set<String>, val maxIcons: Int)

/**
 * Like [toStopsGeoJson], but each feature also carries a `stop_priority` derived from the lines
 * serving the stop (2 = metro/funicular or strong bus, 1 = tram, 0 = ordinary bus) and an `icon`
 * (the drawable name of the highest-priority line that has an available drawable). A map can then
 * reveal stops progressively by zoom (metro always, tram from mid-zoom, bus only when zoomed in)
 * and draw the line glyph per stop, matching the Android map. Unlike `StopsGeoJsonManager`, this
 * emits every stop once with no icon-availability gating on the feature itself, so stops always
 * render; [hasDrawable] only decides which `icon` name to attach (and is collected for image
 * registration). Returns the distinct icon names so the caller can build an `iconImage` expression.
 */
suspend fun StopCollection.toStopsGeoJsonByPriority(
    selectedLineName: String? = null,
    hasDrawable: (String) -> Boolean,
    currentZoom: Double = 20.0,
    shouldIncludeBus: Boolean = true,
): StopsRenderData {
    val lineRules = TransportServiceProvider.getTransportLineRules()
    val iconNames = LinkedHashSet<String>()
    // Merge strong-line stops sharing a name into one point (like Android) so a multi-line station
    // is a single marker rather than a cluster of overlapping platform stops.
    val mergedStops = StopsGeoJsonManager.mergeStopsByName(features)
    var maxIcons = 1
    val normSelected = selectedLineName?.let { lineRules.normalizeForComparison(it) }

    data class SlotRecord(
        val id: String,
        val coordinates: List<Double>,
        val nom: String,
        val desserte: String,
        val priority: Int,
        val iconName: String,
        val slot: Int,
    )
    val slots = ArrayList<SlotRecord>(mergedStops.size * 2)

    for (stop in mergedStops) {
        currentCoroutineContext().ensureActive()
        val lines = LineIconResolver.parseDesserte(stop.properties.desserte)
        val icons = ArrayList<Pair<String, Int>>()

        if (normSelected != null) {
            val isServedBySelected = lines.any { lineRules.normalizeForComparison(it) == normSelected }
            if (isServedBySelected) {
                if (lineRules.isStrongLine(normSelected)) {
                    // Metro / Tram / Funicular: show the line-specific icon (e.g. "a", "t1")
                    val priority = if (normSelected.startsWith("T")) 1 else 2
                    val name = LineIconResolver.getDrawableNameForLineName(selectedLineName)
                    if (hasDrawable(name)) {
                        icons.add(name to priority)
                    }
                } else {
                    // Non-strong line: use the mode icon from config (e.g. mode_chrono, mode_bus)
                    // falling back to mode_bus if the configured icon drawable is missing
                    val configMode = lineRules.getModeIcon(normSelected)
                    val mode = if (configMode != null && hasDrawable(configMode)) configMode
                               else if (hasDrawable("mode_bus")) "mode_bus"
                               else null
                    if (mode != null) {
                        icons.add(mode to 2)
                    }
                }
            }
        } else {
            // 1. Metro / Tram / Funicular
            for (line in lines) {
                val upper = line.uppercase()
                if (lineRules.isStrongLine(upper)) {
                    val priority = if (upper.startsWith("T")) 1 else 2
                    val name = LineIconResolver.getDrawableNameForLineName(line)
                    if (hasDrawable(name)) icons.add(name to priority)
                }
            }

            // 2. Other weak lines/modes — skip bus icons below zoom 17
            // (they're invisible anyway due to minZoom and just waste CPU/GPU)
            if (shouldIncludeBus) {
                val uniqueModes = lines
                    .filterNot { lineRules.isStrongLine(it.uppercase()) }
                    .mapNotNull { lineRules.getModeIcon(it) }
                    .distinct()
                for (mode in uniqueModes) {
                    if (hasDrawable(mode)) icons.add(mode to 0)
                }
            }
        }
        if (icons.isEmpty()) continue
        val coordinates = stop.geometry.coordinates
        if (coordinates.size < 2) continue
        if (icons.size > maxIcons) maxIcons = icons.size

        // Slots spread the icons symmetrically (…, -2, 0, 2, … or …, -1, 1, …).
        var slot = -(icons.size - 1)
        for ((iconName, priority) in icons) {
            iconNames.add(iconName)
            slots.add(SlotRecord(
                id = "${stop.id}_$slot",
                coordinates = coordinates,
                nom = stop.properties.nom,
                desserte = stop.properties.desserte,
                priority = priority,
                iconName = iconName,
                slot = slot,
            ))
            slot += 2
        }
    }

    val json = buildJsonObject {
        put("type", "FeatureCollection")
        putJsonArray("features") {
            for (record in slots) {
                addJsonObject {
                    put("type", "Feature")
                    put("id", record.id)
                    putJsonObject("geometry") {
                        put("type", "Point")
                        putJsonArray("coordinates") {
                            for (coordinate in record.coordinates) add(coordinate)
                        }
                    }
                    putJsonObject("properties") {
                        put("nom", record.nom)
                        put("desserte", record.desserte)
                        put("stop_priority", record.priority.toString()) // String to match convertToString() filter
                        put("icon", record.iconName)
                        put("slot", record.slot)
                    }
                }
            }
        }
    }.toString()
    return StopsRenderData(json, iconNames, maxIcons)
}

/**
 * Converts a list of calculated itinerary journeys into a standard GeoJSON FeatureCollection string.
 * This reconstructs the actual cut line segments or draws straight fallback paths between stops/legs.
 */
suspend fun toItinerariesGeoJson(
    journeys: List<JourneyResult>,
    selectedJourney: JourneyResult?,
    viewModel: TransportViewModel
): String {
    val journeysToDraw = selectedJourney?.let { listOf(it) } ?: journeys
    val lineNames = journeysToDraw.flatMap { journey ->
        journey.legs.mapNotNull { leg ->
            if (!leg.isWalking) leg.routeName else null
        }
    }.distinct()

    val lineFeaturesMap = lineNames.associateWith { lineName ->
        try {
            viewModel.transportRepository.getLineByName(lineName)
                .getOrElse { emptyList() }
        } catch (_: Exception) {
            emptyList<Feature>()
        }
    }

    return buildJsonObject {
        put("type", "FeatureCollection")
        putJsonArray("features") {
            for ((journeyIndex, journey) in journeysToDraw.withIndex()) {
                for ((legIndex, leg) in journey.legs.withIndex()) {
                    val lineColor = if (leg.isWalking) {
                        "#6B7280"
                    } else {
                        LineColorHelper.getColorForLineStringAux(leg.routeName ?: "")
                    }

                    var drewSection = false

                    if (!leg.isWalking) {
                        val lineName = leg.routeName ?: ""
                        val lines = lineFeaturesMap[lineName] ?: emptyList()

                        if (lines.isNotEmpty()) {
                            val sectionedLines = viewModel.sectionLinesBetweenStops(
                                lines,
                                leg.fromStopId,
                                leg.toStopId,
                                leg
                            )
                            if (sectionedLines.isNotEmpty()) {
                                val sectionedLine = sectionedLines.first()
                                val firstLine = sectionedLine.multiLineStringGeometry.coordinates.firstOrNull()
                                if (!firstLine.isNullOrEmpty() && firstLine.size > 1) {
                                    addJsonObject {
                                        put("type", "Feature")
                                        putJsonObject("geometry") {
                                            put("type", "LineString")
                                            putJsonArray("coordinates") {
                                                for (coord in firstLine) {
                                                    addJsonArray {
                                                        add(coord[0])
                                                        add(coord[1])
                                                    }
                                                }
                                            }
                                        }
                                        putJsonObject("properties") {
                                            put("color", lineColor)
                                            put("isWalking", "no")
                                            put("journeyIndex", journeyIndex)
                                            put("legIndex", legIndex)
                                        }
                                    }
                                    drewSection = true
                                }
                            }
                        }
                    }

                    if (!drewSection) {
                        addJsonObject {
                            put("type", "Feature")
                            putJsonObject("geometry") {
                                put("type", "LineString")
                                putJsonArray("coordinates") {
                                    addJsonArray {
                                        add(leg.fromLon)
                                        add(leg.fromLat)
                                    }
                                    for (stop in leg.intermediateStops) {
                                        addJsonArray {
                                            add(stop.lon)
                                            add(stop.lat)
                                        }
                                    }
                                    addJsonArray {
                                        add(leg.toLon)
                                        add(leg.toLat)
                                    }
                                }
                            }
                            putJsonObject("properties") {
                                put("color", lineColor)
                                put("isWalking", if (leg.isWalking) "yes" else "no")
                                put("journeyIndex", journeyIndex)
                                put("legIndex", legIndex)
                            }
                        }
                    }
                }
            }
        }
    }.toString()
}

/**
 * Calculates the exact trace (list of GeoPoints) representing the journey path.
 * Reconstructs detailed transit geometries or walking paths segment-by-segment.
 */
suspend fun calculateJourneyTrace(
    journey: JourneyResult,
    viewModel: TransportViewModel
): List<GeoPoint> {
    val points = mutableListOf<GeoPoint>()

    val lineNames = journey.legs.mapNotNull { leg ->
        if (!leg.isWalking) leg.routeName else null
    }.distinct()

    val lineFeaturesMap = lineNames.associateWith { lineName ->
        try {
            viewModel.transportRepository.getLineByName(lineName)
                .getOrElse { emptyList() }
        } catch (_: Exception) {
            emptyList<Feature>()
        }
    }

    for (leg in journey.legs) {
        var drewSection = false

        if (!leg.isWalking) {
            val lineName = leg.routeName ?: ""
            val lines = lineFeaturesMap[lineName] ?: emptyList()

            if (lines.isNotEmpty()) {
                val sectionedLines = viewModel.sectionLinesBetweenStops(
                    lines,
                    leg.fromStopId,
                    leg.toStopId,
                    leg
                )
                if (sectionedLines.isNotEmpty()) {
                    val sectionedLine = sectionedLines.first()
                    val firstLine = sectionedLine.multiLineStringGeometry.coordinates.firstOrNull()
                    if (!firstLine.isNullOrEmpty() && firstLine.size > 1) {
                        for (coord in firstLine) {
                            points.add(GeoPoint(latitude = coord[1], longitude = coord[0]))
                        }
                        drewSection = true
                    }
                }
            }
        }

        if (!drewSection) {
            points.add(GeoPoint(latitude = leg.fromLat, longitude = leg.fromLon))
            for (stop in leg.intermediateStops) {
                points.add(GeoPoint(latitude = stop.lat, longitude = stop.lon))
            }
            points.add(GeoPoint(latitude = leg.toLat, longitude = leg.toLon))
        }
    }

    // Deduplicate consecutive identical coordinates
    val dedupedPoints = mutableListOf<GeoPoint>()
    for (p in points) {
        if (dedupedPoints.isEmpty() || dedupedPoints.last() != p) {
            dedupedPoints.add(p)
        }
    }
    return dedupedPoints
}
