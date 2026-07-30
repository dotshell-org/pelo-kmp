package eu.dotshell.pelo.generic.data.telemetry

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters

/**
 * Thin WorkManager bridge for telemetry uploads. All payload-building, gzip, and network logic
 * lives in the shared [TelemetryUploader]; this worker only maps its [TelemetryUploader.Outcome]
 * to a WorkManager [Result] (so the OS applies exponential backoff on retry).
 *
 * Scheduling:
 *  - Enqueued from [eu.dotshell.pelo.platform.BackgroundScheduler] on session-close debounce
 *    expiry under [UNIQUE_WORK_NAME] so multiple close events coalesce into a single attempt.
 *  - Constraint: [NetworkType.CONNECTED]. If offline, the OS runs us when connectivity returns,
 *    even if the app is killed.
 */
class TelemetryUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = when (TelemetryUploader.uploadOnce(runAttemptCount)) {
        TelemetryUploader.Outcome.SUCCESS -> Result.success()
        TelemetryUploader.Outcome.RETRY -> Result.retry()
        TelemetryUploader.Outcome.GIVE_UP -> Result.failure()
    }

    companion object {
        val UNIQUE_WORK_NAME = "telemetry_upload"

        fun buildRequest(): OneTimeWorkRequest.Builder {
            return OneTimeWorkRequestBuilder<TelemetryUploadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
        }
    }
}
