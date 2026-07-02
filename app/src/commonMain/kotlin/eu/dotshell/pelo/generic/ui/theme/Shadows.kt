package eu.dotshell.pelo.generic.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Unified shadow system for the Pelo app
 * Provides consistent shadows across all components
 */

// Shadow elevation levels (in dp)
val ShadowElevation = object {
    val none: Dp = 0.dp
    val small: Dp = 2.dp
    val medium: Dp = 4.dp
    val large: Dp = 8.dp
    val xlarge: Dp = 12.dp
}

/**
 * Standard shadow colors based on theme
 */
@Composable
fun shadowColor(elevation: Dp = ShadowElevation.medium): Color {
    return when {
        elevation <= ShadowElevation.small -> Color.Black.copy(alpha = 0.12f)
        elevation <= ShadowElevation.medium -> Color.Black.copy(alpha = 0.16f)
        elevation <= ShadowElevation.large -> Color.Black.copy(alpha = 0.20f)
        else -> Color.Black.copy(alpha = 0.24f)
    }
}

/**
 * Standard button elevation modifier
 * Applies consistent elevation and shadow to buttons
 */
fun Modifier.buttonElevation(
    elevation: Dp = ShadowElevation.medium
): Modifier = composed {
    val shadowColor = shadowColor(elevation)
    
    this.graphicsLayer {
        shadowElevation = elevation
        shadowColor = shadowColor
        shape = RectangleShape
        clip = false
    }
}

/**
 * Standard card elevation modifier
 * Applies consistent elevation and shadow to cards
 */
fun Modifier.cardElevation(
    elevation: Dp = ShadowElevation.medium
): Modifier = composed {
    val shadowColor = shadowColor(elevation)
    
    this.graphicsLayer {
        shadowElevation = elevation
        shadowColor = shadowColor
        shape = RectangleShape
        clip = false
    }
}

/**
 * Floating action button elevation
 * Higher elevation for FABs
 */
fun Modifier.fabElevation(): Modifier = composed {
    val shadowColor = shadowColor(ShadowElevation.xlarge)
    
    this.graphicsLayer {
        shadowElevation = ShadowElevation.xlarge
        shadowColor = shadowColor
        shape = RectangleShape
        clip = false
    }
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
    val shadowColor = shadowColor(elevation)
    
    Box(
        modifier = Modifier
            .graphicsLayer {
                this.shadowElevation = elevation
                this.shadowColor = shadowColor
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
fun Modifier.iconShadow(): Modifier = composed {
    val shadowColor = shadowColor(ShadowElevation.small)
    
    this.graphicsLayer {
        shadowElevation = ShadowElevation.small
        shadowColor = shadowColor
        shape = RectangleShape
        clip = false
    }
}
