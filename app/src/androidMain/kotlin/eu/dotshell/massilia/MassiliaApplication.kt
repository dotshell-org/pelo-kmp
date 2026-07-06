package eu.dotshell.massilia

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import eu.dotshell.massilia.generic.data.cache.journey.JourneyCache
import eu.dotshell.massilia.generic.data.config.AppConfigLoader
import eu.dotshell.massilia.generic.data.repository.itinerary.itinerary.RaptorRepository
import eu.dotshell.massilia.generic.data.repository.offline.SchedulesRepository
import eu.dotshell.massilia.generic.data.telemetry.TelemetryService
import eu.dotshell.massilia.generic.service.TransportServiceProvider
import eu.dotshell.massilia.platform.BackgroundScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MassiliaApplication : Application(), Configuration.Provider {

    companion object {
        private const val TAG = "MassiliaApplication"
    }

    // Application-level coroutine scope for background initialization
    private val appInitScope = CoroutineScope(Dispatchers.Default)

    // On-demand WorkManager initialization (replaces automatic ContentProvider init)
    // This defers non-critical startup work until WorkManager is first accessed.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "MassiliaApplication onCreate()")
        
        // Move all heavy initialization to background to avoid blocking UI thread
        appInitScope.launch {
            // Verify Raptor assets are available at startup (async)
            verifyRaptorAssets()
            
            // Initialize transport service provider (contains Retrofit instance)
            TransportServiceProvider.initialize(this@MassiliaApplication)

            // Schedule traffic alerts in background (skipped when real-time is disabled)
            if (TransportServiceProvider.getRealtimeConfig().trafficAlertsEnabled) {
                BackgroundScheduler(this@MassiliaApplication).ensureTrafficAlertsScheduled()
            }
            
            // Initialize telemetry in background
            initializeTelemetry()
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        appInitScope.cancel()
    }

    private fun initializeTelemetry() {
        try {
            val telemetryConfig = AppConfigLoader.getConfig().telemetry ?: return
            TelemetryService.initialize(this, telemetryConfig)
        } catch (e: Exception) {
            Log.w(TAG, "Telemetry init skipped", e)
        }
    }

    /**
     * Verify that all required Raptor assets are present
     * Logs critical errors if assets are missing
     */
    private fun verifyRaptorAssets() {
        try {
            val raptorRepository = RaptorRepository.getInstance(this)
            val assetsAvailable = raptorRepository.checkAssetsAvailable()
            
            if (!assetsAvailable) {
                Log.e(TAG, "CRITICAL: Raptor assets are missing!")
                Log.e(TAG, "This will cause bus stops to disappear from search and map functionality.")
                Log.e(TAG, "Required assets: holidays.json, raptor/stops_*.bin, raptor/routes_*.bin")
                Log.e(TAG, "Please try: File > Invalidate Caches / Restart in Android Studio")
                Log.e(TAG, "Then clean build: ./gradlew clean assembleDebug")
            } else {
                Log.i(TAG, "All Raptor assets verified successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Raptor assets: ${e.message}", e)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND) {
            Log.i(TAG, "onTrimMemory level=$level, trimming caches")
            SchedulesRepository.trimCaches(level)
            try {
                JourneyCache.getInstance(this).trimMemory(level)
            } catch (_: Exception) {
                // JourneyCache may not be initialized yet
            }
        }
    }
}
