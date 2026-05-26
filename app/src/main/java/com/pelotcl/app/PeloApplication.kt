package com.pelotcl.app

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.pelotcl.app.generic.data.cache.journey.JourneyCache
import com.pelotcl.app.generic.data.config.AppConfigLoader
import com.pelotcl.app.generic.data.network.RetrofitInstance
import com.pelotcl.app.generic.data.repository.itinerary.itinerary.RaptorRepository
import com.pelotcl.app.generic.data.repository.offline.SchedulesRepository
import com.pelotcl.app.generic.data.telemetry.TelemetryService
import com.pelotcl.app.generic.service.TransportServiceProvider
import com.pelotcl.app.generic.utils.graphics.BusIconHelper
import com.pelotcl.app.generic.worker.TrafficAlertsWorker
import java.util.concurrent.TimeUnit

class PeloApplication : Application(), Configuration.Provider {

    companion object {
        private const val TAG = "PeloApplication"
        const val TRAFFIC_ALERTS_WORK_NAME = "traffic_alerts_periodic"
    }

    // On-demand WorkManager initialization (replaces automatic ContentProvider init)
    // This defers non-critical startup work until WorkManager is first accessed.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "PeloApplication onCreate()")
        
        // Verify Raptor assets are available at startup
        verifyRaptorAssets()
        
        TransportServiceProvider.initialize(this)
        RetrofitInstance.initialize(this, TransportServiceProvider.getTransportConfig())
        scheduleTrafficAlertsWork()
        initializeTelemetry()
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
            BusIconHelper.trimCache(level)
            try {
                JourneyCache.getInstance(this).trimMemory(level)
            } catch (_: Exception) {
                // JourneyCache may not be initialized yet
            }
        }
    }

    private fun scheduleTrafficAlertsWork() {
        // Schedule periodic work (minimum 15 minutes)
        val workRequest = PeriodicWorkRequestBuilder<TrafficAlertsWorker>(
            30, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            TRAFFIC_ALERTS_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
        Log.i(TAG, "Periodic work scheduled (every 30 minutes)")
    }
}
