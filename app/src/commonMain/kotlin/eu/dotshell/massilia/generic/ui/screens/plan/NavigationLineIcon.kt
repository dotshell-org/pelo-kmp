package eu.dotshell.massilia.generic.ui.screens.plan

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.dotshell.massilia.generic.utils.LineColorHelper
import eu.dotshell.massilia.generic.utils.graphics.LineIconResolver
import eu.dotshell.massilia.platform.DrawableProvider
import eu.dotshell.massilia.platform.LocalPlatformContext

@Composable
fun NavigationLineIcon(
    lineName: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val drawableName = LineIconResolver.getDrawableNameForLineName(lineName)
    val drawableProvider = DrawableProvider(LocalPlatformContext.current)
    val fallbackColor = Color(LineColorHelper.getColorForLineString(lineName))

    if (drawableName.isNotBlank() && drawableProvider.hasDrawable(drawableName)) {
        Image(
            painter = drawableProvider.getPainter(drawableName),
            contentDescription = null,
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
                text = lineName.ifBlank { "?" }.take(3),
                // Contrast on the fixed line-color badge — not theme-driven.
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
