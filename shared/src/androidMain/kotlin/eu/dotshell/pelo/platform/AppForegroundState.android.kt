package eu.dotshell.pelo.platform

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlin.concurrent.Volatile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Backed by [ProcessLifecycleOwner], which reports the app as a whole rather than one Activity —
 * the same source [eu.dotshell.pelo.generic.data.telemetry.TelemetryService] uses, and the one
 * that matches what a user means by having the app open.
 */
actual object AppForegroundState {

    private val _isForeground = MutableStateFlow(true)
    actual val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    @Volatile
    private var started = false

    private val observer = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            _isForeground.value = true
        }

        override fun onStop(owner: LifecycleOwner) {
            _isForeground.value = false
        }
    }

    actual fun start(context: PlatformContext) {
        if (started) return
        started = true
        // Callers reach this from background initialisation, but ProcessLifecycleOwner and
        // addObserver are main-thread-only.
        Handler(Looper.getMainLooper()).post {
            ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
        }
    }
}
