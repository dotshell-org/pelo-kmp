package eu.dotshell.pelo.generic.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.dotshell.pelo.generic.ui.theme.Gray700
import eu.dotshell.pelo.generic.utils.graphics.LineIconResolver
import eu.dotshell.pelo.generic.utils.schedule.DepartureManager
import eu.dotshell.pelo.platform.DrawableProvider
import eu.dotshell.pelo.platform.LocalPlatformContext
import eu.dotshell.pelo.platform.StringProvider

@Composable
fun DepartureListItem(
    lineName: String,
    directionName: String,
    departureTime: String,
    onClick: () -> Unit
) {
    val drawableProvider = DrawableProvider(LocalPlatformContext.current)
    val strings = StringProvider(LocalPlatformContext.current)
    val drawableName = remember(lineName) {
        LineIconResolver.getDrawableNameForLineName(lineName)
    }
    val hasDrawable = remember(drawableName) { drawableProvider.hasDrawable(drawableName) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasDrawable) {
                Image(
                    painter = drawableProvider.getPainter(drawableName),
                    contentDescription = strings["line_label"].replace("%s", lineName),
                    modifier = Modifier.size(52.dp)
                )
            } else {
                Text(
                    text = lineName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Gray700
                )
            }

            Spacer(modifier = Modifier.size(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = directionName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = departureTime,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DepartureManager.getDepartureColor(departureTime)
                )
                DepartureManager.formatRelativeDeparture(departureTime, strings)?.let { relativeText ->
                    Text(
                        text = relativeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = DepartureManager.getDepartureColor(departureTime)
                    )
                }
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = strings["line_details_cd"].replace("%s", lineName),
            tint = Gray700,
            modifier = Modifier.size(24.dp)
        )
    }
}
