package eu.dotshell.massilia.platform

import androidx.compose.ui.graphics.Color

expect fun parseComposeColor(value: String?): Color?
expect fun parseComposeColor(value: String?, fallback: Color): Color
