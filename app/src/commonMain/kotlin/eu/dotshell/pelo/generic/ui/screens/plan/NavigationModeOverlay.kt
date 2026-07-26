package eu.dotshell.pelo.generic.ui.screens.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.dotshell.pelo.generic.service.TransportServiceProvider
import eu.dotshell.pelo.platform.DrawableProvider
import eu.dotshell.pelo.platform.LocalPlatformContext
import eu.dotshell.pelo.platform.StringProvider

/** Amber used for the "report an alert" affordance, matching the map FAB. */
private val AlertAmber = Color(0xFFFACC15)

@Composable
fun NavigationModeOverlay(
    state: NavigationModeUiState,
    isFollowingUser: Boolean,
    onRecenter: () -> Unit,
    onStop: () -> Unit,
    onReportAlert: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = StringProvider(LocalPlatformContext.current)
    var showStopConfirmation by remember { mutableStateOf(false) }

    Box(modifier) {
        NavigationInstructionCard(
            state = state,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.End
        ) {
            // Recentring is the way back from a map the traveller panned away — without it,
            // making the map interactive would be a one-way door.
            if (!isFollowingUser) {
                Box(
                    modifier = Modifier
                        .padding(end = 16.dp, bottom = 12.dp)
                        .size(52.dp)
                        .shadow(6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable(onClick = onRecenter),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.MyLocation,
                        contentDescription = strings["nav_recenter"],
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            NavigationBottomBar(
                state = state,
                onStopRequested = {
                    if (state.isArrived) onStop() else showStopConfirmation = true
                },
                onReportAlert = onReportAlert,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showStopConfirmation) {
        AlertDialog(
            onDismissRequest = { showStopConfirmation = false },
            title = { Text(strings["nav_stop_confirm_title"]) },
            text = { Text(strings["nav_stop_confirm_message"]) },
            confirmButton = {
                TextButton(onClick = {
                    showStopConfirmation = false
                    onStop()
                }) {
                    Text(strings["nav_stop_confirm_action"])
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirmation = false }) {
                    Text(strings["cancel"])
                }
            }
        )
    }
}

@Composable
private fun NavigationInstructionCard(
    state: NavigationModeUiState,
    modifier: Modifier = Modifier
) {
    val strings = StringProvider(LocalPlatformContext.current)
    val hasUpNext = state.upcomingLeg != null && !state.isArrived
    val topShape = if (hasUpNext) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
    } else {
        RoundedCornerShape(20.dp)
    }

    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(6.dp, topShape)
                .clip(topShape)
                .background(MaterialTheme.colorScheme.surface)
                // A minimum, not a fixed height: a long stop name at a large system font size
                // used to be clipped by the card rather than growing it.
                .heightIn(min = 116.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            NavigationStepBadges(state)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                state.direction?.let { direction ->
                    Text(
                        text = strings.format("nav_direction", direction),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = state.instruction.displayText(),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    // Guidance that changes silently is useless to a screen-reader user.
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
                NavigationStatusLine(state)
            }
        }

        state.upcomingLeg?.takeIf { !state.isArrived }?.let { upcoming ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = strings["next_up"],
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                NavigationLineIcon(lineName = upcoming.routeName.orEmpty(), size = 30.dp)
            }
        }
    }
}

/**
 * The badge stack on the left of the card: one badge normally, two with an arrow between them
 * when the step is a transition (walk to a line, or change from one line to another).
 */
@Composable
private fun NavigationStepBadges(state: NavigationModeUiState) {
    val isWalking = state.currentLeg?.isWalking == true

    when {
        state.isArrived -> Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp)
        )

        state.shouldChangeLine && state.previousLeg != null && state.displayedLeg != null ->
            BadgeTransition(
                top = { NavigationLineIcon(state.previousLeg.routeName.orEmpty(), size = 34.dp) },
                bottom = { NavigationLineIcon(state.displayedLeg.routeName.orEmpty(), size = 34.dp) }
            )

        isWalking && state.displayedLeg != null -> BadgeTransition(
            top = { NavigationWalkIcon(size = 34.dp) },
            bottom = { NavigationLineIcon(state.displayedLeg.routeName.orEmpty(), size = 34.dp) }
        )

        isWalking || state.displayedLeg == null -> NavigationWalkIcon(size = 44.dp)

        else -> NavigationLineIcon(state.displayedLeg.routeName.orEmpty(), size = 44.dp)
    }
}

@Composable
private fun BadgeTransition(
    top: @Composable () -> Unit,
    bottom: @Composable () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        top()
        Icon(
            imageVector = Icons.Filled.ArrowDownward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        bottom()
    }
}

