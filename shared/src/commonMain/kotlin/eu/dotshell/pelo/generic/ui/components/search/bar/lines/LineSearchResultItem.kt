package eu.dotshell.pelo.generic.ui.components.search.bar.lines

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import eu.dotshell.pelo.generic.data.models.search.LineSearchResult
import androidx.compose.material3.MaterialTheme
import eu.dotshell.pelo.generic.utils.graphics.LineIconResolver
import eu.dotshell.pelo.platform.DrawableProvider
import eu.dotshell.pelo.platform.LocalPlatformContext
import eu.dotshell.pelo.platform.StringProvider

@Composable
fun LineSearchResultItem(
    lineResult: LineSearchResult,
    onClick: () -> Unit
) {
    val context = LocalPlatformContext.current
    val drawableProvider = remember(context) { DrawableProvider(context) }
    val stringProvider = remember(context) { StringProvider(context) }
    val drawableName = remember(lineResult.lineName) {
        LineIconResolver.getDrawableNameForLineName(lineResult.lineName)
    }
    val hasDrawable = remember(drawableName) { drawableProvider.hasDrawable(drawableName) }
    val hasModeBus = remember { drawableProvider.hasDrawable("mode_bus") }

    ListItem(
        headlineContent = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasDrawable) {
                    Image(
                        painter = drawableProvider.getPainter(drawableName),
                        contentDescription = stringProvider["line_icon"].replace("%s", lineResult.lineName),
                        modifier = Modifier.size(40.dp)
                    )
                } else if (hasModeBus) {
                    Image(
                        painter = drawableProvider.getPainter("mode_bus"),
                        contentDescription = stringProvider["bus_mode_icon"],
                        modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(modifier = Modifier.size(8.dp))
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text(
                        "${lineResult.category} ${lineResult.lineName}",
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
    )
}
