package eu.dotshell.pelo.platform

import eu.dotshell.pelo.generic.data.config.AppConfigLoader
import eu.dotshell.pelo.generic.data.config.LineColorsData
import eu.dotshell.pelo.generic.data.network.mapstyle.MapStyleConfig
import eu.dotshell.pelo.generic.data.network.transport.TransportLineRules
import eu.dotshell.pelo.generic.service.TransportServiceProvider

// Same as the Android actuals: everything is loaded from config.json via the common
// AppConfigLoader / TransportServiceProvider (initialized at startup). The previous iOS
// stubs threw "Config not available on iOS".
actual fun provideLineColors(): LineColorsData =
    AppConfigLoader.getConfig().lineColors

actual fun provideTransportLineRules(): TransportLineRules =
    TransportServiceProvider.getTransportLineRules()

actual fun provideMapStyleConfig(): MapStyleConfig =
    TransportServiceProvider.getMapStyleConfig()
