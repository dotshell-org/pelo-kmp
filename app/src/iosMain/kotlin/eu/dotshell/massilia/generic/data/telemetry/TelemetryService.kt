package eu.dotshell.massilia.generic.data.telemetry

import eu.dotshell.massilia.IosPlatformContext
import eu.dotshell.massilia.generic.data.config.TelemetryConfigData
import eu.dotshell.massilia.generic.data.repository.online.TrafficAlertsRepository
import eu.dotshell.massilia.generic.service.TransportServiceProvider
import eu.dotshell.massilia.platform.BackgroundScheduler
import eu.dotshell.massilia.platform.Log
import eu.dotshell.massilia.platform.PlatformContext
import eu.dotshell.massilia.platform.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import kotlin.concurrent.Volatile

object TelemetryService {

    private const val TAG = "TelemetryService"

    @Volatile
    private var initialized = false

    private lateinit var controller: TelemetrySessionController

    fun initialize(context: PlatformContext, config: TelemetryConfigData) {
        if (initialized) return
        if (!config.enabled) {
            Log.i(TAG, "Telemetry disabled in config — service inactive")
            return
        }

        TelemetryEmitter.initialize(IosPlatformContext, config)
        controller = TelemetrySessionController(
            scheduler = BackgroundScheduler(IosPlatformContext),
            debounceSeconds = config.closeDebounceSeconds
        )

        // Observe iOS App Lifecycle Notifications
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationWillEnterForegroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
            usingBlock = { _ ->
                Log.i(TAG, "App entered foreground, activating telemetry session")
                controller.onForeground()
            }
        )

        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
            usingBlock = { _ ->
                Log.i(TAG, "App entered background, closing telemetry session")
                controller.onBackground()
            }
        )

        // Register Background Task for Telemetry Upload
        BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(
            "eu.dotshell.massilia.telemetryUpload",
            usingQueue = null
        ) { task ->
            if (task == null) return@registerForTaskWithIdentifier
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            task.expirationHandler = {
                Log.i(TAG, "Telemetry upload background task expired")
            }
            scope.launch {
                Log.i(TAG, "Starting background telemetry upload...")
                val outcome = TelemetryUploader.uploadOnce(0)
                task.setTaskCompletedWithSuccess(outcome == TelemetryUploader.Outcome.SUCCESS)
                Log.i(TAG, "Background telemetry upload completed with outcome: $outcome")
            }
        }

        // Register Background Task for Traffic Alerts
        BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(
            "eu.dotshell.massilia.trafficAlerts",
            usingQueue = null
        ) { task ->
            if (task == null) return@registerForTaskWithIdentifier
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            task.expirationHandler = {
                Log.i(TAG, "Traffic alerts background task expired")
            }
            scope.launch {
                try {
                    Log.i(TAG, "Starting background traffic alerts check...")
                    val trafficAlertsRepository = TrafficAlertsRepository(
                        TransportServiceProvider.getTransportApi(),
                        Settings(IosPlatformContext, "traffic_alerts_cache"),
                        eu.dotshell.massilia.generic.data.offline.OfflineRepository(IosPlatformContext)
                    )
                    val result = trafficAlertsRepository.getTrafficAlerts()
                    task.setTaskCompletedWithSuccess(result.isSuccess)
                    Log.i(TAG, "Background traffic alerts check completed with success: ${result.isSuccess}")
                } catch (e: Exception) {
                    Log.w(TAG, "Background traffic alerts check threw error: ${e.message}")
                    task.setTaskCompletedWithSuccess(false)
                }
            }
        }

        // Since the app is starting in foreground, immediately activate session
        controller.onForeground()

        initialized = true
        Log.i(TAG, "TelemetryService initialized on iOS")
    }
}
