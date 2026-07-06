package eu.dotshell.massilia.generic.data.telemetry

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import eu.dotshell.massilia.generic.data.config.TelemetryConfigData
import eu.dotshell.massilia.platform.BackgroundScheduler
import eu.dotshell.massilia.platform.Log

/**
 * Android lifecycle bridge for telemetry.
 *
 * Observes [ProcessLifecycleOwner] (app-wide foreground/background, not per-Activity — matching
 * the user's notion of "open"/"close") and forwards transitions to the shared
 * [TelemetrySessionController], which owns the debounce + upload-scheduling logic via
 * [BackgroundScheduler].
 *
 * Initialized exactly once from [eu.dotshell.massilia.MassiliaApplication.onCreate].
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
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
        initialized = true
        Log.i(TAG, "TelemetryService initialized")
    }
}
