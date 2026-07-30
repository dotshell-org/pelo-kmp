package eu.dotshell.pelo.specific.data.mapper

import eu.dotshell.pelo.generic.data.models.CRS
import eu.dotshell.pelo.generic.data.models.CRSProperties
import eu.dotshell.pelo.generic.data.models.geojson.Feature
import eu.dotshell.pelo.generic.data.models.geojson.FeatureCollection
import eu.dotshell.pelo.generic.data.models.lines.MultiLineStringGeometry
import eu.dotshell.pelo.generic.data.models.lines.TransportLineProperties
import eu.dotshell.pelo.specific.data.model.LyonFeature
import eu.dotshell.pelo.specific.data.model.LyonFeatureCollection
import eu.dotshell.pelo.specific.data.model.LyonTransportLineProperties

/**
 * Mapper to convert between Lyon-specific transport line models and generic models
 */
object TransportLineMapper {

    /**
     * Convert Lyon-specific transport line properties to generic properties
     */
    fun mapToGeneric(properties: LyonTransportLineProperties): TransportLineProperties {
        return TransportLineProperties(
            lineName = properties.ligne,
            traceCode = properties.codeTrace,
            lineId = properties.codeLigne,
            traceType = properties.typeTrace ?: "",
            traceName = properties.nomTrace ?: "",
            direction = properties.sens,
            origin = properties.origine ?: "",
            destination = properties.destination ?: "",
            originName = properties.nomOrigine ?: "",
            destinationName = properties.nomDestination ?: "",
            transportType = properties.familleTransport,
            startDate = properties.dateDebut ?: "",
            endDate = properties.dateFin,
            lineTypeCode = properties.codeTypeLigne ?: "",
            lineTypeName = properties.nomTypeLigne ?: "",
            sortCode = properties.codeTriLigne ?: "",
            versionName = properties.nomVersion ?: "",
            lastUpdate = properties.lastUpdate ?: "",
            lastUpdateFme = properties.lastUpdateFme ?: "",
            gid = properties.gid,
            color = properties.couleur
        )
    }

    /**
     * Convert Lyon-specific feature to generic feature
     */
    fun mapToGeneric(feature: LyonFeature): Feature {
        return Feature(
            type = feature.type,
            id = feature.id,
            multiLineStringGeometry = MultiLineStringGeometry(
                type = feature.geometry.type,
                coordinates = feature.geometry.coordinates
            ),
            geometryName = feature.geometryName,
            properties = mapToGeneric(feature.properties),
            bbox = feature.bbox
        )
    }

    /**
     * Convert Lyon-specific feature collection to generic feature collection
     */
    fun mapToGeneric(collection: LyonFeatureCollection): FeatureCollection {
        return FeatureCollection(
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
