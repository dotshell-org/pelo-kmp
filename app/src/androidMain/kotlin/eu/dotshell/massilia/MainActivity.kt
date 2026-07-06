package eu.dotshell.massilia

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
import eu.dotshell.massilia.generic.data.cache.TransportCacheImpl
import eu.dotshell.massilia.generic.data.repository.itinerary.itinerary.RaptorRepository
import eu.dotshell.massilia.generic.data.repository.offline.SchedulesRepository
import eu.dotshell.massilia.generic.service.NavigationModeForegroundService
import eu.dotshell.massilia.generic.service.NavigationModeStateStore
import eu.dotshell.massilia.generic.utils.location.LocationPermissionSignal
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
            CompositionLocalProvider(eu.dotshell.massilia.platform.LocalPlatformContext provides this@MainActivity) {
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
                    },
                    // Ask for location only once the user has accepted the terms/privacy policy
                    // (fires immediately on launch for a user who already accepted).
                    onConsentAccepted = { requestLocationPermissionsIfNeeded() }
                )
            }
        }

        // Start all background preloading AFTER setContent to ensure UI displays immediately
        // This is critical for fast first render - do NOT block on these operations
        appScope.launch {
            try {
                // TransportServiceProvider + RetrofitInstance are initialized in MassiliaApplication.onCreate
                // so background workers and repositories can run before the first activity starts.

                // Parallel preloading - fire and forget (do NOT join)
                val cacheJob = launch {
                    val cache = TransportCacheImpl(applicationContext)
                    cache.preloadFromDisk()
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

    private fun requestLocationPermissionsIfNeeded() {
        val missingPermissions = LOCATION_PERMISSIONS.filter {
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
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private val LOCATION_PERMISSIONS = arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }
}

