package eu.dotshell.pelo.platform

import eu.dotshell.pelo.generic.data.config.LineColorsData
import eu.dotshell.pelo.generic.data.network.mapstyle.MapStyleConfig
import eu.dotshell.pelo.generic.data.network.transport.TransportLineRules

expect fun provideLineColors(): LineColorsData
expect fun provideTransportLineRules(): TransportLineRules
expect fun provideMapStyleConfig(): MapStyleConfig
