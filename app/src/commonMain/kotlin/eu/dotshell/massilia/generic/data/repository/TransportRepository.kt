package eu.dotshell.massilia.generic.data.repository

import eu.dotshell.massilia.platform.ioDispatcher

import eu.dotshell.massilia.generic.data.models.geojson.Feature
import eu.dotshell.massilia.generic.data.models.geojson.FeatureCollection
import eu.dotshell.massilia.generic.data.network.transport.TransportApi
import eu.dotshell.massilia.generic.data.network.transport.TransportLinesQuery
import eu.dotshell.massilia.generic.data.repository.api.TransportRepository as ApiTransportRepository
import eu.dotshell.massilia.generic.utils.network.withRetry
import eu.dotshell.massilia.platform.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for managing transport line data.
 * Multiplatform: removed android.content.Context, Gson, android.util.Log dependencies.
 */
class TransportRepository(
    private val transportApi: TransportApi,
    private val offlineRepo: eu.dotshell.massilia.generic.data.repository.api.OfflineRepository? = null
) : ApiTransportRepository {

    /**
     * Fetches all default (non-bus-by-pagination) strong transport line geometries.
     */
    override suspend fun getAllLines(): Result<FeatureCollection> {
        return withContext(ioDispatcher) {
            runCatching {
                withRetry(maxRetries = 2, initialDelayMs = 1000) {
                    transportApi.getLines(TransportLinesQuery.StrongLines)
                }
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { e ->
                    // Try offline repository first
                    val offlineLines = offlineRepo?.loadAllLines().orEmpty()
                    if (offlineLines.isNotEmpty()) {
                        val uniqueLines = offlineLines
                            .groupBy { it.properties.traceCode }
                            .map { (_, features) -> features.first() }
                        Result.success(
                            FeatureCollection(
                                type = "FeatureCollection",
                                features = uniqueLines
                            )
                        )
                    } else {
                        Log.w(TAG, "getAllLines failed and no offline data: ${e.message}")
                        Result.failure(e)
                    }
                }
            )
        }
    }

    /**
     * Loads a single line geometry by line name, including non-strong lines.
     */
    override suspend fun getLineByName(lineName: String): Result<List<Feature>> {
        return withContext(ioDispatcher) {
            runCatching {
                transportApi
                    .getLines(TransportLinesQuery.LineByName(lineName))
                    .features
            }
        }
    }

    companion object {
        private const val TAG = "TransportRepository"
    }
}
