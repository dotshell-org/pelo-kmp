package com.pelotcl.app.generic.service

import com.pelotcl.app.generic.data.network.mapstyle.MapStyleConfig
import com.pelotcl.app.generic.data.network.transport.TransportApi
import com.pelotcl.app.generic.data.network.transport.TransportConfig
import com.pelotcl.app.generic.data.network.transport.TransportLineRules
import com.pelotcl.app.generic.data.network.transport.TransportLineService
import com.pelotcl.app.generic.data.network.TrafficAlertsService
import com.pelotcl.app.generic.data.network.VehiclePositionsService
import com.pelotcl.app.generic.ui.theme.TransportTheme
import com.pelotcl.app.generic.ui.screens.about.AboutScreenContract
import com.pelotcl.app.generic.data.config.AppConfigLoader
import com.pelotcl.app.generic.data.config.AppTransportConfig
import com.pelotcl.app.generic.data.config.AppTransportLineRules
import com.pelotcl.app.generic.data.config.AppTrafficAlertsService
import com.pelotcl.app.generic.data.config.AppVehiclePositionsService
import com.pelotcl.app.generic.ui.screens.about.GenericAboutScreen
import com.pelotcl.app.generic.ui.theme.GenericTransportTheme
import com.pelotcl.app.specific.data.network.LyonKtorClient
import com.pelotcl.app.generic.data.config.AppMapStyleConfig
import com.pelotcl.app.platform.FileSystem
import com.pelotcl.app.platform.PlatformContext
import com.pelotcl.app.specific.TransportLineServiceImpl

/**
 * Service provider for the application
 * Manages initialization and provides concrete implementations
 * Replaces dependency injection for a simpler approach
 */
object TransportServiceProvider {

    private lateinit var transportConfig: TransportConfig
    private lateinit var transportApi: TransportApi
    private lateinit var transportTheme: TransportTheme
    private lateinit var aboutScreen: AboutScreenContract
    private lateinit var mapStyleConfig: MapStyleConfig
    private lateinit var vehiclePositionsService: VehiclePositionsService
    private lateinit var transportLineService: TransportLineService
    private lateinit var trafficAlertsService: TrafficAlertsService
    private lateinit var transportLineRules: TransportLineRules

    /**
     * Initializes the provider with Lyon TCL configuration
     */
    fun initialize(context: PlatformContext) {
        // Load configuration from config.json
        val appConfig = AppConfigLoader.loadConfig(FileSystem(context))

        // Transport configuration
        transportConfig = AppTransportConfig(appConfig.transport)

        // Map style configuration
        mapStyleConfig = AppMapStyleConfig(appConfig.mapStyles)

        // Transport line service
        transportLineService = TransportLineServiceImpl()

        // Create the API using the KMP-compatible Ktor client (commonMain)
        transportApi = LyonKtorClient(transportConfig.baseUrl)

        // Rules for matching/normalizing line names
        transportLineRules = AppTransportLineRules(appConfig.rules)

        // Traffic alerts service
        trafficAlertsService = AppTrafficAlertsService(appConfig.transport, transportApi)

        // Vehicle positions service
        vehiclePositionsService = AppVehiclePositionsService(appConfig.transport, appConfig.rules)

        // Theme
        transportTheme = GenericTransportTheme(appConfig.theme)

        // "About" screens
        aboutScreen = GenericAboutScreen(appConfig.about)

        // Apply the default theme
        com.pelotcl.app.generic.ui.theme.TransportThemeProvider.setTheme(transportTheme)
    }

    /**
     * Gets the transport configuration
     */
    fun getTransportConfig(): TransportConfig {
        if (!::transportConfig.isInitialized) {
            error("TransportServiceProvider not initialized. Call initialize() first.")
        }
        return transportConfig
    }

    /**
     * Gets the transport API
     */
    fun getTransportApi(): TransportApi {
        if (!::transportApi.isInitialized) {
            error("TransportServiceProvider not initialized. Call initialize() first.")
        }
        return transportApi
    }

    /**
     * Gets the transport line service (per-type line geometry loading: bus, navigone, …).
     */
    fun getTransportLineService(): TransportLineService {
        if (!::transportLineService.isInitialized) {
            error("TransportServiceProvider not initialized. Call initialize() first.")
        }
        return transportLineService
    }

    /**
     * Gets the map style configuration
     */
    fun getMapStyleConfig(): MapStyleConfig {
        if (!::mapStyleConfig.isInitialized) {
            error("TransportServiceProvider not initialized. Call initialize() first.")
        }
        return mapStyleConfig
    }

    fun getTransportLineRules(): TransportLineRules {
        if (!::transportLineRules.isInitialized) {
            error("TransportServiceProvider not initialized. Call initialize() first.")
        }
        return transportLineRules
    }

    /**
     * Gets the vehicle positions service
     */
    fun getVehiclePositionsService(): VehiclePositionsService {
        if (!::vehiclePositionsService.isInitialized) {
            error("TransportServiceProvider not initialized. Call initialize() first.")
        }
        return vehiclePositionsService
    }
}
