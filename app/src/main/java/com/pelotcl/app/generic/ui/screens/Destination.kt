package com.pelotcl.app.generic.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val contentDescription: String
) {
    PLAN(
        route = "plan",
        label = "Plan",
        icon = Icons.Filled.Map,
        contentDescription = "Plan Tab"
    ),
    LINES(
        route = "lines",
        label = "Lignes",
        icon = Icons.Filled.Route,
        contentDescription = "Lines Tab"
    ),
    SETTINGS(
        route = "settings",
        label = "Paramètres",
        icon = Icons.Filled.Settings,
        contentDescription = "Settings Tab"
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
    }
}