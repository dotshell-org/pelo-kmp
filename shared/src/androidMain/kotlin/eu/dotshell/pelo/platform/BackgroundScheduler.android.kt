package eu.dotshell.pelo.platform

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import eu.dotshell.pelo.generic.data.telemetry.TelemetryUploadWorker
import eu.dotshell.pelo.generic.worker.TrafficAlertsWorker
import java.time.Duration
import java.util.concurrent.TimeUnit

actual class BackgroundScheduler actual constructor(context: PlatformContext) {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    actual fun scheduleTelemetryUpload(delaySeconds: Long) {
        val request = TelemetryUploadWorker.buildRequest()
            .setInitialDelay(Duration.ofSeconds(delaySeconds.coerceAtLeast(0)))
            .build()
        // REPLACE so multiple close events coalesce into a single pending upload.
        workManager.enqueueUniqueWork(
            TelemetryUploadWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    actual fun cancelTelemetryUpload() {
        workManager.cancelUniqueWork(TelemetryUploadWorker.UNIQUE_WORK_NAME)
    }

    actual fun ensureTrafficAlertsScheduled() {
        // Periodic work (WorkManager minimum interval is 15 minutes).
        val request = PeriodicWorkRequestBuilder<TrafficAlertsWorker>(30, TimeUnit.MINUTES).build()
        workManager.enqueueUniquePeriodicWork(
            TRAFFIC_ALERTS_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        const val TRAFFIC_ALERTS_WORK_NAME = "traffic_alerts_periodic"
    }
}
