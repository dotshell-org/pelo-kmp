package eu.dotshell.pelo.platform

import eu.dotshell.pelo.generic.data.config.AppConfigLoader
import eu.dotshell.pelo.generic.data.config.LineColorsData
import eu.dotshell.pelo.generic.data.network.mapstyle.MapStyleConfig
import eu.dotshell.pelo.generic.data.network.transport.TransportLineRules
import eu.dotshell.pelo.generic.service.TransportServiceProvider

actual fun provideLineColors(): LineColorsData {
    return AppConfigLoader.getConfig().lineColors
}

actual fun provideTransportLineRules(): TransportLineRules {
    return TransportServiceProvider.getTransportLineRules()
}

actual fun provideMapStyleConfig(): MapStyleConfig {
    return TransportServiceProvider.getMapStyleConfig()
}
