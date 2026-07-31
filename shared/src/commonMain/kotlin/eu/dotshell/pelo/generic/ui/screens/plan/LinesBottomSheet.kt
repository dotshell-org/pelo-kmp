package eu.dotshell.pelo.generic.ui.screens.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import eu.dotshell.pelo.generic.ui.theme.bottomSheetContainerColor
import eu.dotshell.pelo.generic.utils.search.SearchUtils
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.dotshell.pelo.generic.data.models.realtime.alerts.official.AlertSeverity
import eu.dotshell.pelo.generic.data.models.realtime.alerts.official.AlertSeverity as TrafficAlertSeverity
import eu.dotshell.pelo.generic.ui.viewmodel.TransportViewModelInterface
import eu.dotshell.pelo.generic.utils.LineColorHelper
import eu.dotshell.pelo.platform.DrawableProvider
import eu.dotshell.pelo.platform.LocalPlatformContext
import eu.dotshell.pelo.platform.StringProvider

/**
 * One LazyColumn row: a category title, or a single row of chips inside a category.
 *
 * @Immutable for ChipRow's list of lines; without it the chip row could not skip while scrolling,
 * which is the one thing this sheet is measured on.
 */
@Immutable
private sealed interface LinesListItem {
    data class Header(val categoryId: String) : LinesListItem
    data class ChipRow(
        val categoryId: String,
        val index: Int,
        val lines: List<LineEntry>,
        val isLastRow: Boolean
    ) : LinesListItem
}

/**
 * Bottom Sheet qui affiche toutes les lignes organisées par catégories
 */
