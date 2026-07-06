package eu.dotshell.massilia.generic.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import eu.dotshell.massilia.generic.data.repository.offline.theme.ThemeMode

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
    // Neutral (non-bluish) grays for dark-mode surfaces/containers: the Gray* palette is
    // blue-tinted, so buttons and cards (direction selector, settings) use Neutral* instead.
    surfaceVariant = Neutral800,
    onSurfaceVariant = Neutral400,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Neutral950,
    surfaceContainer = Neutral900,
    surfaceContainerHigh = Neutral800,
    surfaceContainerHighest = Neutral700,
    outline = Neutral800,
    outlineVariant = Neutral700,
    error = Red400,
    onError = Color.White,
    errorContainer = Red800,
    onErrorContainer = Red100,
    scrim = PrimaryColor,
)

@Composable
fun MassiliaTheme(
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

/**
 * The effective dark/light state of the app, derived from the user's [ThemeMode] selection
 * exposed via [LocalThemeController]. Use this below the theme provider when a component needs
 * to branch on light vs dark independently of the [MaterialTheme] color scheme.
 */
@Composable
fun isAppInDarkTheme(): Boolean = when (LocalThemeController.current.themeMode) {
    ThemeMode.AUTO -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * Container color for bottom sheets. In dark mode this is a neutral dark gray (not pure black,
 * not bluish) so sheets stand out as a distinct surface above the black navbar and map; in light
 * mode it is the standard light surface.
 */
@Composable
fun bottomSheetContainerColor(): Color =
    if (isAppInDarkTheme()) DarkSheetSurface else MaterialTheme.colorScheme.surface

/**
 * Container color for the inline "search field" chips shown inside sheets/dialogs. A neutral
 * (non-bluish) filled-input tone that contrasts with the sheet in both themes and follows the app
 * theme, instead of inverting the way [androidx.compose.material3.ColorScheme.primary] would
 * (dark in light mode, light in dark mode).
 */
@Composable
fun searchFieldContainerColor(): Color =
    if (isAppInDarkTheme()) Color(0xFF2A2A2A) else Color(0xFFF3F4F6)
