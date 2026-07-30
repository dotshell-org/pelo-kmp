package eu.dotshell.pelo.generic.data.repository.api

import eu.dotshell.pelo.generic.data.models.geojson.FeatureCollection
import eu.dotshell.pelo.generic.data.models.geojson.Feature

interface TransportRepository {
    suspend fun getAllLines(): Result<FeatureCollection>
    suspend fun getLineByName(lineName: String): Result<List<Feature>>
}
