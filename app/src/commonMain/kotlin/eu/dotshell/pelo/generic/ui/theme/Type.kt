package eu.dotshell.pelo.generic.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import eu.dotshell.pelo.resources.Res
import eu.dotshell.pelo.resources.plusjakartasans_bold
import eu.dotshell.pelo.resources.plusjakartasans_medium
import eu.dotshell.pelo.resources.plusjakartasans_regular
import eu.dotshell.pelo.resources.plusjakartasans_semibold
import org.jetbrains.compose.resources.Font

/**
 * Plus Jakarta Sans, bundled under composeResources/font/. The four static weights
 * the app actually uses (Normal 400, Medium 500, SemiBold 600, Bold 700) are all
 * provided, so no weight is ever synthesized (fake-bolded) or matched to a wrong cut.
 */
@Composable
private fun plusJakartaSans() = FontFamily(
    Font(Res.font.plusjakartasans_regular, FontWeight.Normal),
    Font(Res.font.plusjakartasans_medium, FontWeight.Medium),
    Font(Res.font.plusjakartasans_semibold, FontWeight.SemiBold),
    Font(Res.font.plusjakartasans_bold, FontWeight.Bold),
)

/**
 * The app-wide Material typography, rendered in Plus Jakarta Sans. This keeps every
 * Material3 default (sizes, line-heights, weights, letter-spacing) and only swaps the
 * font family on each role — Material3 has no `defaultFontFamily` param, so each of the
 * 15 styles is copied explicitly. `Font(...)` from compose-resources is @Composable, so
 * this is a function rather than a top-level val.
 */
@Composable
fun appTypography(): Typography {
    val ff = plusJakartaSans()
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = ff),
        displayMedium = base.displayMedium.copy(fontFamily = ff),
        displaySmall = base.displaySmall.copy(fontFamily = ff),
        headlineLarge = base.headlineLarge.copy(fontFamily = ff),
        headlineMedium = base.headlineMedium.copy(fontFamily = ff),
        headlineSmall = base.headlineSmall.copy(fontFamily = ff),
        titleLarge = base.titleLarge.copy(fontFamily = ff),
        titleMedium = base.titleMedium.copy(fontFamily = ff),
        titleSmall = base.titleSmall.copy(fontFamily = ff),
        bodyLarge = base.bodyLarge.copy(fontFamily = ff),
        bodyMedium = base.bodyMedium.copy(fontFamily = ff),
        bodySmall = base.bodySmall.copy(fontFamily = ff),
        labelLarge = base.labelLarge.copy(fontFamily = ff),
        labelMedium = base.labelMedium.copy(fontFamily = ff),
        labelSmall = base.labelSmall.copy(fontFamily = ff),
    )
}
