package eu.dotshell.massilia.generic.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Main theme entry point for the Massilia app
 * 
 * Usage:
 * 
 * // In your App.kt or top-level composable:
 * MassiliaAppTheme(darkTheme = isDarkTheme) {
 *     // Your app content
 * }
 * 
 * // For components that need shadow:
 * Button(
 *     modifier = Modifier.buttonElevation(ShadowElevation.medium),
 *     ...
 * )
 * 
 * // Or use pre-built button components:
 * MassiliaFilledButton(onClick = { ... }) { Text("Click") }
 * MassiliaElevatedButton(onClick = { ... }) { Text("Important") }
 */

@Composable
fun MassiliaAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

/**
 * Preview theme wrappers for Jetpack Compose previews
 */
@Composable
fun MassiliaThemePreview(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MassiliaAppTheme(darkTheme = darkTheme, content = content)
}

@Composable
fun MassiliaLightThemePreview(content: @Composable () -> Unit) {
    MassiliaAppTheme(darkTheme = false, content = content)
}

@Composable
fun MassiliaDarkThemePreview(content: @Composable () -> Unit) {
    MassiliaAppTheme(darkTheme = true, content = content)
}

/**
 * Theme color extensions for easy access
 */
val MaterialTheme.massiliaColors: MassiliaColors
    @Composable
    get() = MassiliaColors(
        primary = colorScheme.primary,
        onPrimary = colorScheme.onPrimary,
        secondary = colorScheme.secondary,
        onSecondary = colorScheme.onSecondary,
        background = colorScheme.background,
        onBackground = colorScheme.onBackground,
        surface = colorScheme.surface,
        onSurface = colorScheme.onSurface,
        error = colorScheme.error,
        onError = colorScheme.onError
    )

/**
 * Wrapper for theme colors with convenient access
 */
data class MassiliaColors(
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val error: Color,
    val onError: Color
)

/**
 * Helper to get current theme colors
 */
@Composable
fun currentThemeColors(): MassiliaColors {
    return MassiliaColors(
        primary = MaterialTheme.colorScheme.primary,
        onPrimary = MaterialTheme.colorScheme.onPrimary,
        secondary = MaterialTheme.colorScheme.secondary,
        onSecondary = MaterialTheme.colorScheme.onSecondary,
        background = MaterialTheme.colorScheme.background,
        onBackground = MaterialTheme.colorScheme.onBackground,
        surface = MaterialTheme.colorScheme.surface,
        onSurface = MaterialTheme.colorScheme.onSurface,
        error = MaterialTheme.colorScheme.error,
        onError = MaterialTheme.colorScheme.onError
    )
}
