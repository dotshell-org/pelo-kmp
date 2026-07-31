package eu.dotshell.pelo

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.ComposeUIViewController
import eu.dotshell.pelo.generic.data.config.AppConfigLoader
import eu.dotshell.pelo.generic.data.telemetry.TelemetryService
import eu.dotshell.pelo.generic.service.TransportServiceProvider
import eu.dotshell.pelo.generic.utils.location.LocationPermissionManager
import eu.dotshell.pelo.platform.BackgroundScheduler
import eu.dotshell.pelo.platform.LanguageManager
import eu.dotshell.pelo.platform.LocalPlatformContext
import eu.dotshell.pelo.platform.Log
import eu.dotshell.pelo.platform.ProvideAppLocale
import eu.dotshell.pelo.platform.PlatformContext
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController

/**
 * iOS no-op platform context. On Android, PlatformContext is android.content.Context; on iOS the
 * platform actuals (FileSystem, Settings, LocationProvider, …) don't need a real context, so a
 * single shared instance is enough. PlatformContext is `abstract` (to match the Android typealias
 * to the abstract android.content.Context), hence this concrete singleton.
 */
object IosPlatformContext : PlatformContext()

fun initializeKmpDependencies() {
    TransportServiceProvider.initialize(IosPlatformContext)
    try {
        val telemetryConfig = AppConfigLoader.getConfig().telemetry
        if (telemetryConfig != null) {
            TelemetryService.initialize(IosPlatformContext, telemetryConfig)
        }
        if (TransportServiceProvider.getRealtimeConfig().trafficAlertsEnabled) {
            BackgroundScheduler(IosPlatformContext).ensureTrafficAlertsScheduled()
        }
    } catch (e: Exception) {
        Log.w("MainViewController", "Failed to initialize Telemetry: ${e.message}")
    }
    LanguageManager.init(IosPlatformContext)
}

/**
 * Compose entry point, exported to Swift as `ComposeAppKt.MainViewController()`. Provides the iOS
 * [PlatformContext] and hosts the shared [App] (commonMain). The iosApp Xcode target wraps this
 * UIViewController in SwiftUI.
 *
 * @param onReady fires on the first frame that has a map in it. `ContentView` keeps a stand-in for
 *   the launch screen over the Compose view until then — iOS drops the real launch screen as soon
 *   as the first frame renders, so unlike Android it can only be imitated, not held.
 */
fun MainViewController(onReady: () -> Unit): UIViewController {

    return ComposeUIViewController {
        CompositionLocalProvider(LocalPlatformContext provides IosPlatformContext) {
            ProvideAppLocale(LanguageManager.current.tag) {
            App(
                onReady = onReady,
                onNavigationModeChanged = { active ->
                    // The counterpart of Android's FLAG_KEEP_SCREEN_ON. Without it the phone
                    // locked itself mid-journey after the usual idle timeout, which is exactly
                    // when guidance is being read.
                    UIApplication.sharedApplication.idleTimerDisabled = active
                    if (active) {
                        // Always authorization is what lets fixes keep arriving in the background;
                        // the accuracy and background-update switches live on the running stream
                        // (LocationProvider.setNavigationMode), not here.
                        LocationPermissionManager.requestNavigationPermissions(IosPlatformContext)
                    }
                }
            )
            }
        }
    }
}
