package eu.dotshell.massilia.generic.ui.screens.plan

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.dotshell.massilia.generic.data.network.mapstyle.MapStyleData
import eu.dotshell.massilia.generic.ui.theme.bottomSheetContainerColor
import eu.dotshell.massilia.generic.ui.theme.isAppInDarkTheme
import eu.dotshell.massilia.platform.LocalPlatformContext
import eu.dotshell.massilia.platform.StringProvider
import eu.dotshell.massilia.platform.provideMapStyleConfig
import eu.dotshell.massilia.generic.utils.map.MapStyleUtils
import eu.dotshell.massilia.generic.utils.map.MapStyleUtils.mapStyleLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapStyleSelectionSheet(
    isOffline: Boolean,
    downloadedMapStyles: Set<String>,
    selectedMapStyle: MapStyleData,
    onDismiss: () -> Unit,
    onStyleSelected: (MapStyleData) -> Unit
) {
    val strings = StringProvider(LocalPlatformContext.current)
    val mapStyleConfig = provideMapStyleConfig()
    val darkTheme = isAppInDarkTheme()
    val standardStyles = remember { mapStyleConfig.getStandardMapStyles() }
    val satelliteStyle = remember { mapStyleConfig.getSatelliteMapStyle() }
    // The light/dark basemaps are merged into a single "Standard" tile that follows the app theme.
    val allStyles = remember(darkTheme, standardStyles, satelliteStyle) {
        val adaptiveStandard = MapStyleUtils.resolveForTheme(
            standardStyles.first { MapStyleUtils.isAdaptiveStandardKey(it.key) },
            darkTheme,
            mapStyleConfig
        )
        listOf(adaptiveStandard) +
            standardStyles.filterNot { MapStyleUtils.isAdaptiveStandardKey(it.key) } +
            satelliteStyle
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = bottomSheetContainerColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = strings["theme_title"],
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.size(16.dp))

            JustifiedFlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalSpacing = 16.dp,
                verticalSpacing = 12.dp
            ) {
                allStyles.forEach { style ->
                    val enabled = !isOffline || style.key in downloadedMapStyles
                    val isSelected = if (MapStyleUtils.isAdaptiveStandardKey(style.key)) {
                        MapStyleUtils.isAdaptiveStandardKey(selectedMapStyle.key)
                    } else {
                        style.key == selectedMapStyle.key
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .border(
                                    2.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    RoundedCornerShape(14.dp)
                                )
                                .padding(2.dp)
                        ) {
                            MapStylePreviewTile(
                                style = style,
                                isEnabled = enabled,
                                onClick = { onStyleSelected(style) }
                            )
                        }

                        Text(
                            text = mapStyleLabel(style),
                            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JustifiedFlowRow(
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 16.dp,
    verticalSpacing: Dp = 12.dp,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val maxWidth = constraints.maxWidth
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }

        val rows = mutableListOf<List<Placeable>>()
        var currentRow = mutableListOf<Placeable>()
        var currentRowWidth = 0
        val defaultSpacingPx = horizontalSpacing.roundToPx()

        for (placeable in placeables) {
            val neededWidth = if (currentRow.isEmpty()) placeable.width else placeable.width + defaultSpacingPx
            if (currentRowWidth + neededWidth <= maxWidth || currentRow.isEmpty()) {
                currentRow.add(placeable)
                currentRowWidth += neededWidth
            } else {
                rows.add(currentRow)
                currentRow = mutableListOf(placeable)
                currentRowWidth = placeable.width
            }
        }
        if (currentRow.isNotEmpty()) {
            rows.add(currentRow)
        }

        val rowHeights = rows.map { row -> row.maxOfOrNull { it.height } ?: 0 }
        val verticalSpacingPx = verticalSpacing.roundToPx()
        val totalHeight = rowHeights.sum() + (rows.size - 1).coerceAtLeast(0) * verticalSpacingPx

        layout(maxWidth, totalHeight) {
            var y = 0

            var spacingForLastRow = defaultSpacingPx
            if (rows.size > 1 && rows[0].size > 1) {
                val firstRow = rows[0]
                val firstRowItemsWidth = firstRow.sumOf { it.width }
                val remainingWidth = maxWidth - firstRowItemsWidth
                val numSpaces = firstRow.size - 1
                spacingForLastRow = remainingWidth / numSpaces
            }

            for (i in rows.indices) {
                val row = rows[i]
                val rowHeight = rowHeights[i]
                val isLastRow = i == rows.lastIndex

                if (isLastRow) {
                    var x = 0
                    for (placeable in row) {
                        placeable.place(x, y + (rowHeight - placeable.height) / 2)
                        x += placeable.width + spacingForLastRow
                    }
                } else {
                    if (row.size == 1) {
                        row[0].place(0, y + (rowHeight - row[0].height) / 2)
                    } else {
                        val totalItemsWidth = row.sumOf { it.width }
                        val remainingWidth = maxWidth - totalItemsWidth
                        val numSpaces = row.size - 1
                        var x = 0
                        for (j in row.indices) {
                            val placeable = row[j]
                            placeable.place(x, y + (rowHeight - placeable.height) / 2)
                            val spaceAfter = if (numSpaces > 0) {
                                remainingWidth / numSpaces + (if (j < remainingWidth % numSpaces) 1 else 0)
                            } else 0
                            x += placeable.width + spaceAfter
                        }
                    }
                }
                y += rowHeight + verticalSpacingPx
            }
        }
    }
}
