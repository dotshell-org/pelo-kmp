package eu.dotshell.massilia.generic.ui.components.search.bar.stops

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.dotshell.massilia.generic.data.models.search.StationSearchResult
import androidx.compose.material3.MaterialTheme

import eu.dotshell.massilia.platform.LocalPlatformContext
import eu.dotshell.massilia.platform.StringProvider
import eu.dotshell.massilia.platform.provideTransportLineRules

@Composable
fun StopSearchResultItem(
    result: StationSearchResult,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit
) {
    val strings = StringProvider(LocalPlatformContext.current)
    ListItem(
        headlineContent = {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    result.stopName,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )
                val lineRules = provideTransportLineRules()
                if (result.lines.isNotEmpty()) {
                    Spacer(modifier = Modifier.size(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        result.lines.forEach { lineName ->
                            if (lineRules.isStrongLine(lineName)) {
                                SearchConnectionBadge(lineName = lineName, sizeDp = 24)
                            }
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy((-8).dp)
                    ) {
                        result.lines.forEach { lineName ->
                            if (!lineRules.isStrongLine(lineName)) {
                                SearchConnectionBadge(lineName = lineName, sizeDp = 24)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.size(4.dp))
            }
        },
        trailingContent = {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable(onClick = onClick)
            ) {
                Icon(
                    imageVector = Icons.Default.Directions,
                    contentDescription = strings["itinerary"],
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .size(17.dp)
                        .align(Alignment.Center)
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .clickable(onClick = onOptionsClick)
            .fillMaxWidth()
    )
}