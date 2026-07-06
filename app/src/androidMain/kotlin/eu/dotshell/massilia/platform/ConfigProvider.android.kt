package eu.dotshell.massilia.platform

import eu.dotshell.massilia.generic.data.config.AppConfigLoader
import eu.dotshell.massilia.generic.data.config.LineColorsData
import eu.dotshell.massilia.generic.data.network.mapstyle.MapStyleConfig
import eu.dotshell.massilia.generic.data.network.transport.TransportLineRules
import eu.dotshell.massilia.generic.service.TransportServiceProvider

actual fun provideLineColors(): LineColorsData {
    return AppConfigLoader.getConfig().lineColors
}

actual fun provideTransportLineRules(): TransportLineRules {
    return TransportServiceProvider.getTransportLineRules()
}

actual fun provideMapStyleConfig(): MapStyleConfig {
    return TransportServiceProvider.getMapStyleConfig()
}
