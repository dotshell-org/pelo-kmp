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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
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
import kotlinx.coroutines.withTimeoutOrNull

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

    // Outlives serviceScope by design, purely to close it down: the finalisation it waits on runs
    // inside serviceScope, which therefore cannot cancel itself. Held in a field, and bounded by a
    // timeout, because the anonymous CoroutineScope this replaces was unreachable the moment it
    // was created — if a child never completed, that join outlived the service with no handle.
    private val teardownScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
                observeGlanceState()
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
        // Give the trip finalisation a moment to persist before tearing the scope down.
        teardownScope.launch {
            withTimeoutOrNull(TEARDOWN_GRACE_MS) {
                serviceScope.coroutineContext[Job]?.children?.forEach { it.join() }
            }
            serviceScope.cancel()
            teardownScope.cancel()
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

    /** Mirror the live session into the ongoing notification. */
    private fun observeGlanceState() {
        if (notificationJob != null) return
        notificationJob = serviceScope.launch {
            var diagnosed = false
            NavigationGlanceBridge.state.collectLatest { state ->
                val notification = buildForegroundNotification(state)
                getSystemService(NotificationManager::class.java)
                    ?.notify(NOTIFICATION_ID, notification)
                if (state != null && !diagnosed) {
                    diagnosed = true
                    logPromotionDiagnostics(notification)
                }
            }
        }
    }

    /**
     * Whether this notification is one the system would promote, and whether we are allowed to ask.
     *
     * Worth a log line because the two failure modes are indistinguishable from the outside: an
     * OEM whose bubble does not yet read the standard Android 16 signal looks exactly like a
     * notification we built wrong.
     */
    private fun logPromotionDiagnostics(notification: Notification) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return
        val manager = getSystemService(NotificationManager::class.java)
        Log.i(
            TAG,
            "Live Update: promotable=${NotificationCompat.hasPromotableCharacteristics(notification)}" +
                ", allowed=${manager?.canPostPromotedNotifications()}"
        )
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
            val cache = TransportCacheImpl.getInstance(applicationContext)
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

    private fun buildForegroundNotification(state: NavigationLiveActivityState?): Notification {
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

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            // A monochrome glyph: the system masks a small icon to a flat silhouette, so the
            // launcher icon this used to pass came out as a white blob — and it is this icon that
            // shows in the status bar chip once the notification is promoted.
            .setSmallIcon(R.drawable.ic_nav_notification)
            // The instruction, not a fixed sentence. A promoted notification is required to carry
            // a title, and this is the one line worth reading at a glance.
            .setContentTitle(
                state?.instruction ?: getString(R.string.navigation_mode_notification_title)
            )
            .setContentText(subtitleFor(state))
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

        // Android 16 Live Update. Guarded rather than left to the compat layer: ProgressStyle only
        // has an implementation from API 36, and a template that renders as nothing would be worse
        // than the plain notification it replaces.
        if (state != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            builder
                .setRequestPromotedOngoing(true)
                .setShortCriticalText(chipTextFor(state))
            progressStyleFor(state)?.let(builder::setStyle)
        }

        return builder.build()
    }

    private fun subtitleFor(state: NavigationLiveActivityState?): String =
        if (state == null || state.destination.isBlank()) {
            getString(R.string.navigation_mode_notification_text)
        } else {
            getString(
                R.string.navigation_mode_notification_destination,
                state.destination,
                state.arrivalTimeText,
            )
        }

    /** The status bar chip: a few characters wide, so the countdown and nothing else. */
    private fun chipTextFor(state: NavigationLiveActivityState): String =
        if (state.isArrived) {
            getString(R.string.navigation_mode_chip_arrived)
        } else {
            getString(R.string.navigation_mode_chip_minutes, state.remainingMinutes)
        }

    /**
     * The journey drawn as a bar: one segment per leg in its line's own colour, a mark where the
     * traveller changes, and a tracker where they are now.
     */
    private fun progressStyleFor(
        state: NavigationLiveActivityState,
    ): NotificationCompat.ProgressStyle? {
        // Zero-length segments are dropped rather than padded to a second: they contribute nothing
        // to the sum, so progress stays on exactly the scale it was computed against.
        val segments = state.segments.filter { it.seconds > 0 }
        if (segments.isEmpty() || state.totalSeconds <= 0) return null

        val trackerColor = ContextCompat.getColor(this, R.color.nav_progress_tracker)
        val style = NotificationCompat.ProgressStyle()
            // Each leg keeps its line's colour instead of being repainted by how far along the
            // traveller is. Showing the itinerary is the whole point of the bar.
            .setStyledByProgress(false)
            .setProgress(state.progressSeconds)
            .setProgressTrackerIcon(
                IconCompat.createWithResource(this, R.drawable.ic_nav_notification)
                    .setTint(trackerColor)
            )
            .setProgressEndIcon(
                IconCompat.createWithResource(this, R.drawable.ic_nav_destination)
                    .setTint(trackerColor)
            )

        segments.forEach { segment ->
            style.addProgressSegment(
                NotificationCompat.ProgressStyle.Segment(segment.seconds)
                    .setColor(colorFor(segment))
            )
        }

        val transferColor = ContextCompat.getColor(this, R.color.nav_transfer_point)
        state.transferOffsetsSeconds.forEach { offset ->
            style.addProgressPoint(
                NotificationCompat.ProgressStyle.Point(offset).setColor(transferColor)
            )
        }
        return style
    }

    private fun colorFor(segment: NavigationRouteSegment): Int = when (segment.kind) {
        NavigationSegmentKind.WALK -> ContextCompat.getColor(this, R.color.nav_segment_walk)
        NavigationSegmentKind.WAIT -> ContextCompat.getColor(this, R.color.nav_segment_wait)
        NavigationSegmentKind.RIDE -> segment.colorArgb
            ?: ContextCompat.getColor(this, R.color.nav_segment_ride_fallback)
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

        private const val TAG = "NavigationService"
        private const val CHANNEL_ID = "navigation_mode_channel"
        private const val NOTIFICATION_ID = 7411

        /** How long teardown waits for trip finalisation to persist before cancelling anyway. */
        private const val TEARDOWN_GRACE_MS = 5_000L
    }
}
