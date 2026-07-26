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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.dotshell.pelo.generic.data.models.realtime.alerts.official.AlertSeverity
import eu.dotshell.pelo.generic.data.models.realtime.alerts.official.TrafficAlert
import eu.dotshell.pelo.platform.LocalPlatformContext
import eu.dotshell.pelo.platform.StringProvider

@Composable
fun NavigationModeOverlay(
    state: NavigationModeUiState,
    showRecenterButton: Boolean,
    onRecenter: () -> Unit,
    isVoiceEnabled: Boolean,
    onToggleVoice: () -> Unit,
    isRerouting: Boolean,
    onReroute: () -> Unit,
    onDismissReroute: () -> Unit,
    alert: TrafficAlert?,
    onAlertClick: () -> Unit,
    /** Height of the navigation sheet's peek area, so the recentre button clears it. */
    sheetPeekHeight: Dp,
    modifier: Modifier = Modifier
) {
    val strings = StringProvider(LocalPlatformContext.current)

    Box(modifier) {
        NavigationInstructionCard(
            state = state,
            isVoiceEnabled = isVoiceEnabled,
            onToggleVoice = onToggleVoice,
            isRerouting = isRerouting,
            onReroute = onReroute,
            onDismissReroute = onDismissReroute,
            alert = alert,
            onAlertClick = onAlertClick,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        )

        // Recentring is the way back from a map the traveller panned away — without it, making
        // the map interactive would be a one-way door.
        if (showRecenterButton) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = sheetPeekHeight + 12.dp)
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
    }
}

@Composable
private fun NavigationInstructionCard(
    state: NavigationModeUiState,
    isVoiceEnabled: Boolean,
    onToggleVoice: () -> Unit,
    isRerouting: Boolean,
    onReroute: () -> Unit,
    onDismissReroute: () -> Unit,
    alert: TrafficAlert?,
    onAlertClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = StringProvider(LocalPlatformContext.current)
    val hasUpNext = state.upcomingLeg != null && !state.isArrived
    // One radius everywhere, except the bottom-left when the "up next" strip is there: that corner
    // stays square so the strip below it carries the rounding and the two read as one panel.
    val topShape = if (hasUpNext) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 0.dp, bottomEnd = 20.dp)
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

            Icon(
                imageVector = if (isVoiceEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                contentDescription = strings[if (isVoiceEnabled) "nav_voice_on" else "nav_voice_off"],
                tint = if (isVoiceEnabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleVoice)
                    .padding(8.dp)
            )
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

        // One banner at a time, and leaving the route outranks a disruption on a line ahead: the
        // first is about what to do now, the second about what to expect later.
        if (state.canReroute) {
            Spacer(Modifier.height(8.dp))
            RerouteBanner(
                isRerouting = isRerouting,
                onReroute = onReroute,
                onDismiss = onDismissReroute,
            )
        } else if (alert != null && !state.isArrived) {
            Spacer(Modifier.height(8.dp))
            AlertBanner(alert = alert, onClick = onAlertClick)
        }
    }
}

/** A disruption on a line still to come. Tapping opens the operator's full wording. */
@Composable
private fun AlertBanner(alert: TrafficAlert, onClick: () -> Unit) {
    val strings = StringProvider(LocalPlatformContext.current)
    val severity = AlertSeverity.fromSeverityType(alert.severityType, alert.severityLevel)
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // The severity colour comes from the alert feed itself, so it matches how the same alert
        // is coloured everywhere else in the app.
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(severity.color))
        )
        Text(
            text = strings.format("nav_alert_on_line", alert.lineName, alert.title),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Offers a new plan when the traveller has clearly left the old one. Deliberately an offer: a
 * transit reroute can swap every line of the journey, and stepping off the route is not always a
 * mistake.
 */
@Composable
private fun RerouteBanner(
    isRerouting: Boolean,
    onReroute: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = StringProvider(LocalPlatformContext.current)
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = strings["nav_reroute_title"],
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onReroute, enabled = !isRerouting) {
                Text(strings[if (isRerouting) "nav_reroute_running" else "nav_reroute_action"])
            }
            TextButton(onClick = onDismiss, enabled = !isRerouting) {
                Text(
                    text = strings["nav_reroute_dismiss"],
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
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
        // Suppressed once the banner below says the same thing with an action attached.
        state.isOffRoute && !state.canReroute -> {
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