@Composable
fun LinesBottomSheet(
    allLines: List<String>,
    onLineClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransportViewModelInterface? = null
) {
    val platformContext = LocalPlatformContext.current
    // Remembered: DrawableProvider has identity equality, so re-creating it every recomposition
    // would make every remember() keyed on it re-run each pass.
    val drawableProvider = remember(platformContext) { DrawableProvider(platformContext) }
    val strings = StringProvider(platformContext)
    // Resolved once here rather than inside every chip: a stringResource() per line meant one
    // resource read per chip, hundreds of them on a single frame.
    val lineLabelTemplate = strings["line_label"]
    val searchQuery by remember { mutableStateOf("") }

    // State pour gérer le scroll
    val listState = rememberLazyListState()

    // Détecte si on est en bas de la liste
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            val isLastItemVisible = lastVisibleItem?.index == layoutInfo.totalItemsCount - 1
            val isLastItemFullyVisible = lastVisibleItem?.let {
                it.offset + it.size <= layoutInfo.viewportEndOffset
            } ?: false
            isLastItemVisible && isLastItemFullyVisible
        }
    }

    // Détecte si on est en haut de la liste

    // NestedScrollConnection pour arrêter le scroll vers le bas seulement quand on atteint la fin
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Si on scrolle vers le bas (available.y < 0) et qu'on est déjà en bas, bloquer
                if (available.y < 0 && isAtBottom) {
                    return Offset(0f, available.y)
                }
                // Ne PAS bloquer le scroll vers le haut quand on est en haut pour permettre
                // l'interaction avec la BottomSheet (dismiss par drag)
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // Consommer tout le scroll restant seulement si on scrolle vers le bas en étant en bas
                if (isAtBottom && available.y < 0) {
                    return Offset(0f, available.y)
                }
                // Ne PAS consommer le scroll vers le haut pour permettre le dismiss de la BottomSheet
                return Offset.Zero
            }
        }
    }

    // Observe traffic alerts from ViewModel
    val trafficAlerts by viewModel?.trafficAlerts?.collectAsState(initial = emptyList()) ?: remember {
        mutableStateOf(
            emptyList()
        )
    }

    // Compute alerts using ViewModel indexing (fast path, avoids O(lines * alerts)).
    val lineAlerts = remember(trafficAlerts, allLines) {
        if (viewModel != null && allLines.isNotEmpty() && trafficAlerts.isNotEmpty()) {
            viewModel.getAlertSeverityMapForLines(allLines)
        } else {
            emptyMap()
        }
    }

    // Categorised off the main thread and memoised across openings — see LinesCatalog.
    val categorizedLines = rememberLineCategories(allLines)

    // Decode the icons the user has not reached yet, in the frames left idle between scrolls.
    LineIconWarmup(allLines = allLines, pauseWhileScrolling = listState)

    // Filtrer les lignes selon la recherche
    val filteredCategories = remember(categorizedLines, searchQuery) {
        if (searchQuery.isEmpty()) {
            categorizedLines
        } else {
            categorizedLines.mapNotNull { category ->
                val filtered = category.lines.filter { SearchUtils.fuzzyContains(it.name, searchQuery) }
                if (filtered.isNotEmpty()) category.copy(lines = filtered) else null
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .background(bottomSheetContainerColor(), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
    ) {
        // The chip grid is laid out by hand rather than with FlowRow so that each row can be its
        // own lazy item: one item per category would compose every chip of that category (150+
        // for the buses) in a single frame, which is what made the list stutter while scrolling.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val itemWidth = 72.dp
            val availableWidth = maxWidth

            // Nombre d'items par ligne (minimum 4)
            val itemsPerRow = (availableWidth / itemWidth).toInt().coerceAtLeast(4)

            // Si itemsPerRow est 4 mais que ça ne rentre pas avec itemWidth,
            // on réduit dynamiquement la taille de l'item pour que ça rentre.
            val actualItemWidth = if (availableWidth < itemWidth * itemsPerRow) {
                availableWidth / itemsPerRow
            } else {
                itemWidth
            }

            // Calcul de l'écart pour la justification (SpaceBetween)
            // gap = (TotalWidth - (itemsPerRow * actualItemWidth)) / (itemsPerRow - 1)
            val gap = if (itemsPerRow > 1) {
                (availableWidth - (actualItemWidth * itemsPerRow)) / (itemsPerRow - 1)
            } else {
                0.dp
            }

            val flattenedItems = remember(filteredCategories, itemsPerRow) {
                buildList {
                    filteredCategories.forEach { category ->
                        add(LinesListItem.Header(category.id))
                        val rows = category.lines.chunked(itemsPerRow)
                        rows.forEachIndexed { rowIndex, rowLines ->
                            add(
                                LinesListItem.ChipRow(
                                    categoryId = category.id,
                                    index = rowIndex,
                                    lines = rowLines,
                                    isLastRow = rowIndex == rows.lastIndex
                                )
                            )
                        }
                    }
                }
            }

            // List of lines by category
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection),
                userScrollEnabled = true
            ) {
                items(
                    items = flattenedItems,
                    key = { item ->
                        when (item) {
                            is LinesListItem.Header -> "header_${item.categoryId}"
                            is LinesListItem.ChipRow -> "row_${item.categoryId}_${item.index}"
                        }
                    },
                    contentType = { item ->
                        when (item) {
                            is LinesListItem.Header -> "header"
                            is LinesListItem.ChipRow -> "row"
                        }
                    }
                ) { item ->
                    when (item) {
                        is LinesListItem.Header -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                val categoryText = when (item.categoryId) {
                                    "Métro" -> strings["category_metro"]
                                    "Funiculaire" -> strings["category_funicular"]
                                    "Tramway" -> strings["category_tramway"]
                                    "Navigone" -> strings["category_navigone"]
                                    "Chrono" -> strings["category_chrono"]
                                    "Pleine Lune" -> strings["category_pleine_lune"]
                                    "Gare Express" -> strings["category_gare_express"]
                                    "Navette" -> strings["category_navette"]
                                    "Soyeuse" -> strings["category_soyeuse"]
                                    "Zone Industrielle" -> strings["category_zone_industrielle"]
                                    "Bus" -> strings["category_bus"]
                                    "Cars du Rhône TCL unifié" -> strings["category_cars_du_rhone"]
                                    "Junior Direct" -> strings["category_junior_direct"]
                                    else -> item.categoryId
                                }
                                Text(
                                    text = categoryText,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp, bottom = 4.dp)
                                )
                            }
                        }

                        is LinesListItem.ChipRow -> {
                            LineChipRow(
                                item = item,
                                itemsPerRow = itemsPerRow,
                                itemWidth = actualItemWidth,
                                gap = gap,
                                lineAlerts = lineAlerts,
                                lineLabelTemplate = lineLabelTemplate,
                                drawableProvider = drawableProvider,
                                onLineClick = onLineClick
                            )
                        }
                    }
                }

                // Message if no results
                if (filteredCategories.isEmpty() && searchQuery.isNotEmpty()) {
                    item(key = "no_results") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = strings["no_lines_found"],
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A single row of chips. Justified left and right, with the last row of a category keeping the
 * same spacing instead of being stretched.
 */
@Composable
private fun LineChipRow(
    item: LinesListItem.ChipRow,
    itemsPerRow: Int,
    itemWidth: Dp,
    gap: Dp,
    lineAlerts: Map<String, TrafficAlertSeverity>,
    lineLabelTemplate: String,
    drawableProvider: DrawableProvider,
    onLineClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (item.isLastRow) 8.dp else 0.dp),
        horizontalArrangement = if (item.isLastRow || itemsPerRow == 1) {
            Arrangement.spacedBy(gap)
        } else {
            Arrangement.SpaceBetween
        }
    ) {
        item.lines.forEach { line ->
            LineChip(
                line = line,
                onClick = { onLineClick(line.name) },
                alertSeverity = lineAlerts[line.alertKey],
                lineLabelTemplate = lineLabelTemplate,
                drawableProvider = drawableProvider,
                modifier = Modifier.width(itemWidth)
            )
        }
    }
}

