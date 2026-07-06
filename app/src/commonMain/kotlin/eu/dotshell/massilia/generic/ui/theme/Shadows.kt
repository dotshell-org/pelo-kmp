package eu.dotshell.massilia.generic.ui.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Unified shadow system for the Massilia app
 * Provides consistent shadows across all components
 */

// Shadow elevation levels (in dp)
object ShadowElevation {
    val none: Dp = 0.dp
    val small: Dp = 2.dp
    val medium: Dp = 4.dp
    val large: Dp = 8.dp
    val xlarge: Dp = 12.dp
}


/**
 * Standard button elevation modifier
 * Applies consistent elevation and shadow to buttons
 */
fun Modifier.buttonElevation(
    elevation: Dp = ShadowElevation.medium
): Modifier = this.graphicsLayer {
    shadowElevation = elevation.value
    shape = RectangleShape
    clip = false
}

/**
 * Standard card elevation modifier
 * Applies consistent elevation and shadow to cards
 */
fun Modifier.cardElevation(
    elevation: Dp = ShadowElevation.medium
): Modifier = this.graphicsLayer {
    shadowElevation = elevation.value
    shape = RectangleShape
    clip = false
}

/**
 * Floating action button elevation
 * Higher elevation for FABs
 */
fun Modifier.fabElevation(): Modifier = this.graphicsLayer {
    shadowElevation = ShadowElevation.xlarge.value
    shape = RectangleShape
    clip = false
}

/**
 * Standard elevated container
 * For surfaces that need to appear raised
 */
@Composable
fun ElevatedSurface(
    elevation: Dp = ShadowElevation.medium,
    shape: Shape = RectangleShape,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .graphicsLayer {
                this.shadowElevation = elevation.value
                this.shape = shape
                this.clip = false
            }
    ) {
        content()
    }
}

/**
 * Standard shadow for icons and small elements
 */
fun Modifier.iconShadow(): Modifier = this.graphicsLayer {
    shadowElevation = ShadowElevation.small.value
    shape = RectangleShape
    clip = false
}

/**
 * Subtle outline for surface-colored floating controls (search bar, map buttons, favorite chips)
 * so they stay legible against dark backgrounds. Draws a faint white border in dark mode; a no-op
 * in light mode, where the drop shadow already separates the control from the map.
 */
@Composable
fun Modifier.floatingControlBorder(shape: Shape): Modifier =
    if (isAppInDarkTheme()) {
        this.border(1.dp, Color.White.copy(alpha = 0.16f), shape)
    } else {
        this
    }

/**
 * Extension to add elevation to any modifier
 */
fun Modifier.withElevation(
    elevation: Dp
): Modifier = this.graphicsLayer {
    shadowElevation = elevation.value
    shape = RectangleShape
    clip = false
}
