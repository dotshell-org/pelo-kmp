@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package eu.dotshell.massilia.platform

import platform.BackgroundTasks.*
import platform.Foundation.*
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskIdentifier
import platform.UIKit.UIBackgroundTaskInvalid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import eu.dotshell.massilia.generic.data.telemetry.TelemetryUploader

/**
 * iOS implementation using BGTaskScheduler.
 */
actual class BackgroundScheduler actual constructor(context: PlatformContext) {

    actual fun scheduleTelemetryUpload(delaySeconds: Long) {
        val request = BGAppRefreshTaskRequest("eu.dotshell.massilia.telemetryUpload")
        request.earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(delaySeconds.toDouble())
        
        val success = BGTaskScheduler.sharedScheduler.submitTaskRequest(request, null)
        if (success) {
            Log.i(TAG, "scheduleTelemetryUpload($delaySeconds) submitted")
        } else {
            Log.e(TAG, "Failed to submit telemetry upload task")
        }

        // Trigger immediate background task assertion to upload before the OS suspends the app
        val sharedApp = UIApplication.sharedApplication
        var bgTaskId = UIBackgroundTaskInvalid
        bgTaskId = sharedApp.beginBackgroundTaskWithExpirationHandler {
            sharedApp.endBackgroundTask(bgTaskId)
            bgTaskId = UIBackgroundTaskInvalid
        }

        if (bgTaskId != UIBackgroundTaskInvalid) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            scope.launch {
                try {
                    delay(delaySeconds * 1000L)
                    Log.i(TAG, "Executing immediate background upload task assertion...")
                    val outcome = TelemetryUploader.uploadOnce(0)
                    Log.i(TAG, "Immediate background upload completed with outcome: $outcome")
                } catch (e: Exception) {
                    Log.w(TAG, "Immediate background upload threw: ${e.message}")
                } finally {
                    sharedApp.endBackgroundTask(bgTaskId)
                }
            }
        }
    }

    actual fun cancelTelemetryUpload() {
        BGTaskScheduler.sharedScheduler.cancelTaskRequestWithIdentifier("eu.dotshell.massilia.telemetryUpload")
        Log.i(TAG, "cancelTelemetryUpload() called")
    }

    actual fun ensureTrafficAlertsScheduled() {
        val request = BGAppRefreshTaskRequest("eu.dotshell.massilia.trafficAlerts")
        
        val success = BGTaskScheduler.sharedScheduler.submitTaskRequest(request, null)
        if (success) {
            Log.i(TAG, "ensureTrafficAlertsScheduled() submitted")
        } else {
            Log.e(TAG, "Failed to submit traffic alerts task")
        }
    }

    private companion object {
        const val TAG = "BackgroundScheduler"
    }
}
