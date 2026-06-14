package com.pelotcl.app.generic.ui.screens.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.pelotcl.app.generic.utils.search.SearchUtils
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
import com.pelotcl.app.generic.data.models.realtime.alerts.official.AlertSeverity
import com.pelotcl.app.generic.ui.theme.PrimaryColor
import com.pelotcl.app.generic.ui.theme.SecondaryColor
import com.pelotcl.app.generic.data.models.realtime.alerts.official.AlertSeverity as TrafficAlertSeverity
import com.pelotcl.app.generic.ui.viewmodel.TransportViewModelInterface
import com.pelotcl.app.generic.utils.LineColorHelper
import com.pelotcl.app.generic.utils.graphics.LineIconResolver
import com.pelotcl.app.platform.DrawableProvider
import com.pelotcl.app.platform.LocalPlatformContext

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
    val drawableProvider = DrawableProvider(LocalPlatformContext.current)
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

    // Flatten the structure for LazyColumn items
    data class CategoryItem(val category: String)
    data class LineRowItem(val category: String, val rowIndex: Int, val lines: List<String>)

    val flattenedItems = remember(filteredCategories) {
        buildList {
            filteredCategories.forEach { (category, lines) ->
                // Add category header
                add(CategoryItem(category))

                // Add line rows (chunked by 4)
                lines.chunked(4).forEachIndexed { rowIndex, rowLines ->
                    add(LineRowItem(category, rowIndex, rowLines))
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .background(SecondaryColor, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
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
                        is LineRowItem -> "${item.category}_row_${item.rowIndex}"
                        else -> item.hashCode()
                    }
                },
                contentType = { item ->
                    when (item) {
                        is CategoryItem -> "header"
                        is LineRowItem -> "linerow"
                        else -> null
                    }
                }
            ) { item ->
                when (item) {
                    is CategoryItem -> {
                        // Category header
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = item.category,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 8.dp)
                            )
                        }
                    }

                    is LineRowItem -> {
                        // Row of line chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Up to 4 chips per row
                            item.lines.forEach { line ->
                                val alertSeverity = lineAlerts[line.uppercase()]
                                LineChip(
                                    lineName = line,
                                    onClick = { onLineClick(line) },
                                    modifier = Modifier.weight(1f),
                                    alertSeverity = alertSeverity,
                                    drawableProvider = drawableProvider
                                )
                            }
                            // Fill remaining columns for alignment consistency
                            repeat(4 - item.lines.size) {
                                Spacer(modifier = Modifier.weight(1f))
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
                            text = "Aucune ligne trouvée",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

/**
 * Chip to show a line with the official TCL icon
 */
@Composable
private fun LineChip(
    lineName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    alertSeverity: TrafficAlertSeverity? = null,
    drawableProvider: DrawableProvider
) {
    val drawableName = remember(lineName) { LineIconResolver.getDrawableNameForLineName(lineName) }
    val hasIcon = remember(drawableName, drawableProvider) {
        drawableProvider.hasDrawable(drawableName)
    }

    Box(
        modifier = modifier
            .height(48.dp)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        // Content Box with clipping and click
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (hasIcon) {
                // Use official TCL icon
                Icon(
                    painter = drawableProvider.getPainter(drawableName),
                    contentDescription = "Ligne $lineName",
                    modifier = Modifier.size(80.dp),
                    tint = Color.Unspecified
                )
            } else {
                // Fallback if icon doesn't exist
                val backgroundColor = Color(LineColorHelper.getColorForLineString(lineName))
                val textColor = if (lineName.uppercase() == "T3") PrimaryColor else SecondaryColor

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
                color = SecondaryColor,
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
                tint = SecondaryColor,
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
    // Keep lines with icons, and keep NAVI* even without a dedicated icon file.
    val linesWithIcon = lines.filter { line ->
        val upperLine = line.uppercase()
        hasLineIcon(line) || upperLine.startsWith("NAVI")
    }

    val metros = mutableListOf<String>()
    val trams = mutableListOf<String>()
    val funiculaires = mutableListOf<String>()
    val chrono = mutableListOf<String>()
    val pleineLune = mutableListOf<String>()
    val jd = mutableListOf<String>()
    val navigone = mutableListOf<String>()
    val gareExpress = mutableListOf<String>()
    val soyeuses = mutableListOf<String>()
    val navettes = mutableListOf<String>()
    val zi = mutableListOf<String>()
    val carsDuRhone = mutableListOf<String>()
    val bus = mutableListOf<String>()

    linesWithIcon.forEach { line ->
        val upperLine = line.uppercase()
        when {
            upperLine in setOf("A", "B", "C", "D") -> metros.add(line)
            upperLine.startsWith("F") && (upperLine == "F1" || upperLine == "F2") -> funiculaires.add(
                line
            )

            upperLine.startsWith("TB") || upperLine == "RX" || upperLine.contains("RHON") -> trams.add(
                line
            )

            upperLine.startsWith("T") && upperLine.length == 2 -> trams.add(line)
            upperLine.startsWith("C") && upperLine.length >= 2 -> chrono.add(line)
            upperLine.startsWith("PL") -> pleineLune.add(line)
            upperLine.startsWith("JD") -> jd.add(line)
            upperLine.startsWith("NAVI") -> navigone.add(line)
            upperLine.startsWith("GE") -> gareExpress.add(line)
            upperLine.startsWith("S") -> soyeuses.add(line)
            upperLine.startsWith("ZI") -> zi.add(line)
            upperLine.startsWith("N") -> navettes.add(line)
            upperLine.length >= 3 && upperLine != "128" && upperLine.all { it.isDigit() } -> carsDuRhone.add(
                line
            )

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
    if (funiculaires.isNotEmpty()) result["Funiculaire"] = naturalSort(funiculaires)
    if (trams.isNotEmpty()) result["Tramway"] = naturalSort(trams)
    if (navigone.isNotEmpty()) result["Navigone"] = naturalSort(navigone)
    if (chrono.isNotEmpty()) result["Chrono"] = naturalSort(chrono)
    if (pleineLune.isNotEmpty()) result["Pleine Lune"] = naturalSort(pleineLune)
    if (gareExpress.isNotEmpty()) result["Gare Express"] = naturalSort(gareExpress)
    if (navettes.isNotEmpty()) result["Navette"] = naturalSort(navettes)
    if (soyeuses.isNotEmpty()) result["Soyeuse"] = naturalSort(soyeuses)
    if (zi.isNotEmpty()) result["Zone Industrielle"] = naturalSort(zi)
    if (bus.isNotEmpty()) result["Bus"] = naturalSort(bus)
    if (carsDuRhone.isNotEmpty()) result["Cars du Rhône TCL unifié"] = naturalSort(carsDuRhone)
    if (jd.isNotEmpty()) result["Junior Direct"] = naturalSort(jd)

    return result
}
