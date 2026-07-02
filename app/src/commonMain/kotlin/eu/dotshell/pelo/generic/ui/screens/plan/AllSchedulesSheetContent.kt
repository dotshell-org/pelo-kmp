package eu.dotshell.pelo.generic.ui.screens.plan

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.dotshell.pelo.generic.data.models.ui.AllSchedulesInfo
import eu.dotshell.pelo.generic.ui.theme.Orange500
import eu.dotshell.pelo.generic.ui.theme.AccentColor
import eu.dotshell.pelo.generic.utils.LineColorHelper
import eu.dotshell.pelo.generic.utils.graphics.LineIconResolver
import eu.dotshell.pelo.platform.DrawableProvider
import eu.dotshell.pelo.platform.LocalPlatformContext
import eu.dotshell.pelo.platform.StringProvider
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private fun getLineColor(lineName: String): Color {
    return Color(LineColorHelper.getColorForLineString(lineName))
}

private fun getAllDayScheduleColor(hour: String, minute: String, defaultColor: Color): Color {
    val hourInt = hour.toIntOrNull() ?: return defaultColor
    val minuteInt = minute.toIntOrNull() ?: return defaultColor
    if (hourInt !in 0..23 || minuteInt !in 0..59) return defaultColor

    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val nowMinutes = now.hour * 60 + now.minute
    val scheduleMinutes = hourInt * 60 + minuteInt
    val diffMinutes = scheduleMinutes - nowMinutes

    return when {
        diffMinutes < 0 -> Color.Gray
        diffMinutes < 2 -> AccentColor
        diffMinutes < 15 -> Orange500
        else -> defaultColor
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AllSchedulesSheetContent(
    allSchedulesInfo: AllSchedulesInfo,
    stationName: String,
    selectedDirection: Int,
    availableDirections: List<Int>,
    headsigns: Map<Int, String>,
    onDirectionChange: (Int) -> Unit,
    onBack: () -> Unit
) {
    val drawableProvider = DrawableProvider(LocalPlatformContext.current)
    val strings = StringProvider(LocalPlatformContext.current)
    val drawableName = remember(allSchedulesInfo.lineName) {
        LineIconResolver.getDrawableNameForLineName(allSchedulesInfo.lineName)
    }
    val hasDrawable = remember(drawableName) {
        drawableName.isNotBlank() && drawableProvider.hasDrawable(drawableName)
    }

    val groupedSchedules = remember(allSchedulesInfo.schedules) {
        allSchedulesInfo.schedules
            .map { it.split(":") }
            .filter { it.size == 2 }
            .groupBy({ it[0] }, { it[1] })
            .toList()
            .sortedBy { it.first }
            .toMap()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = strings["back"],
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))

            if (hasDrawable) {
                Image(
                    painter = drawableProvider.getPainter(drawableName),
                    contentDescription = strings["line_label"].replace("%s", allSchedulesInfo.lineName),
                    modifier = Modifier.size(50.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(getLineColor(allSchedulesInfo.lineName)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = allSchedulesInfo.lineName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        // Contrast on the fixed line-color badge — not theme-driven.
                        color = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stationName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (availableDirections.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableDirections.forEach { directionId ->
                    val headsign = headsigns[directionId] ?: "Direction ${directionId + 1}"
                    Button(
                        onClick = { onDirectionChange(directionId) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedDirection == directionId) getLineColor(
                                allSchedulesInfo.lineName
                            ) else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (selectedDirection == directionId) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Text(
                            text = headsign,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        val list = groupedSchedules.entries.toList()

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            itemsIndexed(list, key = { _, (hour, _) -> hour }) { index, (hour, minutesList) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "${hour}h",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = getLineColor(allSchedulesInfo.lineName),
                        modifier = Modifier.width(50.dp)
                    )

                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        minutesList.forEach { minute ->
                            val minuteColor = getAllDayScheduleColor(hour, minute, MaterialTheme.colorScheme.onSurface)
                            Text(
                                text = minute,
                                style = MaterialTheme.typography.bodyLarge,
                                color = minuteColor,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
                if (index < list.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                } else {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}
