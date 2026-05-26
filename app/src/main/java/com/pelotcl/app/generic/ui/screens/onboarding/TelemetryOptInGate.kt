package com.pelotcl.app.generic.ui.screens.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.pelotcl.app.generic.data.config.AppConfigLoader
import com.pelotcl.app.generic.data.telemetry.TelemetryEmitter

/**
 * Wraps the main content. Shows the [TelemetryOptInScreen] until the user has either
 * accepted or declined for the current schema version, then transparently swaps in the
 * provided [content].
 *
 * If telemetry is disabled in `config.yml` or the [TelemetryEmitter] has not been
 * initialized (e.g., config load failed at startup), the gate is a no-op — the app
 * keeps working without telemetry.
 */
@Composable
fun TelemetryOptInGate(content: @Composable () -> Unit) {
    val optInManager = TelemetryEmitter.optInManager()
    val config = TelemetryEmitter.config() ?: AppConfigLoader.getConfig().telemetry

    if (optInManager == null || config == null) {
        content()
        return
    }

    val state by optInManager.state.collectAsState()
    val schemaVersion = config.schemaVersion
    val needsDecision = state.decidedAtEpochMs == null ||
        (state.optedIn && (state.schemaVersionAccepted ?: 0) < schemaVersion)

    if (needsDecision) {
        TelemetryOptInScreen(
            disclosure = config.disclosure,
            onAccept = { optInManager.acceptCurrentSchema(schemaVersion) },
            onDecline = { optInManager.decline() }
        )
    } else {
        content()
    }
}
