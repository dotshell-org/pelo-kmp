package eu.dotshell.pelo

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.content.ContextCompat
import eu.dotshell.pelo.generic.data.cache.TransportCacheImpl
import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.RaptorRepository
import eu.dotshell.pelo.generic.data.repository.offline.SchedulesRepository
import eu.dotshell.pelo.generic.service.NavigationModeForegroundService
import eu.dotshell.pelo.generic.service.NavigationModeStateStore
import eu.dotshell.pelo.generic.utils.location.LocationPermissionSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Application-level coroutine scope for early background work
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var isNavigationModeEnabled = false

    // Splash hand-off state. See holdSplashUntilReady().
    private var isUiReady = false
    private var contentView: View? = null

    // Held as one instance: a `::releaseSplash` reference is SAM-converted at each use site, and
    // removeCallbacks() matches on the Runnable itself, so posting and removing separate
    // conversions could leave the timeout queued against a destroyed Activity.
    private val releaseSplashRunnable = Runnable { releaseSplash() }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The persisted flag outlives the process; the in-memory session does not. A flag left
        // set by a crash or a kill would otherwise pin the screen awake and show the app over the
        // lock screen forever, with no session left that could turn either back off — so treat a
        // fresh process as "not navigating" and clear the platform state to match.
        if (NavigationModeStateStore.isNavigationActive(this)) {
            NavigationModeStateStore.setNavigationActive(this, false)
            stopNavigationForegroundService()
        }
        setNavigationLockScreenBehavior(false)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.dark(
                android.graphics.Color.TRANSPARENT
            )
        )

        setContent {
            CompositionLocalProvider(eu.dotshell.pelo.platform.LocalPlatformContext provides this@MainActivity) {
                App(
                    onReady = ::releaseSplash,
                    onNavigationModeChanged = { active ->
                        if (active != isNavigationModeEnabled) {
                            isNavigationModeEnabled = active
                            if (active) {
                                startNavigationForegroundService()
                            } else {
                                stopNavigationForegroundService()
                            }
                            setNavigationLockScreenBehavior(active)
                        }
                    },
                    // Ask for location only once the user has accepted the terms/privacy policy
                    // (fires immediately on launch for a user who already accepted).
                    onConsentAccepted = { requestLocationPermissionsIfNeeded() }
                )
            }
        }

        holdSplashUntilReady()

        // Start all background preloading AFTER setContent to ensure UI displays immediately
        // This is critical for fast first render - do NOT block on these operations
        appScope.launch {
            try {
                // TransportServiceProvider + RetrofitInstance are initialized in PeloApplication.onCreate
                // so background workers and repositories can run before the first activity starts.

                // Parallel preloading - fire and forget (do NOT join)
                // Warms the process-wide cache the view model will go on to use. It used to build
                // a throwaway instance, so this decompression happened twice.
                val cacheJob = launch {
                    TransportCacheImpl.getInstance(applicationContext).preloadFromDisk()
                }
                
                // Preload Raptor library in background (only needed for itinerary calculations)
                // Note: SchedulesRepository.warmupDatabase() is a no-op, so we skip it
                launch {
                    val raptorRepo = RaptorRepository.getInstance(applicationContext)
                    raptorRepo.initialize()
                    raptorRepo.preloadJourneyCache()
                }
                
                // Don't wait for any of these - let them complete in background
                // cacheJob.join() - REMOVED to avoid blocking
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Background preload failed: ${e.message}")
            }
        }
    }

    /**
     * Keeps the launch screen up until the app has something real to show.
     *
     * Blocking the first draw is what holds it: on API 31+ that is the system splash, below it is
     * the window background, and either way the user sees one screen instead of the app cutting to
     * an empty surface while the view model is still being built. [App] calls back through
     * [releaseSplash] as soon as it can render the map.
     *
     * The delayed release is not optional. A pre-draw listener that returns false only gets asked
     * again on the next traversal, so if the UI went idle while still not ready, nothing would ever
     * ask again and the splash would stay up for good. The timeout also covers a failed init, which
     * leaves the view model null forever.
     */
    private fun holdSplashUntilReady() {
        val content: View = findViewById(android.R.id.content)
        contentView = content
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!isUiReady) return false
                content.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
        content.postDelayed(releaseSplashRunnable, MAX_SPLASH_HOLD_MS)
    }

    /** Lets the held first frame through. Safe to call more than once, and from any point. */
    private fun releaseSplash() {
        if (isUiReady) return
        isUiReady = true
        // Nothing may be invalidating by now, so ask for the traversal that runs the listener.
        contentView?.invalidate()
    }

    override fun onDestroy() {
        super.onDestroy()
        contentView?.removeCallbacks(releaseSplashRunnable)
        contentView = null
        // Cancel the activity-scoped init work so it doesn't outlive the Activity (it would
        // otherwise leak across configuration-change recreations). The navigation foreground
        // service runs independently and is unaffected.
        appScope.cancel()
        // The foreground service keeps the session going when the task is closed; the window
        // flags belong to this window and go with it.
        if (!isNavigationModeEnabled) {
            setNavigationLockScreenBehavior(false)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun requestLocationPermissionsIfNeeded() {
        // POST_NOTIFICATIONS goes in the same prompt: without it, Android 13+ silently drops the
        // navigation foreground-service notification, which is both the only sign that location
        // tracking is running and the only way back into the app from the shade.
        val wanted = buildList {
            addAll(LOCATION_PERMISSIONS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val missingPermissions = wanted.filter {
            ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isEmpty()) {
            LocationPermissionSignal.setGranted(true)
        } else {
            requestPermissions(missingPermissions.toTypedArray(), LOCATION_PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            // Push the fresh grant state so location collection restarts immediately.
            val hasLocation = LOCATION_PERMISSIONS.any {
                ContextCompat.checkSelfPermission(this, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            LocationPermissionSignal.setGranted(hasLocation)
        }
    }

    private fun startNavigationForegroundService() {
        val serviceIntent = Intent(this, NavigationModeForegroundService::class.java).apply {
            action = NavigationModeForegroundService.ACTION_START
        }
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun stopNavigationForegroundService() {
        val serviceIntent = Intent(this, NavigationModeForegroundService::class.java).apply {
            action = NavigationModeForegroundService.ACTION_STOP
        }
        startService(serviceIntent)
    }

    private fun setNavigationLockScreenBehavior(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(enabled)
            setTurnScreenOn(enabled)
            if (enabled) {
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                )
            } else {
                window.clearFlags(
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                )
            }
        } else {
            @Suppress("DEPRECATION")
            if (enabled) {
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                )
            } else {
                window.clearFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                )
            }
        }
    }

    companion object {
        /**
         * Ceiling on the splash hold. Long enough for the config parse and view model build that
         * [App] waits on, short enough that a cold or failing start still shows the app rather than
         * looking hung. Never wait on the network or on a location fix here — neither is bounded.
         */
        private const val MAX_SPLASH_HOLD_MS = 1200L

        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private val LOCATION_PERMISSIONS = arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }
}

