package eu.dotshell.pelo.generic.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
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
import eu.dotshell.pelo.MainActivity
import eu.dotshell.pelo.R
import eu.dotshell.pelo.generic.data.cache.TransportCacheImpl
import eu.dotshell.pelo.generic.data.config.AppConfigLoader
import eu.dotshell.pelo.generic.data.telemetry.TelemetryEmitter
import eu.dotshell.pelo.generic.data.telemetry.TripDetector
import eu.dotshell.pelo.generic.utils.location.GeoPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps location alive — and the traveller informed — for the duration of a navigation session.
 *
 * The ongoing notification is the only handle on the session once the app is backgrounded, so it
 * carries the live instruction and a Stop action rather than a fixed sentence.
 */
class NavigationModeForegroundService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tripDetector: TripDetector? = null
    private var tripDetectorInitJob: Job? = null
    private var notificationJob: Job? = null
    private var isFinalizing = false

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                shutDown()
                return START_NOT_STICKY
            }

            ACTION_START, null -> {
                // Android 14+ refuses to promote a location-typed service without the permission,
                // and Android 12+ refuses a background start outright. Both throw; neither should
                // take the app down, and neither may leave the "navigating" flag set behind.
                val started = runCatching {
                    startForeground(NOTIFICATION_ID, buildForegroundNotification(null))
                }.isSuccess

                if (!started || !hasLocationPermission()) {
                    shutDown()
                    return START_NOT_STICKY
                }

                // Only now is the session genuinely running. Setting the flag before this point
                // meant a failed start left it stuck on: the next launch believed navigation was
                // under way and pinned the screen awake with no session able to switch it off.
                NavigationModeStateStore.setNavigationActive(this, true)
                startTracking()
                observeInstruction()
                initializeTripDetector()
                return START_STICKY
            }

            else -> return START_STICKY
        }
    }

    override fun onDestroy() {
        stopTracking()
        notificationJob?.cancel()
        notificationJob = null
        NavigationModeStateStore.setNavigationActive(this, false)
        finalizeTripDetector()
        // Give the trip finalisation a moment to persist before tearing the scope down; it runs
        // in serviceScope, so it cannot cancel itself.
        CoroutineScope(Dispatchers.IO).launch {
            serviceScope.coroutineContext[Job]?.children?.forEach { it.join() }
            serviceScope.cancel()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun shutDown() {
        NavigationModeStateStore.setNavigationActive(this, false)
        finalizeTripDetector()
        stopTracking()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun startTracking() {
        if (locationCallback != null) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
            .setMinUpdateIntervalMillis(1_000L)
            .setWaitForAccurateLocation(false)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val fix = locationResult.lastLocation ?: return
                // Feed the trip detector — snap-and-drop happens internally, raw coordinates
                // are not persisted anywhere outside this callback's stack frame.
                tripDetector?.onLocationFix(fix.latitude, fix.longitude)
                // This is the session's location stream, foreground or not; the guidance used to
                // rely on the UI's, which stops being delivered once the app is backgrounded.
                NavigationLocationBus.publish(GeoPoint(fix.latitude, fix.longitude))
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback!!,
                mainLooper
            )
        } catch (_: SecurityException) {
            shutDown()
        }
    }

    private fun stopTracking() {
        val callback = locationCallback ?: return
        fusedLocationClient.removeLocationUpdates(callback)
        locationCallback = null
    }

    /** Mirror the on-screen instruction into the ongoing notification. */
    private fun observeInstruction() {
        if (notificationJob != null) return
        notificationJob = serviceScope.launch {
            NavigationNotificationBridge.instruction.collectLatest { text ->
                val manager = getSystemService(NotificationManager::class.java)
                manager?.notify(NOTIFICATION_ID, buildForegroundNotification(text))
            }
        }
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
            tripDetectorInitJob = null
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

        tripDetectorInitJob?.cancel()
        tripDetectorInitJob = null
        // Taken *after* the early return for "no detector": setting it first meant that path
        // latched the guard on for good and every later call became a no-op.
        val detector = tripDetector ?: return
        tripDetector = null
        isFinalizing = true

        serviceScope.launch {
            try {
                detector.stop().join()
                detector.dispose()
            } finally {
                isFinalizing = false
            }
        }
    }

    private fun buildForegroundNotification(instruction: String?): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, NavigationModeForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.navigation_mode_notification_title))
            .setContentText(instruction ?: getString(R.string.navigation_mode_notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            // The way out of navigation when the app is not on screen.
            .addAction(
                0,
                getString(R.string.navigation_mode_notification_stop),
                stopPendingIntent
            )
            .build()
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
    }

    companion object {
        const val ACTION_START = "eu.dotshell.pelo.action.navigation.START"
        const val ACTION_STOP = "eu.dotshell.pelo.action.navigation.STOP"

        private const val CHANNEL_ID = "navigation_mode_channel"
        private const val NOTIFICATION_ID = 7411
    }
}
