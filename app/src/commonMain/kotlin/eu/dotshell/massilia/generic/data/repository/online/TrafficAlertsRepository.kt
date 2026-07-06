package eu.dotshell.massilia.generic.data.repository.online

import eu.dotshell.massilia.platform.ioDispatcher

import eu.dotshell.massilia.generic.data.models.realtime.alerts.official.TrafficAlert
import eu.dotshell.massilia.generic.data.network.transport.TransportApi
import eu.dotshell.massilia.generic.data.repository.api.OfflineRepository
import eu.dotshell.massilia.generic.data.repository.api.TrafficAlertsRepository as ApiTrafficAlertsRepository
import eu.dotshell.massilia.generic.utils.network.withRetry
import eu.dotshell.massilia.platform.Log
import eu.dotshell.massilia.platform.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for managing traffic alerts data.
 * Multiplatform: removed android.content.Context, android.util.Log, SharedPreferences + Gson.
 * Uses [Settings] for simple timestamp caching and [OfflineRepository] for persistent storage.
 */
class TrafficAlertsRepository(
    private val transportApi: TransportApi,
    settings: Settings,
    private val offlineRepo: OfflineRepository? = null
) : ApiTrafficAlertsRepository {

    // Simple in-memory + Settings-based cache
    private val settingsRef = settings
    private val KEY_CACHE_TIMESTAMP = "traffic_alerts_cache_ts"
    private val CACHE_DURATION_MS = 15 * 60 * 1000L // 15 minutes

    /**
     * Fetches all traffic alerts, with simple cache + offline fallback.
     */
    override suspend fun getTrafficAlerts(): Result<List<TrafficAlert>> {
        return withContext(ioDispatcher) {
            try {
                val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                val cachedTs = settingsRef.getLong(KEY_CACHE_TIMESTAMP, 0L)
                val cacheExpired = now - cachedTs > CACHE_DURATION_MS

                if (!cacheExpired) {
                    // Try offline data as "cache" — it was refreshed recently
                    val cached = offlineRepo?.loadTrafficAlerts()
                    if (!cached.isNullOrEmpty()) {
                        return@withContext Result.success(cached)
                    }
                }

                Log.i(TAG, "Fetching traffic alerts from API...")
                val response = withRetry(maxRetries = 2, initialDelayMs = 500) {
                    transportApi.getTrafficAlerts()
                }

                if (response.success && response.alerts.isNotEmpty()) {
                    settingsRef.putLong(KEY_CACHE_TIMESTAMP, now)
                    try {
                        offlineRepo?.saveTrafficAlerts(response.alerts)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to save traffic alerts to offline storage: ${e.message}")
                    }
                    Result.success(response.alerts)
                } else {
                    val offline = offlineRepo?.loadTrafficAlerts()
                    if (!offline.isNullOrEmpty()) {
                        Result.success(offline)
                    } else {
                        Result.success(emptyList())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch traffic alerts: ${e.message}")
                val offline = offlineRepo?.loadTrafficAlerts()
                if (!offline.isNullOrEmpty()) {
                    Result.success(offline)
                } else {
                    Result.failure(e)
                }
            }
        }
    }

    companion object {
        private const val TAG = "TrafficAlertsRepository"
    }
}
