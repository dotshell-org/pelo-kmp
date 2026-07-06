package eu.dotshell.massilia.generic.service

import android.Manifest
import android.content.BroadcastReceiver
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import eu.dotshell.massilia.MainActivity
import eu.dotshell.massilia.R
import eu.dotshell.massilia.generic.data.cache.TransportCacheImpl
import eu.dotshell.massilia.generic.data.config.AppConfigLoader
import eu.dotshell.massilia.generic.data.telemetry.TelemetryEmitter
import eu.dotshell.massilia.generic.data.telemetry.TripDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NavigationModeForegroundService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var isScreenReceiverRegistered = false

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tripDetector: TripDetector? = null
    private var tripDetectorInitJob: Job? = null
    private var isFinalizing = false

    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_ON &&
                NavigationModeStateStore.isNavigationActive(this@NavigationModeForegroundService)
            ) {
                showWakeupFullScreenNotification()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        registerScreenReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                NavigationModeStateStore.setNavigationActive(this, false)
                finalizeTripDetector()
                stopTracking()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                NavigationModeStateStore.setNavigationActive(this, true)
                startForeground(NOTIFICATION_ID, buildForegroundNotification())
                startTracking()
                initializeTripDetector()
                return START_STICKY
            }
            else -> return START_STICKY
        }
    }

    override fun onDestroy() {
        stopTracking()
        unregisterScreenReceiver()
        
        // Finalize trip detector - this launches a job in serviceScope
        finalizeTripDetector()
        
        // Cancel serviceScope after a reasonable delay to allow finalization to complete
        // This prevents memory leaks from long-running services
        if (!NavigationModeStateStore.isNavigationActive(this)) {
            NavigationModeStateStore.setNavigationActive(this, false)
            // Create a separate scope for cleanup to avoid circular dependency
            // (can't cancel serviceScope from within serviceScope itself)
            CoroutineScope(Dispatchers.IO).launch {
                delay(5000) // 5 seconds grace period
                serviceScope.cancel()
            }
        }
        
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startTracking() {
        val hasFinePermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarsePermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFinePermission && !hasCoarsePermission) {
            stopSelf()
            return
        }

        if (locationCallback != null) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(2000L)
            .setWaitForAccurateLocation(false)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val fix = locationResult.lastLocation ?: return
                // Feed the trip detector — snap-and-drop happens internally, raw coordinates
                // are not persisted anywhere outside this callback's stack frame.
                tripDetector?.onLocationFix(fix.latitude, fix.longitude)
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback!!,
                mainLooper
            )
        } catch (_: SecurityException) {
            stopSelf()
        }
    }

    private fun stopTracking() {
        val callback = locationCallback ?: return
        fusedLocationClient.removeLocationUpdates(callback)
        locationCallback = null
    }

    /**
     * Build a [TripDetector] once the GTFS stop catalogue has been loaded from the cache. Done
     * off the main thread because [TransportCacheImpl] does disk IO. If the user has not opted
     * in to telemetry, or the stops cache is empty (cold start before first fetch), we skip
     * detector creation — navigation still works fine, just without trip telemetry.
     */
    private fun initializeTripDetector() {
        if (tripDetector != null || tripDetectorInitJob != null) return
        if (TelemetryEmitter.optInManager()?.isOptedIn != true) return

        tripDetectorInitJob = serviceScope.launch {
            val cache = TransportCacheImpl(applicationContext)
            val stops = runCatching { cache.getStops() }.getOrNull().orEmpty()
            if (stops.isEmpty()) return@launch

            val telemetryConfig = runCatching { AppConfigLoader.getConfig().telemetry }.getOrNull()
            val detector = TripDetector(
                stops = stops,
                snapRadiusMeters = telemetryConfig?.tripSnapRadiusMeters ?: 100,
                samplingIntervalMs = (telemetryConfig?.tripSamplingSeconds ?: 30L) * 1000L
            )
            detector.start()
            tripDetector = detector
        }
    }

    /**
     * Stop and dispose the [TripDetector]. Idempotent — safe to call from both ACTION_STOP
     * and onDestroy().
     *
     * We join the stop() job in our own [serviceScope] before disposing the detector so that
     * the trip.completed emission and local persistence have time to complete.
     */
    private fun finalizeTripDetector() {
        if (isFinalizing) return
        isFinalizing = true
        
        tripDetectorInitJob?.cancel()
        tripDetectorInitJob = null
        val detector = tripDetector ?: return
        tripDetector = null
        serviceScope.launch {
            try {
                detector.stop().join()
                detector.dispose()
            } finally {
                isFinalizing = false
            }
        }
    }

    private fun buildForegroundNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.navigation_mode_notification_title))
            .setContentText(getString(R.string.navigation_mode_notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showWakeupFullScreenNotification() {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_FROM_NAVIGATION_WAKEUP, true)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            1,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, WAKE_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.navigation_mode_wakeup_title))
            .setContentText(getString(R.string.navigation_mode_wakeup_text))
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(WAKE_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.navigation_mode_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.navigation_mode_notification_channel_description)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)

        val wakeChannel = NotificationChannel(
            WAKE_CHANNEL_ID,
            getString(R.string.navigation_mode_wakeup_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.navigation_mode_wakeup_channel_description)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        manager.createNotificationChannel(wakeChannel)
    }

    private fun registerScreenReceiver() {
        if (isScreenReceiverRegistered) return
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        registerReceiver(screenOnReceiver, filter)
        isScreenReceiverRegistered = true
    }

    private fun unregisterScreenReceiver() {
        if (!isScreenReceiverRegistered) return
        unregisterReceiver(screenOnReceiver)
        isScreenReceiverRegistered = false
    }

    companion object {
        const val ACTION_START = "eu.dotshell.massilia.action.navigation.START"
        const val ACTION_STOP = "eu.dotshell.massilia.action.navigation.STOP"
        const val EXTRA_FROM_NAVIGATION_WAKEUP = "extra_from_navigation_wakeup"

        private const val CHANNEL_ID = "navigation_mode_channel"
        private const val WAKE_CHANNEL_ID = "navigation_mode_wakeup_channel"
        private const val NOTIFICATION_ID = 7411
        private const val WAKE_NOTIFICATION_ID = 7412
    }
}
