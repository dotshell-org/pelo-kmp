package eu.dotshell.pelo.generic.data.telemetry

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import eu.dotshell.pelo.generic.data.config.TelemetryConfigData
import eu.dotshell.pelo.platform.BackgroundScheduler
import eu.dotshell.pelo.platform.Log

/**
 * Android lifecycle bridge for telemetry.
 *
 * Observes [ProcessLifecycleOwner] (app-wide foreground/background, not per-Activity — matching
 * the user's notion of "open"/"close") and forwards transitions to the shared
 * [TelemetrySessionController], which owns the debounce + upload-scheduling logic via
 * [BackgroundScheduler].
 *
 * Initialized exactly once from [eu.dotshell.pelo.PeloApplication.onCreate].
 */
object TelemetryService {

    private const val TAG = "TelemetryService"

    @Volatile
    private var initialized = false

    private lateinit var controller: TelemetrySessionController

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            controller.onForeground()
        }

        override fun onStop(owner: LifecycleOwner) {
            controller.onBackground()
        }
    }

    fun initialize(context: Context, config: TelemetryConfigData) {
        if (initialized) return
        if (!config.enabled) {
            Log.i(TAG, "Telemetry disabled in config — service inactive")
            return
        }
        val appContext = context.applicationContext
        TelemetryEmitter.initialize(appContext, config)
        controller = TelemetrySessionController(
            scheduler = BackgroundScheduler(appContext),
            debounceSeconds = config.closeDebounceSeconds
        )
        // Set before scheduling the observer so a re-entrant initialize() is a no-op and the
        // controller is ready before onStart/onStop can fire.
        initialized = true
        // initialize() runs on a background init coroutine (PeloApplication.onCreate), but
        // ProcessLifecycleOwner + LifecycleRegistry.addObserver are main-thread-only. Hop to the
        // main thread to register the foreground/background (session open/close) observer.
        Handler(Looper.getMainLooper()).post {
            ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
            Log.i(TAG, "TelemetryService initialized")
        }
    }
}
