package eu.dotshell.pelo.generic.ui.screens.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import eu.dotshell.pelo.generic.data.config.AppConfigLoader
import eu.dotshell.pelo.generic.data.telemetry.TelemetryEmitter

/**
 * Wraps the main content. Telemetry consent is handled upstream by the mandatory
 * onboarding consent, so this gate now auto-accepts the current schema.
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
    val needsAutoAccept = !state.optedIn || (state.schemaVersionAccepted ?: 0) < schemaVersion

    if (needsAutoAccept) {
        LaunchedEffect(schemaVersion, needsAutoAccept) {
            optInManager.acceptCurrentSchema(schemaVersion)
        }
    }

    content()
}
