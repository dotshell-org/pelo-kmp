package eu.dotshell.massilia.generic.worker

import android.content.Context
import eu.dotshell.massilia.platform.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import eu.dotshell.massilia.generic.data.models.realtime.alerts.official.TrafficAlert
import eu.dotshell.massilia.generic.service.TransportServiceProvider
import eu.dotshell.massilia.generic.data.repository.online.TrafficAlertsRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class TrafficAlertsWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "TrafficAlertsWorker"

    }

    private val trafficAlertsRepository = TrafficAlertsRepository(
        TransportServiceProvider.getTransportApi(),
        eu.dotshell.massilia.platform.Settings(applicationContext, "traffic_alerts_cache"),
        eu.dotshell.massilia.generic.data.offline.OfflineRepository(applicationContext)
    )

    override suspend fun doWork(): Result {
        return try {
            Log.i(TAG, "Starting traffic alerts check...")
            val alertsResult = trafficAlertsRepository.getTrafficAlerts()
            if (alertsResult.isFailure) {
                Log.e(TAG, "Failed to fetch alerts: ${alertsResult.exceptionOrNull()?.message}")
                return Result.retry()
            }
            val allAlerts = alertsResult.getOrThrow()
            val validAlerts = filterValidAlerts(allAlerts)
            Log.i(TAG, "Fetched ${validAlerts.size} valid traffic alerts")
            // No notification logic, just fetch and filter
            Log.i(TAG, "Traffic alerts check completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking traffic alerts", e)
            Result.retry()
        }
    }

    private fun filterValidAlerts(alerts: List<TrafficAlert>): List<TrafficAlert> {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

        return alerts.filter { alert ->
            try {
                val endDate = LocalDateTime.parse(alert.endDate.replace(" ", "T"))
                endDate > now
            } catch (e: Exception) {
                true // Keep if we can't parse
            }
        }
    }

}
