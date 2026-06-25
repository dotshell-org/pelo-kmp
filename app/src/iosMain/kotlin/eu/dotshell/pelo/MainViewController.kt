package eu.dotshell.pelo

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.ComposeUIViewController
import eu.dotshell.pelo.generic.service.TransportServiceProvider
import eu.dotshell.pelo.platform.LocalPlatformContext
import eu.dotshell.pelo.platform.PlatformContext
import platform.UIKit.UIViewController

/**
 * iOS no-op platform context. On Android, PlatformContext is android.content.Context; on iOS the
 * platform actuals (FileSystem, Settings, LocationProvider, …) don't need a real context, so a
 * single shared instance is enough. PlatformContext is `abstract` (to match the Android typealias
 * to the abstract android.content.Context), hence this concrete singleton.
 */
object IosPlatformContext : PlatformContext()

/**
 * Compose entry point, exported to Swift as `ComposeAppKt.MainViewController()`. Provides the iOS
 * [PlatformContext] and hosts the shared [App] (commonMain). The iosApp Xcode target wraps this
 * UIViewController in SwiftUI.
 */
fun MainViewController(): UIViewController {
    TransportServiceProvider.initialize(IosPlatformContext)
    return ComposeUIViewController {
        CompositionLocalProvider(LocalPlatformContext provides IosPlatformContext) {
            App()
        }
    }
}
