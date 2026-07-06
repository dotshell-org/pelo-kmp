package eu.dotshell.massilia.generic.ui.screens.plan

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
import eu.dotshell.massilia.generic.data.network.mapstyle.MapStyleData
import eu.dotshell.massilia.platform.DrawableProvider
import eu.dotshell.massilia.platform.LocalPlatformContext
import eu.dotshell.massilia.platform.StringProvider

@Composable
fun MapStylePreviewTile(
    style: MapStyleData,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    val drawableName = when (style.key) {
        "positron" -> "visu_positron"
        "dark_matter" -> "visu_dark_matter"
        "bright" -> "visu_osm_bright"
        "liberty" -> "visu_liberty"
        "satellite" -> "visu_satellite"
        else -> "visu_positron"
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
