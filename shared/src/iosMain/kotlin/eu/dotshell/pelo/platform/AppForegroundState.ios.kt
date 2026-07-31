package eu.dotshell.pelo.platform

import kotlin.concurrent.Volatile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification

/**
 * Backed by the UIKit application notifications, the same pair
 * [eu.dotshell.pelo.generic.data.telemetry.TelemetryService] observes.
 *
 * The observers are never removed: this is a process-lifetime singleton, so there is no point at
 * which removing them would be correct.
 */
actual object AppForegroundState {

    private val _isForeground = MutableStateFlow(true)
    actual val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    @Volatile
    private var started = false

    actual fun start(context: PlatformContext) {
        if (started) return
        started = true

        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationWillEnterForegroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
            usingBlock = { _ -> _isForeground.value = true }
        )

        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
            usingBlock = { _ -> _isForeground.value = false }
        )
    }
}
