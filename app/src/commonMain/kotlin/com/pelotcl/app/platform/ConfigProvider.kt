package com.pelotcl.app.platform

import com.pelotcl.app.generic.data.config.LineColorsData
import com.pelotcl.app.generic.data.network.transport.TransportLineRules

expect fun provideLineColors(): LineColorsData
expect fun provideTransportLineRules(): TransportLineRules
