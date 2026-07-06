package eu.dotshell.massilia.generic.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class Destination(
    val route: String,
    // Resource keys (resolved via StringProvider at the render site — enum field initialisers
    // can't call the @Composable provider).
    val labelKey: String,
    val icon: ImageVector,
    val contentDescriptionKey: String
) {
    PLAN(
        route = "plan",
        labelKey = "tab_plan",
        icon = Icons.Filled.Map,
        contentDescriptionKey = "tab_plan_cd"
    ),
    LINES(
        route = "lines",
        labelKey = "tab_lines",
        icon = Icons.Filled.Route,
        contentDescriptionKey = "tab_lines_cd"
    ),
    SETTINGS(
        route = "settings",
        labelKey = "tab_settings",
        icon = Icons.Filled.Settings,
        contentDescriptionKey = "tab_settings_cd"
    );

    companion object {
        const val ABOUT = "about"
        const val LEGAL = "legal"
        const val CREDITS = "credits"
        const val CONTACT = "contact"
        const val ITINERARY_SETTINGS = "itinerary_settings"
        const val OFFLINE_SETTINGS = "offline_settings"
        const val API_HEALTH = "api_health"
        const val TELEMETRY_SETTINGS = "telemetry_settings"
        const val TELEMETRY_PREVIEW = "telemetry_preview"
        const val TELEMETRY_FAQ = "telemetry_faq"
    }
}