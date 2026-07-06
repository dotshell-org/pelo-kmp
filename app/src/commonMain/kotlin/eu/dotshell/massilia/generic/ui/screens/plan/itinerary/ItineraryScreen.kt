package eu.dotshell.massilia.generic.ui.screens.plan.itinerary

import kotlinx.datetime.isoDayNumber
 import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
 import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import eu.dotshell.massilia.platform.DrawableProvider
import eu.dotshell.massilia.platform.LocalPlatformContext
import eu.dotshell.massilia.platform.StringProvider
import eu.dotshell.massilia.generic.utils.graphics.LineIconResolver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import eu.dotshell.massilia.generic.data.repository.itinerary.itinerary.JourneyLeg
import eu.dotshell.massilia.generic.data.repository.itinerary.itinerary.JourneyResult
import eu.dotshell.massilia.generic.data.models.itinerary.TimeMode
import eu.dotshell.massilia.generic.ui.theme.AccentColor
import eu.dotshell.massilia.generic.ui.theme.Gray700
import eu.dotshell.massilia.generic.ui.theme.PrimaryColor
import eu.dotshell.massilia.generic.ui.theme.SecondaryColor
import eu.dotshell.massilia.generic.utils.LineColorHelper
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

private val FRENCH_MONTHS = arrayOf(
    "janv.", "févr.", "mars", "avr.", "mai", "juin",
    "juil.", "août", "sept.", "oct.", "nov.", "déc."
)

/**
 * Compact journey card showing key information in a condensed format
 * Similar to the bottom sheet header in map view
 */
