package eu.dotshell.pelo

import android.content.Intent
import android.os.Build
import android.os.Bundle
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
import eu.dotshell.pelo.generic.ui.screens.onboarding.NotificationPermissionGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Application-level coroutine scope for early background work
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var isNavigationModeEnabled = false
    private var hasAppliedFirstNavigationCallback = false

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Restore lockscreen behavior when navigation is still active in foreground service.
        if (NavigationModeStateStore.isNavigationActive(this)) {
            isNavigationModeEnabled = true
            setNavigationLockScreenBehavior(true)
        }

        // Preload disk cache and binary schedule data in parallel BEFORE UI (critical for first render)
        // Retrofit initialization is deferred to after setContent
        appScope.launch {
            try {
                // Parallel cache and schedule-data warmup - these are needed for initial UI
                val cacheJob = launch {
                    val cache = TransportCacheImpl(applicationContext)
                    cache.preloadFromDisk()
                }
                val schedulesJob = launch {
                    val schedulesRepo =
                        SchedulesRepository.getInstance(applicationContext)
                    schedulesRepo.warmupDatabase()
                }
                // Wait for cache and schedule data warmup (critical for UI)
                cacheJob.join()
                schedulesJob.join()
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Startup preload failed: ${e.message}")
            }
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.dark(
                android.graphics.Color.TRANSPARENT
            )
        )

        // Request location permissions if they are not already granted
        val locationPermissions = arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val missingPermissions = locationPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            requestPermissions(missingPermissions.toTypedArray(), 1001)
        }

        setContent {
            CompositionLocalProvider(eu.dotshell.pelo.platform.LocalPlatformContext provides this@MainActivity) {
                NotificationPermissionGate {
                    App(
                        onNavigationModeChanged = { active ->
                            if (!hasAppliedFirstNavigationCallback) {
                                hasAppliedFirstNavigationCallback = true
                                // Ignore initial Compose state restoration mismatch when service is still active.
                                if (isNavigationModeEnabled && !active) return@App
                            }
                            if (active == isNavigationModeEnabled) return@App
                            isNavigationModeEnabled = active
                            if (active) {
                                startNavigationForegroundService()
                            } else {
                                stopNavigationForegroundService()
                            }
                            setNavigationLockScreenBehavior(active)
                        }
                    )
                }
            }
        }

        // Deferred initialization - run AFTER setContent to not block first frame
        // These are not needed for initial UI display
        appScope.launch {
            // TransportServiceProvider + RetrofitInstance are initialized in PeloApplication.onCreate
            // so background workers and repositories can run before the first activity starts.

            // Preload Raptor library in background (only needed for itinerary calculations)
            // yield() gives the UI thread priority without an arbitrary delay
            kotlinx.coroutines.yield()
            try {
                val raptorRepo =
                    RaptorRepository.getInstance(
                        applicationContext
                    )
                raptorRepo.initialize()
                raptorRepo.preloadJourneyCache()
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Raptor preload failed: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel the activity-scoped init work so it doesn't outlive the Activity (it would
        // otherwise leak across configuration-change recreations). The navigation foreground
        // service runs independently and is unaffected.
        appScope.cancel()
        // Keep navigation service running when activity/task is closed.
        if (!isNavigationModeEnabled) {
            setNavigationLockScreenBehavior(false)
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
}

