package eu.dotshell.pelo.platform

import androidx.compose.runtime.compositionLocalOf

val LocalPlatformContext = compositionLocalOf<PlatformContext> {
    error("PlatformContext not provided")
}