@Composable
fun CompactJourneyCard(
    journey: JourneyResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    useLightColors: Boolean = false,
    showAvoidedAlertsBadge: Boolean = false,
    avoidedAlertsLabel: String? = null
) {
    val drawableProvider = DrawableProvider(LocalPlatformContext.current)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val primaryTextColor = remember(useLightColors) {
        if (useLightColors) PrimaryColor else SecondaryColor
    }
    val secondaryTextColor = remember(useLightColors) {
        if (useLightColors) Color(0xFF4B5563) else SecondaryColor.copy(alpha = 0.7f)
    }
    val chipBackgroundColor = remember(useLightColors) {
        if (useLightColors) Color(0xFFF3F4F6) else SecondaryColor.copy(alpha = 0.15f)
    }
    val baseBackgroundColor = remember(useLightColors) {
        if (useLightColors) Color(0xFFF9FAFB) else SecondaryColor.copy(alpha = 0.1f)
    }
    val pressedColor = remember(useLightColors) {
        if (useLightColors) Color(0xFFF3F4F6) else SecondaryColor.copy(alpha = 0.16f)
    }
    val backgroundColor by animateColorAsState(
        targetValue = if (isPressed) pressedColor else baseBackgroundColor,
        label = "compact_journey_press"
    )

    val formattedDuration = remember(journey.durationMinutes) {
        if (journey.durationMinutes < 60) {
            "${journey.durationMinutes} min"
        } else {
            "${journey.durationMinutes / 60}h${
                (journey.durationMinutes % 60).toString().padStart(2, '0')
            }"
        }
    }

    Card(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (showAvoidedAlertsBadge) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = primaryTextColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = avoidedAlertsLabel ?: "Alertes utilisateurs evitees",
                        style = MaterialTheme.typography.labelSmall,
                        color = primaryTextColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = journey.formatDepartureTime(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor
                    )
                    Text(
                        text = " -> ",
                        style = MaterialTheme.typography.titleMedium,
                        color = secondaryTextColor
                    )
                    Text(
                        text = journey.formatArrivalTime(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor
                    )
                }

                Box(
                    modifier = Modifier
                        .background(
                            color = chipBackgroundColor,
                            shape = RoundedCornerShape(8.dp)
                        )
                ) {
                    Text(
                        text = formattedDuration,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = primaryTextColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val nonWalkingLegs = remember(journey.legs) {
                    journey.legs.filterNot { it.isWalking }
                }
                val arrowTint = remember(useLightColors) {
                    if (useLightColors) Color(0xFF6B7280) else SecondaryColor.copy(alpha = 0.5f)
                }

                nonWalkingLegs.forEachIndexed { index, leg ->
                    val drawableName = LineIconResolver.getDrawableNameForLineName(leg.routeName ?: "")

                    if (drawableProvider.hasDrawable(drawableName)) {
                        Image(
                            painter = drawableProvider.getPainter(drawableName),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                    } else {
                        val legColor = remember(leg.routeName) {
                            Color(LineColorHelper.getColorForLineString(leg.routeName ?: ""))
                        }
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(legColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (leg.routeName ?: "?").take(3),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = SecondaryColor
                            )
                        }
                    }

                    if (index < nonWalkingLegs.size - 1) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = arrowTint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JourneyLegItem(
    leg: JourneyLeg,
    isFirst: Boolean,
    isLast: Boolean,
    useLightColors: Boolean
) {
    val drawableProvider = DrawableProvider(LocalPlatformContext.current)
    val strings = StringProvider(LocalPlatformContext.current)
    val lineColor = remember(leg.isWalking, leg.routeName) {
        if (leg.isWalking) Gray700 else Color(
            LineColorHelper.getColorForLineString(leg.routeName ?: "")
        )
    }
    val primaryTextColor = remember(useLightColors) {
        if (useLightColors) PrimaryColor else SecondaryColor
    }
    val secondaryTextColor = remember(useLightColors) {
        if (useLightColors) Color(0xFF4B5563) else SecondaryColor.copy(alpha = 0.7f)
    }
    val tertiaryTextColor = remember(useLightColors) {
        if (useLightColors) Color(0xFF6B7280) else SecondaryColor.copy(alpha = 0.6f)
    }

    // State for expanding intermediate stops
    var isExpanded by remember { mutableStateOf(false) }
    val hasIntermediateStops = !leg.isWalking && leg.intermediateStops.isNotEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxHeight()
            ) {
                if (!isFirst) {
                    Box(modifier = Modifier
                        .width(3.dp)
                        .height(8.dp)
                        .background(lineColor))
                }

                if (leg.isWalking) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                        contentDescription = null,
                        tint = Gray700,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    val drawableName = LineIconResolver.getDrawableNameForLineName(leg.routeName ?: "")

                    if (drawableProvider.hasDrawable(drawableName)) {
                        Image(
                            painter = drawableProvider.getPainter(drawableName),
                            contentDescription = null,
                            modifier = Modifier.size(30.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(lineColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = leg.routeName ?: "?",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = SecondaryColor
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .weight(1f)
                        .background(lineColor)
                )

                if (isLast) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .border(3.dp, lineColor, CircleShape)
                            .background(if (useLightColors) SecondaryColor else PrimaryColor)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        ) {
            Text(
                text = leg.fromStopName,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                color = primaryTextColor
            )
            Text(
                text = leg.formatDepartureTime(),
                color = secondaryTextColor,
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (leg.isWalking) strings["walking_duration"].replace("%s", leg.durationMinutes.toString()) else strings["direction_to"].replace("%s", leg.direction ?: leg.toStopName),
                color = secondaryTextColor,
                style = MaterialTheme.typography.bodySmall
            )

            // Expandable intermediate stops section
            if (hasIntermediateStops) {
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier
                        .clickable { isExpanded = !isExpanded }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = if (isExpanded) "Réduire" else "Développer",
                        tint = secondaryTextColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = strings["intermediate_stops"]
                            .replace("%1\$s", leg.intermediateStops.size.toString())
                            .replace("%2\$s", if (leg.intermediateStops.size > 1) "s" else ""),
                        color = secondaryTextColor,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                // Expanded intermediate stops list
                if (isExpanded) {
                    Column(
                        modifier = Modifier.padding(start = 20.dp, top = 4.dp)
                    ) {
                        leg.intermediateStops.forEach { stop ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stop.stopName,
                                    color = tertiaryTextColor,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = stop.formatArrivalTime(),
                                    color = if (useLightColors) Color(0xFF9CA3AF) else SecondaryColor.copy(
                                        alpha = 0.5f
                                    ),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            if (isLast) {
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = leg.toStopName,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = primaryTextColor
                )
                Text(
                    text = leg.formatArrivalTime(),
                    color = secondaryTextColor,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Sheet content for journey details shown in BottomSheetScaffold
 * Shows a compact horizontal view of the journey with line icons
 * Expands to show full itinerary details when sheet is expanded
 */
@Composable
fun JourneyDetailsSheetContent(
    journey: JourneyResult,
    isExpanded: Boolean,
    onStartNavigation: () -> Unit = {},
    modifier: Modifier = Modifier,
    useLightColors: Boolean = false,
    scrollAllContent: Boolean = false
) {
    val strings = StringProvider(LocalPlatformContext.current)
    val primaryTextColor = if (useLightColors) PrimaryColor else SecondaryColor
    val secondaryTextColor =
        if (useLightColors) Color(0xFF4B5563) else SecondaryColor.copy(alpha = 0.7f)
    val chipBackgroundColor =
        if (useLightColors) Color(0xFFF3F4F6) else SecondaryColor.copy(alpha = 0.15f)
    val bottomBarHeight = 72.dp

    // Memoize formatted duration to avoid recalculation on recomposition
    val formattedDuration by remember(journey.durationMinutes) {
        derivedStateOf {
            if (journey.durationMinutes < 60) {
                "${journey.durationMinutes} min"
            } else {
                "${journey.durationMinutes / 60}h${
                    (journey.durationMinutes % 60).toString().padStart(2, '0')
                }min"
            }
        }
    }

    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 16.dp)
                .verticalScroll(
                    state = scrollState,
                    enabled = isExpanded
                )
        ) {
            if (scrollAllContent) {
                Spacer(modifier = Modifier.height(16.dp))
            }
            journey.legs.forEachIndexed { index, leg ->
                key("${leg.fromStopId}_${leg.departureTime}") {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        JourneyLegItem(
                            leg = leg,
                            isFirst = index == 0,
                            isLast = index == journey.legs.size - 1,
                            useLightColors = useLightColors
                        )
                    }
                }
            }
            if (scrollAllContent) {
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Spacer(modifier = Modifier.height(80.dp))
            }
            Spacer(modifier = Modifier.height(bottomBarHeight))
        }

        val sheetBgColor = if (useLightColors) SecondaryColor else PrimaryColor
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            sheetBgColor.copy(alpha = 0.85f),
                            sheetBgColor,
                            sheetBgColor
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // "Démarrer" button on the left - even closer to edge
                Box(
                    modifier = Modifier
                        .background(
                            color = PrimaryColor,
                            shape = RoundedCornerShape(24.dp)
                        )
                        .clickable { onStartNavigation() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Navigation,
                            contentDescription = null,
                            tint = SecondaryColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = strings["start"],
                            color = SecondaryColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                // Time info on the right - closer to edge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = journey.formatDepartureTime(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = primaryTextColor
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowRight,
                            contentDescription = null,
                            tint = Gray700
                        )
                        Text(
                            text = journey.formatArrivalTime(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = primaryTextColor
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(
                                color = chipBackgroundColor,
                                shape = RoundedCornerShape(8.dp)
                            )
                    ) {
                        Text(
                            text = formattedDuration,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = primaryTextColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Row for selecting departure/arrival time mode and picking a date/time
 */
@Composable
fun TimeSelectionRow(
    timeMode: TimeMode,
    selectedTimeSeconds: Int?,
    selectedDate: LocalDate?,
    onTimeModeChange: (TimeMode) -> Unit,
    onTimeClick: () -> Unit,
    onDateClick: () -> Unit,
    onClearDateTime: () -> Unit,
    useLightColors: Boolean = false
) {
    val strings = StringProvider(LocalPlatformContext.current)
    val containerColor = remember(useLightColors) {
        if (useLightColors) Color(0xFFF9FAFB) else SecondaryColor.copy(alpha = 0.1f)
    }
    val selectedModeBackground = remember(useLightColors) {
        if (useLightColors) Color(0xFFE5E7EB) else SecondaryColor.copy(alpha = 0.2f)
    }
    val pickerBackground = remember(useLightColors) {
        if (useLightColors) Color(0xFFF3F4F6) else SecondaryColor.copy(alpha = 0.15f)
    }
    val primaryTextColor = remember(useLightColors) {
        if (useLightColors) PrimaryColor else SecondaryColor
    }
    val secondaryTextColor = remember(useLightColors) {
        if (useLightColors) Color(0xFF4B5563) else SecondaryColor.copy(alpha = 0.6f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = containerColor,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        // First row: Mode toggle and clear button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Mode toggle buttons
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Departure mode button
                Box(
                    modifier = Modifier
                        .background(
                            color = if (timeMode == TimeMode.DEPARTURE) selectedModeBackground else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onTimeModeChange(TimeMode.DEPARTURE) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = strings["departure"],
                        color = if (timeMode == TimeMode.DEPARTURE) primaryTextColor else secondaryTextColor,
                        fontWeight = if (timeMode == TimeMode.DEPARTURE) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Arrival mode button
                Box(
                    modifier = Modifier
                        .background(
                            color = if (timeMode == TimeMode.ARRIVAL) selectedModeBackground else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onTimeModeChange(TimeMode.ARRIVAL) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = strings["arrival"],
                        color = if (timeMode == TimeMode.ARRIVAL) primaryTextColor else secondaryTextColor,
                        fontWeight = if (timeMode == TimeMode.ARRIVAL) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                }
            }

            // Clear button (only show if date or time is set)
            if (selectedTimeSeconds != null || selectedDate != null) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = strings["reset"],
                    tint = secondaryTextColor,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onClearDateTime() }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Second row: Date and time pickers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Date picker
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = pickerBackground,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onDateClick() }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = primaryTextColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatDateDisplay(selectedDate),
                    color = primaryTextColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Time picker
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = pickerBackground,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onTimeClick() }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = primaryTextColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (selectedTimeSeconds != null) {
                        formatTimeSeconds(selectedTimeSeconds)
                    } else {
                        "Maintenant"
                    },
                    color = primaryTextColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Simple time picker dialog using wheel-style pickers
 * Minutes are rounded to 5-minute intervals
 */
@Composable
fun TimePickerDialog(
    initialTimeSeconds: Int,
    onTimeSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = StringProvider(LocalPlatformContext.current)
    val initialHour = (initialTimeSeconds / 3600) % 24
    // Round initial minute to nearest 5 minutes
    val initialMinute = ((initialTimeSeconds % 3600) / 60 / 5) * 5

    var selectedHour by remember { mutableIntStateOf(initialHour) }
    var selectedMinute by remember { mutableIntStateOf(initialMinute) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Time display
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Hour picker
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { selectedHour = (selectedHour + 1) % 24 }) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = strings["increase_hour"],
                                tint = SecondaryColor
                            )
                        }
                        Text(
                            text =     selectedHour.toString().padStart(2, '0'),
                            color = SecondaryColor,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = {
                            selectedHour = if (selectedHour == 0) 23 else selectedHour - 1
                        }) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = strings["decrease_hour"],
                                tint = SecondaryColor
                            )
                        }
                    }

                    Text(
                        text = ":",
                        color = SecondaryColor,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    // Minute picker
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { selectedMinute = (selectedMinute + 5) % 60 }) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = strings["increase_minutes"],
                                tint = SecondaryColor
                            )
                        }
                        Text(
                            text =     selectedMinute.toString().padStart(2, '0'),
                            color = SecondaryColor,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = {
                            selectedMinute = if (selectedMinute < 5) 55 else selectedMinute - 5
                        }) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = strings["decrease_minutes"],
                                tint = SecondaryColor
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = strings["cancel"],
                        color = SecondaryColor.copy(alpha = 0.7f),
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                color = AccentColor,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                onTimeSelected(selectedHour * 3600 + selectedMinute * 60)
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = strings["confirm"],
                            color = SecondaryColor
                        )
                    }
                }
            }
        }
    }
}

/**
 * Format time in seconds to HH:mm string
 */
private fun formatTimeSeconds(seconds: Int): String {
    val hours = (seconds / 3600) % 24
    val minutes = (seconds % 3600) / 60
    return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"
}

/**
 * Format date for display
 * Shows "Aujourd'hui" for today, "Demain" for tomorrow, or the date
 */
private fun formatDateDisplay(date: LocalDate?): String {
    if (date == null) return "Aujourd'hui"

    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val tomorrow = today.plus(1, DateTimeUnit.DAY)

    return when (date) {
        today -> "Aujourd'hui"
        tomorrow -> "Demain"
        else -> "${date.dayOfMonth} ${FRENCH_MONTHS[date.monthNumber - 1]}"
    }
}

/**
 * Date picker dialog for selecting a journey date
 * Allows selecting dates with month navigation
 */
@Composable
fun DatePickerDialog(
    initialDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = StringProvider(LocalPlatformContext.current)
    var selectedDate by remember { mutableStateOf(initialDate) }
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    // Current displayed month
    var displayedMonth by remember {
        mutableStateOf(LocalDate(initialDate.year, initialDate.month, 1))
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Month navigation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous month button
                    val todayFirstOfMonth = LocalDate(today.year, today.month, 1)
                    IconButton(
                        onClick = {
                            val prevMonth = displayedMonth.plus(-1, DateTimeUnit.MONTH)
                            if (!(prevMonth.plus(1, DateTimeUnit.MONTH) < todayFirstOfMonth)) {
                                displayedMonth = prevMonth
                            }
                        },
                        enabled = displayedMonth > todayFirstOfMonth
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = strings["previous_month"],
                            tint = if (displayedMonth > todayFirstOfMonth)
                                SecondaryColor else SecondaryColor.copy(alpha = 0.3f),
                            modifier = Modifier.rotate(-90f)
                        )
                    }

                    Text(
                        text = "${FRENCH_MONTHS[displayedMonth.monthNumber - 1]} ${displayedMonth.year}",
                        color = SecondaryColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )

                    // Next month button
                    IconButton(onClick = { displayedMonth = displayedMonth.plus(1, DateTimeUnit.MONTH) }) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = strings["next_month"],
                            tint = SecondaryColor,
                            modifier = Modifier.rotate(90f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Days of week header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("L", "M", "M", "J", "V", "S", "D").forEach { day ->
                        Text(
                            text = day,
                            color = SecondaryColor.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.width(36.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Calendar grid
                val firstDayOfMonth = displayedMonth
                val lastDayOfMonth = displayedMonth.plus(1, DateTimeUnit.MONTH).plus(-1, DateTimeUnit.DAY)
                // Monday = 1, Sunday = 7, we want Monday as first column (index 0)
                val firstDayOfWeek = (firstDayOfMonth.dayOfWeek.isoDayNumber - 1) // 0 = Monday
                val daysInMonth = lastDayOfMonth.dayOfMonth

                // Generate calendar rows (max 6 weeks)
                val calendarDays = mutableListOf<LocalDate?>()
                // Add empty cells for days before the first of month
                repeat(firstDayOfWeek) { calendarDays.add(null) }
                // Add all days of the month
                for (day in 1..daysInMonth) {
                    calendarDays.add(LocalDate(displayedMonth.year, displayedMonth.month, day))
                }
                // Fill remaining cells to complete the last row
                while (calendarDays.size % 7 != 0) {
                    calendarDays.add(null)
                }

                calendarDays.chunked(7).forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        week.forEach { date ->
                            if (date != null) {
                                val isSelected = date == selectedDate
                                val isToday = date == today
                                val isSelectable = date >= today

                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(
                                            color = when {
                                                isSelected -> AccentColor
                                                isToday -> SecondaryColor.copy(alpha = 0.15f)
                                                else -> Color.Transparent
                                            },
                                            shape = CircleShape
                                        )
                                        .then(
                                            if (isSelectable) Modifier.clickable {
                                                selectedDate = date
                                            }
                                            else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = date.dayOfMonth.toString(),
                                        color = when {
                                            !isSelectable -> SecondaryColor.copy(alpha = 0.3f)
                                            isSelected -> SecondaryColor
                                            else -> SecondaryColor.copy(alpha = 0.9f)
                                        },
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            } else {
                                // Empty cell
                                Box(modifier = Modifier.size(36.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = strings["cancel"],
                        color = SecondaryColor.copy(alpha = 0.7f),
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                color = AccentColor,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                onDateSelected(selectedDate)
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = strings["confirm"],
                            color = SecondaryColor
                        )
                    }
                }
            }
        }
    }
}
