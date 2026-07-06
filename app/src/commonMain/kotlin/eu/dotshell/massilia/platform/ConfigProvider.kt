package eu.dotshell.massilia.platform

import eu.dotshell.massilia.generic.data.config.LineColorsData
import eu.dotshell.massilia.generic.data.network.mapstyle.MapStyleConfig
import eu.dotshell.massilia.generic.data.network.transport.TransportLineRules

expect fun provideLineColors(): LineColorsData
expect fun provideTransportLineRules(): TransportLineRules
expect fun provideMapStyleConfig(): MapStyleConfig
