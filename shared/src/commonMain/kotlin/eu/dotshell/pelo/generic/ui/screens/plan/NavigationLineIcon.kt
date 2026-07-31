package eu.dotshell.pelo.generic.ui.screens.plan

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.dotshell.pelo.generic.utils.LineColorHelper
import eu.dotshell.pelo.generic.utils.graphics.LineIconResolver
import eu.dotshell.pelo.platform.DrawableProvider
import eu.dotshell.pelo.platform.LocalPlatformContext
import eu.dotshell.pelo.platform.StringProvider

/**
 * Line badge for the navigation overlay. [lineName] must be a real line — an empty name used to
 * render a "?" disc, which is what a walking leg looked like; use [NavigationWalkIcon] for those.
 */
@Composable
fun NavigationLineIcon(
    lineName: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val strings = StringProvider(LocalPlatformContext.current)
    val drawableName = LineIconResolver.getDrawableNameForLineName(lineName)
    val drawableProvider = DrawableProvider(LocalPlatformContext.current)
    val fallbackColor = Color(LineColorHelper.getColorForLineString(lineName))
    // Screen readers otherwise skip the badge entirely, and the badge is the only thing on the
    // card that says which line the instruction is about.
    val label = strings.format("nav_line_badge", lineName)

    if (drawableName.isNotBlank() && drawableProvider.hasDrawable(drawableName)) {
        Image(
            painter = drawableProvider.getPainter(drawableName),
            contentDescription = label,
            modifier = modifier.size(size)
        )
    } else {
        Box(
            modifier = modifier
                .size((size - 2.dp).coerceAtLeast(20.dp))
                .clip(CircleShape)
                .background(fallbackColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = lineName.take(3),
                // Contrast on the fixed line-color badge — not theme-driven.
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Badge for a leg travelled on foot, so a walking step never renders as an unknown line. */
@Composable
fun NavigationWalkIcon(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val strings = StringProvider(LocalPlatformContext.current)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
            contentDescription = strings["nav_walk_badge"],
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(size * 0.6f)
        )
    }
}
