package com.pelotcl.app.generic.utils.map

import com.pelotcl.app.generic.data.models.geojson.FeatureCollection
import com.pelotcl.app.generic.data.models.geojson.StopCollection
import com.pelotcl.app.generic.service.TransportServiceProvider
import com.pelotcl.app.generic.utils.LineColorHelper
import com.pelotcl.app.generic.utils.geo.StopsGeoJsonManager
import com.pelotcl.app.generic.utils.graphics.LineIconResolver
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

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
 * Result of [toStopsGeoJsonByPriority]: the GeoJSON, the distinct icon drawable names used (to build
 * an `iconImage` expression), and the largest number of icons stacked on a single stop (so the
 * caller can create one offset layer per `slot`).
 */
class StopsRenderData(val geoJson: String, val iconNames: Set<String>, val maxIcons: Int)

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
fun StopCollection.toStopsGeoJsonByPriority(hasDrawable: (String) -> Boolean): StopsRenderData {
    val lineRules = TransportServiceProvider.getTransportLineRules()
    val iconNames = LinkedHashSet<String>()
    // Merge strong-line stops sharing a name into one point (like Android) so a multi-line station
    // is a single marker rather than a cluster of overlapping platform stops.
    val mergedStops = StopsGeoJsonManager.mergeStopsByName(features)
    var maxIcons = 1
    val json = buildJsonObject {
        put("type", "FeatureCollection")
        putJsonArray("features") {
            for (stop in mergedStops) {
                val lines = LineIconResolver.parseDesserte(stop.properties.desserte)
                // One icon per strong line glyph (priority 2 metro/funicular/strong bus, 1 tram) plus
                // one per unique bus mode (priority 0) — exactly what Android stacks on a stop.
                val icons = ArrayList<Pair<String, Int>>()
                for (line in lines) {
                    val upper = line.uppercase()
                    if (lineRules.isStrongLine(upper)) {
                        val priority = if (upper.startsWith("T")) 1 else 2
                        val name = LineIconResolver.getDrawableNameForLineName(line)
                        if (hasDrawable(name)) icons.add(name to priority)
                    }
                }
                val uniqueModes = lines
                    .filterNot { lineRules.isStrongLine(it.uppercase()) }
                    .mapNotNull { lineRules.getModeIcon(it) }
                    .distinct()
                for (mode in uniqueModes) {
                    if (hasDrawable(mode)) icons.add(mode to 0)
                }
                if (icons.isEmpty()) continue
                val coordinates = stop.geometry.coordinates
                if (coordinates.size < 2) continue
                if (icons.size > maxIcons) maxIcons = icons.size

                // Slots spread the icons symmetrically (…, -2, 0, 2, … or …, -1, 1, …).
                var slot = -(icons.size - 1)
                for ((iconName, priority) in icons) {
                    iconNames.add(iconName)
                    addJsonObject {
                        put("type", "Feature")
                        put("id", "${stop.id}_$slot")
                        putJsonObject("geometry") {
                            put("type", "Point")
                            putJsonArray("coordinates") {
                                for (coordinate in coordinates) add(coordinate)
                            }
                        }
                        putJsonObject("properties") {
                            put("nom", stop.properties.nom)
                            put("desserte", stop.properties.desserte)
                            put("stop_priority", priority)
                            put("icon", iconName)
                            put("slot", slot)
                        }
                    }
                    slot += 2
                }
            }
        }
    }.toString()
    return StopsRenderData(json, iconNames, maxIcons)
}
