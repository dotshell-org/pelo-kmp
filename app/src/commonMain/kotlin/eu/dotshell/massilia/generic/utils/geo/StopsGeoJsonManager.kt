package eu.dotshell.massilia.generic.utils.geo

import eu.dotshell.massilia.generic.data.models.geojson.Feature
import eu.dotshell.massilia.generic.data.models.geojson.StopFeature
import eu.dotshell.massilia.generic.data.models.stops.StopGeometry
import eu.dotshell.massilia.generic.data.models.stops.StopProperties
import eu.dotshell.massilia.generic.service.TransportServiceProvider
import eu.dotshell.massilia.generic.utils.graphics.LineIconResolver
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object StopsGeoJsonManager {

    private val lineRules get() = TransportServiceProvider.getTransportLineRules()

    private fun linesForStop(stop: StopFeature): List<String> =
        LineIconResolver.parseDesserte(stop.properties.desserte)

    fun mergeStopsByName(stops: List<StopFeature>): List<StopFeature> {
        fun normalizeStopName(name: String): String {
            return name.filter { c -> (c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9') || c.code > 127 }.lowercase()
        }

        val strongLineStops = mutableListOf<StopFeature>()
        val weakLineStops = mutableListOf<StopFeature>()

        stops.forEach { stop ->
            val allLines = linesForStop(stop)
            val strongLines = allLines.filter { lineRules.isStrongLine(it) }
            val weakLines = allLines.filter { !lineRules.isStrongLine(it) }

            if (strongLines.isNotEmpty()) {
                val strongDesserte = strongLines.joinToString(", ")
                strongLineStops.add(
                    StopFeature(
                        type = stop.type,
                        id = stop.id,
                        geometry = stop.geometry,
                        properties = StopProperties(
                            id = stop.properties.id,
                            nom = stop.properties.nom,
                            desserte = strongDesserte,
                            ascenseur = stop.properties.ascenseur,
                            escalator = stop.properties.escalator,
                            gid = stop.properties.gid,
                            lastUpdate = stop.properties.lastUpdate,
                            lastUpdateFme = stop.properties.lastUpdateFme,
                            adresse = stop.properties.adresse,
                            localiseFaceAAdresse = stop.properties.localiseFaceAAdresse,
                            commune = stop.properties.commune,
                            insee = stop.properties.insee,
                            zone = stop.properties.zone
                        )
                    )
                )
            }

            if (weakLines.isNotEmpty()) {
                val weakDesserte = weakLines.joinToString(", ")
                weakLineStops.add(
                    StopFeature(
                        type = stop.type,
                        id = "${stop.id}-weak",
                        geometry = stop.geometry,
                        properties = StopProperties(
                            id = stop.properties.id,
                            nom = stop.properties.nom,
                            desserte = weakDesserte,
                            ascenseur = stop.properties.ascenseur,
                            escalator = stop.properties.escalator,
                            gid = stop.properties.gid,
                            lastUpdate = stop.properties.lastUpdate,
                            lastUpdateFme = stop.properties.lastUpdateFme,
                            adresse = stop.properties.adresse,
                            localiseFaceAAdresse = stop.properties.localiseFaceAAdresse,
                            commune = stop.properties.commune,
                            insee = stop.properties.insee,
                            zone = stop.properties.zone
                        )
                    )
                )
            }
        }

        val strongStopsByName = strongLineStops.groupBy { normalizeStopName(it.properties.nom) }

        val mergedStrongStops = strongStopsByName.map { (_, stopsGroup) ->
            if (stopsGroup.size == 1) {
                stopsGroup.first()
            } else {
                val mergedDesserte = stopsGroup
                    .flatMap { linesForStop(it) }
                    .distinct()
                    .sorted()
                    .joinToString(", ")

                val firstStop = stopsGroup.first()
                val validCoordinates = stopsGroup
                    .mapNotNull { stop ->
                        val coordinates = stop.geometry.coordinates
                        if (coordinates.size < 2) null else coordinates[0] to coordinates[1]
                    }
                val avgLon = validCoordinates.map { it.first }.average()
                val avgLat = validCoordinates.map { it.second }.average()
                if (avgLon.isNaN() || avgLat.isNaN()) {
                    return@map firstStop
                }
                val mergedGeometry = StopGeometry(
                    type = "Point",
                    coordinates = listOf(avgLon, avgLat)
                )

                StopFeature(
                    type = firstStop.type,
                    id = firstStop.id,
                    geometry = mergedGeometry,
                    properties = StopProperties(
                        id = firstStop.properties.id,
                        nom = firstStop.properties.nom,
                        desserte = mergedDesserte,
                        ascenseur = firstStop.properties.ascenseur,
                        escalator = firstStop.properties.escalator,
                        gid = firstStop.properties.gid,
                        lastUpdate = firstStop.properties.lastUpdate,
                        lastUpdateFme = firstStop.properties.lastUpdateFme,
                        adresse = firstStop.properties.adresse,
                        localiseFaceAAdresse = firstStop.properties.localiseFaceAAdresse,
                        commune = firstStop.properties.commune,
                        insee = firstStop.properties.insee,
                        zone = firstStop.properties.zone
                    )
                )
            }
        }

        return mergedStrongStops + weakLineStops
    }

}
