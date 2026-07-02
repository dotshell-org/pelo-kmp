package eu.dotshell.pelo.generic.ui.components.search.bar

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import eu.dotshell.pelo.generic.data.repository.offline.search.SearchHistoryItem
import eu.dotshell.pelo.generic.data.repository.offline.search.SearchType
import eu.dotshell.pelo.generic.ui.components.search.bar.stops.SearchConnectionBadge
import androidx.compose.material3.MaterialTheme

import eu.dotshell.pelo.platform.LocalPlatformContext
import eu.dotshell.pelo.platform.StringProvider
import eu.dotshell.pelo.platform.provideTransportLineRules

@Composable
fun HistoryListItem(
    historyItem: SearchHistoryItem,
    showRemove: Boolean,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    val strings = StringProvider(LocalPlatformContext.current)
    ListItem(
        headlineContent = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = if (historyItem.type == SearchType.LINE) 10.dp else 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (historyItem.type == SearchType.LINE) {
                    // Show line icon on the left for LINE type history items (larger size)
                    SearchConnectionBadge(lineName = historyItem.query, sizeDp = 44)
                    Spacer(modifier = Modifier.size(12.dp))
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy((-6).dp)
                ) {
                    val lineRules = provideTransportLineRules()
                    val displayText = if (historyItem.type == SearchType.LINE) {
                        "${lineRules.getTransportType(historyItem.query)} ${historyItem.query}"
                    } else {
                        historyItem.query
                    }
                    Text(
                        displayText,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium
                    )
                    // For stops, show ALL line icons below the name
                    if (historyItem.type == SearchType.STOP && historyItem.lines.isNotEmpty()) {
                        Spacer(modifier = Modifier.size(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            historyItem.lines.forEach { lineName ->
                                if (lineRules.isStrongLine(lineName)) {
                                    SearchConnectionBadge(lineName = lineName, sizeDp = 24)
                                }
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy((-8).dp)
                        ) {
                            historyItem.lines.forEach { lineName ->
                                if (!lineRules.isStrongLine(lineName)) {
                                    SearchConnectionBadge(lineName = lineName, sizeDp = 24)
                                }
                            }
                        }
                    }
                }
                if (historyItem.type == SearchType.STOP) {
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
                }
                if (showRemove) {
                    IconButton(onClick = onRemoveClick) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = strings["delete"],
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .clickable(onClick = if (historyItem.type == SearchType.LINE) onClick else onOptionsClick)
            .fillMaxWidth()
    )
}
