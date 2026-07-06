package eu.dotshell.massilia.generic.ui.screens.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import eu.dotshell.massilia.generic.data.config.AppConfigLoader
import eu.dotshell.massilia.generic.data.consent.TermsConsentManager
import eu.dotshell.massilia.platform.LocalPlatformContext

/**
 * Gates the app behind terms/privacy acceptance.
 *
 * [onConsentSatisfied] fires once when the app proceeds past the gate — either right after the
 * user accepts, or immediately on launch for a returning user who already accepted. It's the hook
 * platforms use to defer follow-up prompts (e.g. the location permission request) until consent.
 */
@Composable
fun TermsConsentGate(
    onConsentSatisfied: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val context = LocalPlatformContext.current
    val config = AppConfigLoader.getConfig().consent
    val legalSections = AppConfigLoader.getConfig().about.legalSections
    val manager = remember(context) { TermsConsentManager(context) }
    val state by manager.state.collectAsState()

    val needsAcceptance = !state.accepted || (state.versionAccepted ?: 0) < config.version

    if (needsAcceptance) {
        TermsConsentScreen(
            consent = config,
            legalSections = legalSections,
            onAccept = { manager.accept(config.version) }
        )
    } else {
        LaunchedEffect(Unit) { onConsentSatisfied() }
        content()
    }
}
