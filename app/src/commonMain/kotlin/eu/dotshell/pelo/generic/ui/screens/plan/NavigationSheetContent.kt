package eu.dotshell.pelo.generic.ui.screens.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.JourneyResult
import eu.dotshell.pelo.generic.service.TransportServiceProvider
import eu.dotshell.pelo.generic.ui.screens.plan.itinerary.JourneyDetailsSheetContent
import eu.dotshell.pelo.generic.ui.theme.isAppInDarkTheme
import eu.dotshell.pelo.platform.DrawableProvider
import eu.dotshell.pelo.platform.LocalPlatformContext
import eu.dotshell.pelo.platform.StringProvider

/** Amber used for the "report an alert" affordance, matching the map FAB. */
private val AlertAmber = Color(0xFFFACC15)

/**
 * Height of the always-visible part of the navigation sheet, excluding the drag handle and the
 * gesture inset (the summary carries that itself).
 */
val NavigationSheetPeekContentHeight: Dp = 104.dp

/**
 * Vertical space `BottomSheetDefaults.DragHandle` occupies (4dp bar, 22dp padding either side).
 * The scaffold measures its peek from the sheet's very top, handle included, so the peek height
 * has to account for it or the summary row is cut in half.
 */
val SheetDragHandleHeight: Dp = 48.dp

/**
 * The bottom half of navigation mode, as the scaffold's bottom sheet.
 *
 * Collapsed it is the status bar the mode has always had — time left, stop, report. Pulled up it
 * reveals the full journey breakdown, so the traveller can check the stops ahead or which platform
 * the change is on without leaving guidance.
 */
@Composable
fun NavigationSheetContent(
    state: NavigationModeUiState,
    journey: JourneyResult,
    onStop: () -> Unit,
    onReportAlert: () -> Unit,
    maxHeight: Dp,
    getZoneForStopName: (String) -> String? = { null },
) {
    val strings = StringProvider(LocalPlatformContext.current)
    var showStopConfirmation by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        NavigationSheetSummary(
            state = state,
            onStopRequested = {
                // Arrival is already an explicit end of the journey; only interrupting one early
                // is worth a confirmation.
                if (state.isArrived) onStop() else showStopConfirmation = true
            },
            onReportAlert = onReportAlert,
        )

        JourneyDetailsSheetContent(
            journey = journey,
            isExpanded = true,
            showStartAction = false,
            modifier = Modifier
                .fillMaxWidth()
                // The summary already claims the top of the sheet; the breakdown gets the rest,
                // and scrolls within it.
                .heightIn(
                    max = (maxHeight - NavigationSheetPeekContentHeight - SheetDragHandleHeight)
                        .coerceAtLeast(160.dp)
                ),
            useLightColors = !isAppInDarkTheme(),
            scrollAllContent = true,
            getZoneForStopName = getZoneForStopName,
        )
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

/** The peek row: what stays on screen with the sheet collapsed. */
@Composable
private fun NavigationSheetSummary(
    state: NavigationModeUiState,
    onStopRequested: () -> Unit,
    onReportAlert: () -> Unit,
) {
    val strings = StringProvider(LocalPlatformContext.current)

    if (state.isArrived) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .heightIn(min = NavigationSheetPeekContentHeight)
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Button(onClick = onStopRequested, modifier = Modifier.fillMaxWidth()) {
                Text(strings["nav_finish"])
            }
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The gesture inset belongs to the peek, not to the breakdown below it: leaving it out
            // let the top of the detail show through under the summary when collapsed.
            .windowInsetsPadding(WindowInsets.navigationBars)
            .heightIn(min = NavigationSheetPeekContentHeight)
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
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

@Composable
private fun CircularOverlayButton(
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    icon: ImageVector? = null,
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

/** Rounds up, so "0 min" only appears once there is genuinely nothing left. */
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
