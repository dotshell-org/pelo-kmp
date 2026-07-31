package eu.dotshell.pelo.platform

import androidx.compose.runtime.Composable

@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No-op for iOS since back gestures are handled by native gesture controllers
}
