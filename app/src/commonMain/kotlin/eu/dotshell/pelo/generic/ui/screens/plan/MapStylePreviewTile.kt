package eu.dotshell.pelo.generic.ui.screens.plan

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.dotshell.pelo.generic.data.network.mapstyle.MapStyleData
import eu.dotshell.pelo.platform.DrawableProvider
import eu.dotshell.pelo.platform.LocalPlatformContext
import eu.dotshell.pelo.platform.StringProvider

@Composable
fun MapStylePreviewTile(
    style: MapStyleData,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    // Keyed on the concrete style, not the pair, so the tile previews the half the user
    // will actually get in the current theme.
    val drawableName = when (style.key) {
        "bright" -> "visu_osm_bright"
        "bright_dark" -> "visu_osm_bright_dark"
        "liberty" -> "visu_liberty"
        "liberty_dark" -> "visu_liberty_dark"
        "satellite" -> "visu_satellite"
        else -> "visu_osm_bright"
    }
    val drawableProvider = DrawableProvider(LocalPlatformContext.current)
    val strings = StringProvider(LocalPlatformContext.current)
    val previewPainter = drawableProvider.getPainter(drawableName)
    val alpha = if (isEnabled) 1f else 0.4f
    Box(
        modifier = Modifier
            .size(60.dp)
            .border(
                width = 0.5.dp,
                color = Color.Gray,
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = isEnabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = previewPainter,
            contentDescription = strings["map_style_preview"],
            modifier = Modifier.fillMaxSize(),
            alpha = alpha
        )
    }
}
