package eu.dotshell.massilia.generic.ui.screens.plan

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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import eu.dotshell.massilia.generic.ui.theme.bottomSheetContainerColor
import eu.dotshell.massilia.generic.utils.search.SearchUtils
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.dotshell.massilia.generic.data.models.realtime.alerts.official.AlertSeverity
import eu.dotshell.massilia.generic.data.models.realtime.alerts.official.AlertSeverity as TrafficAlertSeverity
import eu.dotshell.massilia.generic.ui.viewmodel.TransportViewModelInterface
import eu.dotshell.massilia.generic.utils.LineColorHelper
import eu.dotshell.massilia.generic.utils.graphics.LineIconResolver
import eu.dotshell.massilia.platform.DrawableProvider
import eu.dotshell.massilia.platform.LocalPlatformContext
import eu.dotshell.massilia.platform.StringProvider

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
    // made the remember(allLines, drawableProvider) key below change each pass and re-ran the
    // (filter + bucket + natural sort) categorizeLines() on every keystroke.
    val drawableProvider = remember(platformContext) { DrawableProvider(platformContext) }
    val strings = StringProvider(platformContext)
    var searchQuery by remember { mutableStateOf("") }

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

    // Organize lines by category
    val categorizedLines = remember(allLines, drawableProvider) {
        val hasLineIcon: (String) -> Boolean = { lineName ->
            drawableProvider.hasDrawable(LineIconResolver.getDrawableNameForLineName(lineName))
        }
        categorizeLines(allLines, hasLineIcon).toList()
    }

    // Filtrer les lignes selon la recherche
    val filteredCategories = remember(categorizedLines, searchQuery) {
        if (searchQuery.isEmpty()) {
            categorizedLines
        } else {
            categorizedLines.mapNotNull { (category, lines) ->
                val filtered = lines.filter { SearchUtils.fuzzyContains(it, searchQuery) }
                if (filtered.isNotEmpty()) category to filtered else null
            }
        }
    }

    // Flatten: one header item + one flat "lines" item per category
    data class CategoryItem(val category: String)
    data class CategoryLinesItem(val category: String, val lines: List<String>)

    val flattenedItems = remember(filteredCategories) {
        buildList {
            filteredCategories.forEach { (category, lines) ->
                add(CategoryItem(category))
                add(CategoryLinesItem(category, lines))
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
        // List of lines by category
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .nestedScroll(nestedScrollConnection),
            userScrollEnabled = true
        ) {
            items(
                items = flattenedItems,
                key = { item ->
                    when (item) {
                        is CategoryItem -> "header_${item.category}"
                        is CategoryLinesItem -> "lines_${item.category}"
                        else -> item.hashCode()
                    }
                },
                contentType = { item ->
                    when (item) {
                        is CategoryItem -> "header"
                        is CategoryLinesItem -> "lines"
                        else -> null
                    }
                }
            ) { item ->
                when (item) {
                    is CategoryItem -> {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            val categoryText = when (item.category) {
                                "Métro" -> strings["category_metro"]
                                "Tramway" -> strings["category_tramway"]
                                "BHNS" -> strings["category_bhns"]
                                "Navette maritime" -> strings["category_navette_maritime"]
                                "Bus" -> strings["category_bus"]
                                "Bus de nuit" -> strings["category_nuit"]
                                "Scolaire" -> strings["category_scolaire"]
                                "Bus de remplacement" -> strings["category_remplacement"]
                                else -> item.category
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

                    is CategoryLinesItem -> {
                        // On calcule l'espacement pour justifier à gauche et à droite,
                        // et on applique le même espacement à la dernière ligne.
                        val itemWidth = 72.dp
                        androidx.compose.foundation.layout.BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 0.dp)
                                .padding(bottom = 8.dp)
                        ) {
                            val availableWidth = maxWidth
                            
                            // Nombre d'items par ligne (minimum 4)
                            val itemsPerRow = (availableWidth / itemWidth).toInt().coerceAtLeast(4)
                            
                            // Si itemsPerRow est 4 mais que ça ne rentre pas avec itemWidth=80dp,
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

                            val rows = item.lines.chunked(itemsPerRow)
                            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                rows.forEachIndexed { rowIndex, rowLines ->
                                    val isLastRow = rowIndex == rows.lastIndex
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = if (isLastRow || itemsPerRow == 1) {
                                            Arrangement.spacedBy(gap)
                                        } else {
                                            Arrangement.SpaceBetween
                                        }
                                    ) {
                                        rowLines.forEach { line ->
                                            val alertSeverity = lineAlerts[line.uppercase()]
                                            LineChip(
                                                lineName = line,
                                                onClick = { onLineClick(line) },
                                                alertSeverity = alertSeverity,
                                                drawableProvider = drawableProvider,
                                                modifier = Modifier.width(actualItemWidth)
                                            )
                                        }
                                    }
                                }
                            }
                        }
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

/**
 * Chip to show a line with the official RTM icon
 */
@Composable
private fun LineChip(
    lineName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    alertSeverity: TrafficAlertSeverity? = null,
    drawableProvider: DrawableProvider
) {
    val strings = StringProvider(LocalPlatformContext.current)
    val drawableName = remember(lineName) { LineIconResolver.getDrawableNameForLineName(lineName) }
    val hasIcon = remember(drawableName, drawableProvider) {
        drawableProvider.hasDrawable(drawableName)
    }

    Box(
        modifier = modifier
            .height(50.dp),
        contentAlignment = Alignment.Center
    ) {
        // Content Box with clipping and click
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (hasIcon) {
                // Use official RTM icon
                Icon(
                    painter = drawableProvider.getPainter(drawableName),
                    contentDescription = strings["line_label"].replace("%s", lineName),
                    modifier = Modifier.size(64.dp),
                    tint = Color.Unspecified
                )
            } else {
                // Fallback if icon doesn't exist
                val backgroundColor = Color(LineColorHelper.getColorForLineString(lineName))
                // Contrast color painted on the fixed line-color badge — must NOT follow the theme.
                val textColor = if (lineName.uppercase() == "T3") Color.Black else Color.White

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundColor)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = lineName,
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

/**
 * Organises lines by category and filters those which haven't icon.
 */
private fun categorizeLines(
    lines: List<String>,
    hasLineIcon: (String) -> Boolean
): Map<String, List<String>> {
    // Keep lines with icons, and keep the BM metro-replacement lines even
    // without a dedicated icon file (they fall back to the colored badge).
    val linesWithIcon = lines.filter { line ->
        val upperLine = line.uppercase()
        hasLineIcon(line) || upperLine.startsWith("BM")
    }

    val metros = mutableListOf<String>()
    val trams = mutableListOf<String>()
    val bhns = mutableListOf<String>()
    val navettesMaritimes = mutableListOf<String>()
    val nuit = mutableListOf<String>()
    val scolaires = mutableListOf<String>()
    val remplacement = mutableListOf<String>()
    val bus = mutableListOf<String>()

    linesWithIcon.forEach { line ->
        val upperLine = line.uppercase()
        when {
            upperLine.matches(Regex("^M[12]$")) -> metros.add(line)
            upperLine.matches(Regex("^T[1-3]$")) -> trams.add(line)
            upperLine.matches(Regex("^B[1-5]$")) -> bhns.add(line)
            upperLine.startsWith("NAV") || upperLine == "FERRY" -> navettesMaritimes.add(line)
            upperLine.matches(Regex("^N[12]$")) -> nuit.add(line)
            upperLine.matches(Regex("^S\\d{1,2}$")) -> scolaires.add(line)
            upperLine.startsWith("BM") -> remplacement.add(line)
            else -> bus.add(line)
        }
    }

    // Natural sort that correctly handles numbers in strings
    val naturalComparator = Comparator<String> { a, b ->
        val partsA = a.split(Regex("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)"))
        val partsB = b.split(Regex("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)"))
        val maxParts = maxOf(partsA.size, partsB.size)

        for (i in 0 until maxParts) {
            val partA = partsA.getOrNull(i)
            val partB = partsB.getOrNull(i)

            if (partA == null) return@Comparator -1 // a est plus court
            if (partB == null) return@Comparator 1  // b est plus court

            val numA = partA.toIntOrNull()
            val numB = partB.toIntOrNull()

            if (numA != null && numB != null) {
                val numCompare = numA.compareTo(numB)
                if (numCompare != 0) return@Comparator numCompare
            } else {
                val strCompare = partA.compareTo(partB)
                if (strCompare != 0) return@Comparator strCompare
            }
        }
        return@Comparator 0
    }

    fun naturalSort(lines: List<String>): List<String> {
        return lines.sortedWith(naturalComparator)
    }

    val result = mutableMapOf<String, List<String>>()

    if (metros.isNotEmpty()) result["Métro"] = naturalSort(metros)
    if (trams.isNotEmpty()) result["Tramway"] = naturalSort(trams)
    if (bhns.isNotEmpty()) result["BHNS"] = naturalSort(bhns)
    if (navettesMaritimes.isNotEmpty()) result["Navette maritime"] = naturalSort(navettesMaritimes)
    if (bus.isNotEmpty()) result["Bus"] = naturalSort(bus)
    if (nuit.isNotEmpty()) result["Bus de nuit"] = naturalSort(nuit)
    if (remplacement.isNotEmpty()) result["Bus de remplacement"] = naturalSort(remplacement)
    if (scolaires.isNotEmpty()) result["Scolaire"] = naturalSort(scolaires)

    return result
}
