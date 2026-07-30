package eu.dotshell.pelo.generic.ui.screens.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import eu.dotshell.pelo.generic.data.models.gtfs.LineStopInfo
import eu.dotshell.pelo.generic.ui.theme.bottomSheetContainerColor
import eu.dotshell.pelo.platform.LocalPlatformContext
import eu.dotshell.pelo.platform.StringProvider

/**
 * Fare-zone bracket rendered in a left gutter of the line-details stop timeline. Consecutive stops
 * sharing a fare zone form a run drawn as a `[`-shaped bracket, with a pill (the zone label) on the
 * run's middle row. Stops with no zone (null) leave a gap. Everything here is pure except the
 * gutter composable, so the run computation is unit-tested without Compose.
 */
enum class ZoneSegmentKind { NONE, SINGLE, START, MIDDLE, END }

data class ZoneSegment(
    val kind: ZoneSegmentKind,
    val zone: String? = null,
    val showLabel: Boolean = false
)

/**
 * Maps each stop (in display order) to its bracket segment. A run is a maximal block of consecutive
 * stops with the same non-null zone; its middle row carries the label. Null-zone stops are NONE.
 */
fun computeZoneSegments(stops: List<LineStopInfo>): List<ZoneSegment> {
    val out = MutableList(stops.size) { ZoneSegment(ZoneSegmentKind.NONE) }
    var i = 0
    while (i < stops.size) {
        val zone = stops[i].zone
        if (zone == null) { i++; continue }
        var end = i
        while (end + 1 < stops.size && stops[end + 1].zone == zone) end++
        val labelRow = i + (end - i) / 2
        for (j in i..end) {
            val kind = when {
                i == end -> ZoneSegmentKind.SINGLE
                j == i -> ZoneSegmentKind.START
                j == end -> ZoneSegmentKind.END
                else -> ZoneSegmentKind.MIDDLE
            }
            out[j] = ZoneSegment(kind, zone, showLabel = j == labelRow)
        }
        i = end + 1
    }
    return out
}

/** Compact label for the pill: "Zone Externe" → "EXT"; zones "1".."5" display raw. */
fun zoneShortLabel(zone: String): String =
    if (zone.equals("Zone Externe", ignoreCase = true)) "EXT" else zone.trim()

/**
 * Draws one stop row's slice of the zone bracket. The spine bleeds past the row bounds so runs join
 * seamlessly across the list's per-row vertical padding (same trick as the colored rail). The pill
 * is opaque (sheet-container background) so it visually interrupts the spine on the label row.
 */
@Composable
fun ZoneBracketGutter(segment: ZoneSegment, modifier: Modifier = Modifier) {
    if (segment.kind == ZoneSegmentKind.NONE) {
        Box(modifier)
        return
    }
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val container = bottomSheetContainerColor()
    val strings = StringProvider(LocalPlatformContext.current)

    Box(
        modifier = modifier.drawBehind {
            // Spine centered in the gutter so the (centered) pill masks it exactly; hooks point right
            // toward the stop dots. Both stay within the gutter so nothing overlaps the colored rail.
            val spineX = size.width / 2f
            val w = 2.dp.toPx()
            val hook = 6.dp.toPx()
            val bleed = 6.dp.toPx()
            val mid = size.height / 2f

            val top: Float
            val bottom: Float
            when (segment.kind) {
                ZoneSegmentKind.START -> { top = mid; bottom = size.height + bleed }
                ZoneSegmentKind.MIDDLE -> { top = -bleed; bottom = size.height + bleed }
                ZoneSegmentKind.END -> { top = -bleed; bottom = mid }
                ZoneSegmentKind.SINGLE -> { top = mid - 8.dp.toPx(); bottom = mid + 8.dp.toPx() }
                ZoneSegmentKind.NONE -> return@drawBehind
            }
            drawLine(color, Offset(spineX, top), Offset(spineX, bottom), w, StrokeCap.Round)

            // Horizontal hooks pointing at the stop dots that bound the run.
            val hookYs = when (segment.kind) {
                ZoneSegmentKind.START -> floatArrayOf(mid)
                ZoneSegmentKind.END -> floatArrayOf(mid)
                ZoneSegmentKind.SINGLE -> floatArrayOf(top, bottom)
                else -> floatArrayOf()
            }
            for (y in hookYs) {
                drawLine(color, Offset(spineX, y), Offset(spineX + hook, y), w, StrokeCap.Round)
            }
        },
        contentAlignment = Alignment.Center
    ) {
        if (segment.showLabel && segment.zone != null) {
            Text(
                text = strings["zone_label"].replace("%s", zoneShortLabel(segment.zone)).lowercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier
                    .wrapContentSize(unbounded = true)
                    .rotate(-90f)
                    .background(container)
                    .padding(start = 16.dp, end = 4.dp)
            )
        }
    }
}
