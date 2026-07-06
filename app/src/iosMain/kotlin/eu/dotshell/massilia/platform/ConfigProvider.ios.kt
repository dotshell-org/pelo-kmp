package eu.dotshell.massilia.platform

import eu.dotshell.massilia.generic.data.config.AppConfigLoader
import eu.dotshell.massilia.generic.data.config.LineColorsData
import eu.dotshell.massilia.generic.data.network.mapstyle.MapStyleConfig
import eu.dotshell.massilia.generic.data.network.transport.TransportLineRules
import eu.dotshell.massilia.generic.service.TransportServiceProvider

// Same as the Android actuals: everything is loaded from config.json via the common
// AppConfigLoader / TransportServiceProvider (initialized at startup). The previous iOS
// stubs threw "Config not available on iOS".
actual fun provideLineColors(): LineColorsData =
    AppConfigLoader.getConfig().lineColors

actual fun provideTransportLineRules(): TransportLineRules =
    TransportServiceProvider.getTransportLineRules()

actual fun provideMapStyleConfig(): MapStyleConfig =
    TransportServiceProvider.getMapStyleConfig()
