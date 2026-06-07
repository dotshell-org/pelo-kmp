package com.pelotcl.app.platform

import com.pelotcl.app.generic.data.config.LineColorsData
import com.pelotcl.app.generic.data.network.transport.TransportLineRules

actual fun provideLineColors(): LineColorsData {
    throw UnsupportedOperationException("Config not available on iOS")
}

actual fun provideTransportLineRules(): TransportLineRules {
    throw UnsupportedOperationException("Config not available on iOS")
}
