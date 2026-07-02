package eu.dotshell.pelo.generic.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val LightColorScheme = lightColorScheme(
    primary = Gray900,
    onPrimary = SecondaryColor,
    primaryContainer = Gray100,
    onPrimaryContainer = Gray900,
    secondary = Gray700,
    onSecondary = SecondaryColor,
    secondaryContainer = Gray200,
    onSecondaryContainer = Gray800,
    tertiary = AccentColor,
    onTertiary = SecondaryColor,
    background = SecondaryColor,
    onBackground = Gray900,
    surface = SecondaryColor,
    onSurface = Gray900,
    surfaceVariant = Gray100,
    onSurfaceVariant = Gray600,
    surfaceContainerLowest = SecondaryColor,
    surfaceContainerLow = Gray50,
    surfaceContainer = Gray100,
    surfaceContainerHigh = Gray200,
    surfaceContainerHighest = Gray300,
    outline = Gray400,
    outlineVariant = Gray200,
    error = Red600,
    onError = SecondaryColor,
    errorContainer = Red100,
    onErrorContainer = Red900,
    scrim = PrimaryColor,
)

val DarkColorScheme = darkColorScheme(
    primary = Gray100,
    onPrimary = Gray900,
    primaryContainer = Gray800,
    onPrimaryContainer = Gray100,
    secondary = Gray300,
    onSecondary = Gray900,
    secondaryContainer = Gray700,
    onSecondaryContainer = Gray100,
    tertiary = AccentColor,
    onTertiary = SecondaryColor,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Gray900,
    onSurfaceVariant = Gray400,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Gray950,
    surfaceContainer = Gray900,
    surfaceContainerHigh = Gray800,
    surfaceContainerHighest = Gray700,
    outline = Gray800,
    outlineVariant = Gray700,
    error = Red400,
    onError = Color.White,
    errorContainer = Red800,
    onErrorContainer = Red100,
    scrim = PrimaryColor,
)

@Composable
fun PeloTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