/**
 * Chip to show a line with the official TCL icon
 */
@Composable
private fun LineChip(
    line: LineEntry,
    onClick: () -> Unit,
    lineLabelTemplate: String,
    modifier: Modifier = Modifier,
    alertSeverity: TrafficAlertSeverity? = null,
    drawableProvider: DrawableProvider
) {
    // The touch target is the whole cell. It used to be an inner 64.dp box inside this 50.dp
    // one: taller than its parent, so it reached 7.dp into the rows above and below, where
    // Compose has to arbitrate between it and the row actually under the finger — a press near
    // the top or bottom edge of a badge could land on the neighbouring row's line. The badges
    // themselves are 83.5x28 vectors drawn with ContentScale.Fit, so nothing is clipped by
    // moving the bounds in: the visible icon is 64x21.5dp, well inside the cell.
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(64.dp),
            contentAlignment = Alignment.Center
        ) {
            if (line.icon != null) {
                // Use official TCL icon
                Icon(
                    painter = drawableProvider.getPainter(line.icon),
                    contentDescription = lineLabelTemplate.replace("%s", line.name),
                    modifier = Modifier.size(64.dp),
                    tint = Color.Unspecified
                )
            } else {
                // Fallback if icon doesn't exist
                val backgroundColor = remember(line.name) {
                    Color(LineColorHelper.getColorForLineString(line.name))
                }
                // Contrast color painted on the fixed line-color badge — must NOT follow the theme.
                val textColor = if (line.alertKey == "T3") Color.Black else Color.White

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .background(backgroundColor)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = line.name,
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Alert badge (bottom-right corner) - Placed outside the clipped box
        if (alertSeverity != null) {
            AlertBadge(
                severity = alertSeverity,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (5).dp, y = (2).dp)
            )
        }
    }
}

/**
 * Composable for displaying an alert pastilla (color circle)
 */
@Composable
private fun AlertBadge(
    severity: TrafficAlertSeverity,
    modifier: Modifier = Modifier
) {
    val badgeColor = Color(severity.color)
    val badgeSize = 16.dp

    Box(
        modifier = modifier
            .size(badgeSize)
            .clip(CircleShape)
            .background(badgeColor),
        contentAlignment = Alignment.Center
    ) {
        if (severity == AlertSeverity.INFORMATION || severity == AlertSeverity.OTHER_EFFECT) {
            // Use a text-based "i" to avoid the double circle from Icons.Default.Info
            Text(
                text = "i",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Serif
                ),
                modifier = Modifier.padding(bottom = 1.dp)
            )
        } else {
            // PriorityHigh is a plain "!" without a surrounding circle
            Icon(
                imageVector = Icons.Default.PriorityHigh,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}
