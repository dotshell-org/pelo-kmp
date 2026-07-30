package eu.dotshell.pelo.generic.data.repository

import eu.dotshell.pelo.platform.ioDispatcher

import eu.dotshell.pelo.generic.data.models.realtime.alerts.community.StopAlertsStatus
import eu.dotshell.pelo.generic.data.models.realtime.alerts.community.UserStopAlert
import eu.dotshell.pelo.generic.data.models.realtime.alerts.community.UserStopAlertsResponse
import eu.dotshell.pelo.generic.data.network.UserStopAlertsApi
import eu.dotshell.pelo.generic.utils.search.SearchUtils
import eu.dotshell.pelo.platform.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for managing user stop alerts (karma-based alerts).
 * Multiplatform: removed android.util.Log dependency; now uses platform.Log.
 * When the transport client has no [UserStopAlertsApi] backend (null), every
 * query resolves to "no alerts".
 */
class UserStopAlertsRepository(
    private val api: UserStopAlertsApi?
) {
    companion object {
        private const val TAG = "UserStopAlertsRepository"
        private const val API_STOPS_CHUNK_SIZE = 10
    }

    private fun hasKarmaAtOrAboveThreshold(status: StopAlertsStatus): Boolean {
        return status.karmaAtOrAboveThreshold.isNotEmpty()
    }

    /**
     * Fetch alerts for the given stops.
     */
    suspend fun getUserStopAlerts(stopIds: List<String>): UserStopAlertsResponse =
        withContext(ioDispatcher) {
            if (api == null || stopIds.isEmpty()) return@withContext emptyMap()

            val requestedStops = stopIds.distinct()
            Log.i(TAG, "Fetching user stop alerts for ${requestedStops.size} stops")

            val merged = linkedMapOf<String, StopAlertsStatus>()

            requestedStops.chunked(API_STOPS_CHUNK_SIZE).forEach { chunk ->
                try {
                    val response = api.getUserStopAlerts(chunk)
                    merged.putAll(response)
                } catch (chunkError: Exception) {
                    Log.w(TAG, "Chunk request failed, retrying one by one: ${chunkError.message}")
                    chunk.forEach { stopId ->
                        try {
                            val singleResponse = api.getUserStopAlerts(listOf(stopId))
                            merged.putAll(singleResponse)
                        } catch (singleError: Exception) {
                            Log.w(TAG, "Single stop alert request failed for '$stopId': ${singleError.message}")
                        }
                    }
                }
            }

            merged
        }

    /**
     * Return problematic alerts mapped to caller stop names.
     * Prevents false positives when names differ by accents/casing/punctuation.
     */
    suspend fun getProblematicAlertDetails(stopIds: List<String>): Map<String, List<UserStopAlert>> =
        withContext(Dispatchers.Default) {
            val alerts = getUserStopAlerts(stopIds)
            if (alerts.isEmpty()) return@withContext emptyMap()

            val problematicEntries = alerts.filter { (_, status) -> hasKarmaAtOrAboveThreshold(status) }

            Log.d(TAG, "Problematic API stops: ${problematicEntries.size}/${alerts.size}")

            if (problematicEntries.isEmpty()) return@withContext emptyMap()

            val normalizedToApiStops = problematicEntries.keys
                .groupBy(SearchUtils::normalizeStopKey)

            val problematicByExact = problematicEntries
                .mapValues { (_, status) -> status.karmaAtOrAboveThreshold }

            val result = linkedMapOf<String, List<UserStopAlert>>()
            stopIds.forEach { callerStopName ->
                val exact = problematicByExact[callerStopName]
                if (!exact.isNullOrEmpty()) {
                    result[callerStopName] = exact
                    return@forEach
                }
                val normalized = SearchUtils.normalizeStopKey(callerStopName)
                val candidates = normalizedToApiStops[normalized].orEmpty()
                if (candidates.size == 1) {
                    val fallback = problematicByExact[candidates.first()]
                    if (!fallback.isNullOrEmpty()) {
                        result[callerStopName] = fallback
                    }
                }
            }

            Log.d(TAG, "Matched problematic caller stops: ${result.keys.joinToString()}")
            result
        }
}
