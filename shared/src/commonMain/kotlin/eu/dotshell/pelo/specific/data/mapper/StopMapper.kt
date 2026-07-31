package eu.dotshell.pelo.specific.data.mapper

import eu.dotshell.pelo.generic.data.models.CRS
import eu.dotshell.pelo.generic.data.models.CRSProperties
import eu.dotshell.pelo.generic.data.models.geojson.StopCollection
import eu.dotshell.pelo.generic.data.models.geojson.StopFeature
import eu.dotshell.pelo.generic.data.models.stops.StopGeometry
import eu.dotshell.pelo.generic.data.models.stops.StopProperties
import eu.dotshell.pelo.specific.data.model.LyonStopCollection
import eu.dotshell.pelo.specific.data.model.LyonStopFeature

/**
 * Mapper to convert between Lyon-specific stop models and generic models.
 * Handles the case where WFS stop names are missing by enriching from Raptor/GTFS.
 */
object StopMapper {

    /**
     * Convert Lyon-specific stop properties to generic properties.
     * Uses fallback naming if WFS names are missing.
     */
    fun mapToGeneric(properties: eu.dotshell.pelo.specific.data.model.LyonStopProperties): StopProperties {
        val desserteRaw = properties.desserte
        val desserteArretRaw = properties.desserteArret

        // Ensure desserte is never empty by using a fallback value
        val desserteValue = (desserteRaw ?: desserteArretRaw).orEmpty()
        val finalDesserte = if (desserteValue.isNotBlank()) desserteValue else "UNKNOWN"

        // Try to get stop name from WFS first
        // Note: Gson may inject null into non-null Kotlin fields, so we use safe calls
        @Suppress("USELESS_ELVIS")
        var finalNom: String = properties.stopName ?: ""

        // If still empty, use a temporary placeholder
        // The real name will be enriched later from Raptor
        if (finalNom.isBlank()) {
            finalNom = "Arret ${properties.gid}"
        }

        return StopProperties(
            id = properties.gid,
            nom = finalNom,
            desserte = finalDesserte,
            ascenseur = false,
            escalator = false,
            gid = properties.gid,
            lastUpdate = null,
            lastUpdateFme = null,
            adresse = null,
            localiseFaceAAdresse = false,
            commune = properties.city,
            insee = properties.inseeCode,
            zone = null
        )
    }

    /**
     * Convert Lyon-specific stop feature to generic feature
     */
    fun mapToGeneric(feature: LyonStopFeature): StopFeature {
        return StopFeature(
            type = feature.type,
            id = feature.id,
            geometry = StopGeometry(
                type = feature.geometry.type,
                coordinates = feature.geometry.coordinates
            ),
            geometryName = feature.geometryName,
            properties = mapToGeneric(feature.properties),
            bbox = feature.bbox
        )
    }

    /**
     * Convert Lyon-specific stop collection to generic collection
     */
    fun mapToGeneric(collection: LyonStopCollection): StopCollection {
        return StopCollection(
            type = collection.type,
            features = collection.features.map { mapToGeneric(it) },
            totalFeatures = collection.totalFeatures,
            numberMatched = collection.numberMatched,
            numberReturned = collection.numberReturned,
            timeStamp = collection.timeStamp,
            crs = collection.crs?.let {
                CRS(
                    type = it.type,
                    properties = CRSProperties(
                        name = it.properties.name
                    )
                )
            },
            bbox = collection.bbox
        )
    }
}
