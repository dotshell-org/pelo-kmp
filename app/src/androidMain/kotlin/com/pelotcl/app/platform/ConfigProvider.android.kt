package com.pelotcl.app.platform

import com.pelotcl.app.generic.data.config.AppConfigLoader
import com.pelotcl.app.generic.data.config.LineColorsData
import com.pelotcl.app.generic.data.network.mapstyle.MapStyleConfig
import com.pelotcl.app.generic.data.network.transport.TransportLineRules
import com.pelotcl.app.generic.service.TransportServiceProvider

actual fun provideLineColors(): LineColorsData {
    return AppConfigLoader.getConfig().lineColors
}

actual fun provideTransportLineRules(): TransportLineRules {
    return TransportServiceProvider.getTransportLineRules()
}

actual fun provideMapStyleConfig(): MapStyleConfig {
    return TransportServiceProvider.getMapStyleConfig()
}
