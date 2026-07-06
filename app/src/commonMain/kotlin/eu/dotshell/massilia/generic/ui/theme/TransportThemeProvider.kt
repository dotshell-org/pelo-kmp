package eu.dotshell.massilia.generic.ui.theme

/**
 * Theme provider - allows dynamic theme changes
 */
object TransportThemeProvider {
    private var currentTheme: TransportTheme = GenericTransportTheme()

    fun setTheme(theme: TransportTheme) {
        currentTheme = theme
    }

    fun getTheme(): TransportTheme = currentTheme
}