/** Tells the traveller when the guidance is degraded, instead of silently showing stale steps. */
@Composable
private fun NavigationStatusLine(state: NavigationModeUiState) {
    val strings = StringProvider(LocalPlatformContext.current)
    val message: String
    val color: Color
    when {
        state.isArrived -> return
        state.isOffRoute -> {
            message = strings["nav_off_route"]
            color = MaterialTheme.colorScheme.error
        }
        state.isDeadReckoning -> {
            message = strings["nav_dead_reckoning"]
            color = MaterialTheme.colorScheme.onSurfaceVariant
        }
        else -> return
    }
    Text(
        text = message,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun NavigationBottomBar(
    state: NavigationModeUiState,
    onStopRequested: () -> Unit,
    onReportAlert: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = StringProvider(LocalPlatformContext.current)
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

    Box(
        modifier = modifier
            .shadow(8.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            // Inset the content, not the surface: the panel still paints behind the gesture bar
            // while nothing it contains ends up underneath it.
            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            JourneyProgressBar(state.progressFraction)

            if (state.isArrived) {
                Button(
                    onClick = onStopRequested,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Text(strings["nav_finish"])
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CircularOverlayButton(
                        icon = Icons.Filled.Close,
                        contentDescription = strings["nav_stop_navigation"],
                        tint = MaterialTheme.colorScheme.onSurface,
                        onClick = onStopRequested
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = formatRemainingTime(state.remainingSeconds),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = strings.format("nav_arrival_at", state.arrivalTimeText),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    val realtimeConfig = remember { TransportServiceProvider.getRealtimeConfig() }
                    if (realtimeConfig.userStopAlertsEnabled) {
                        val drawableProvider = DrawableProvider(LocalPlatformContext.current)
                        CircularOverlayButton(
                            painterName = "add_triangle_24px",
                            drawableProvider = drawableProvider,
                            contentDescription = strings["alert_report_title"],
                            tint = AlertAmber,
                            onClick = onReportAlert
                        )
                    } else {
                        // Keeps the countdown optically centred when the alert button is off.
                        Spacer(Modifier.width(48.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun JourneyProgressBar(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(4.dp)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun CircularOverlayButton(
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    painterName: String? = null,
    drawableProvider: DrawableProvider? = null,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(26.dp)
            )
        } else if (painterName != null && drawableProvider != null) {
            Icon(
                painter = drawableProvider.getPainter(painterName),
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

/** The instruction as a sentence in the active locale. Also used for the ongoing notification. */
@Composable
fun NavigationInstruction.displayText(): String {
    val strings = StringProvider(LocalPlatformContext.current)
    return when (this) {
        is NavigationInstruction.AcquiringSignal -> strings["nav_acquiring_signal"]
        is NavigationInstruction.InProgress -> strings["nav_in_progress"]
        is NavigationInstruction.Arrived -> strings["nav_arrived"]
        is NavigationInstruction.WalkTo -> if (distanceMeters != null) {
            strings.format("nav_walk_to_distance", stopName, formatDistance(distanceMeters))
        } else {
            strings.format("nav_walk_to", stopName)
        }
        is NavigationInstruction.BoardAt -> strings.format(
            "nav_board_at",
            formatDuration(secondsUntilDeparture),
            stopName
        )
        is NavigationInstruction.RideTo -> when {
            remainingStops <= 0 && changesLine -> strings.format("nav_ride_next_stop_change", stopName)
            remainingStops <= 0 -> strings.format("nav_ride_next_stop", stopName)
            changesLine -> strings.plural(
                "nav_ride_stops_change_one",
                "nav_ride_stops_change_other",
                remainingStops,
                stopName,
                remainingStops
            )
            else -> strings.plural(
                "nav_ride_stops_one",
                "nav_ride_stops_other",
                remainingStops,
                stopName,
                remainingStops
            )
        }
    }
}

/** Rounds up, so "1 min" only becomes "0 min" once there is genuinely nothing left. */
@Composable
private fun formatRemainingTime(seconds: Int): String {
    val strings = StringProvider(LocalPlatformContext.current)
    val minutes = (seconds + 59) / 60
    return if (minutes < 60) {
        strings.format("duration_minutes", minutes)
    } else {
        strings.format("duration_hours_minutes", minutes / 60, (minutes % 60).toString().padStart(2, '0'))
    }
}

@Composable
private fun formatDuration(seconds: Int): String {
    val strings = StringProvider(LocalPlatformContext.current)
    if (seconds < 60) return strings["duration_less_than_a_minute"]
    val minutes = seconds / 60
    return if (minutes < 60) {
        strings.format("duration_minutes", minutes)
    } else {
        strings.format("duration_hours_minutes", minutes / 60, (minutes % 60).toString().padStart(2, '0'))
    }
}

@Composable
private fun formatDistance(meters: Int): String {
    val strings = StringProvider(LocalPlatformContext.current)
    if (meters < 1000) return strings.format("distance_meters", meters)
    val separator = strings["decimal_separator"]
    val kilometers = "${meters / 1000}$separator${(meters % 1000) / 100}"
    return strings.format("distance_kilometers", kilometers)
}
