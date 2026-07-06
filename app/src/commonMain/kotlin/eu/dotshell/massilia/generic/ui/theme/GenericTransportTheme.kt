package eu.dotshell.massilia.generic.ui.theme

import androidx.compose.ui.graphics.Color
import eu.dotshell.massilia.generic.data.config.ThemeData
import eu.dotshell.massilia.platform.parseComposeColor

/**
 * Provides the transport "domain" colors (line colors + semantic status colors)
 * from the city [ThemeData] config. The Material color scheme (light/dark chrome)
 * lives in [MassiliaTheme]; this class no longer applies a theme.
 */
class GenericTransportTheme(private val data: ThemeData? = null) : TransportTheme {
    override val metroLineColor: Color = parseComposeColor(data?.metroLineColor, Color(0xFFFF0000))
    override val tramLineColor: Color = parseComposeColor(data?.tramLineColor, Color(0xFF00FF00))
    override val busLineColor: Color = parseComposeColor(data?.busLineColor, Color(0xFF0000FF))
    override val errorColor: Color = parseComposeColor(data?.errorColor, Color(0xFFFF0000))
    override val successColor: Color = parseComposeColor(data?.successColor, Color(0xFF00FF00))
    override val warningColor: Color = parseComposeColor(data?.warningColor, Color(0xFFFFFF00))
    override val disruptionColor: Color = parseComposeColor(data?.disruptionColor, Color(0xFFFFA500))
    override val linkColor: Color = parseComposeColor(data?.linkColor, Color(0xFF4285F4))
}
