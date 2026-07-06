package eu.dotshell.massilia.generic.service

import eu.dotshell.massilia.generic.data.network.mapstyle.MapStyleConfig
import eu.dotshell.massilia.generic.data.network.transport.TransportApi
import eu.dotshell.massilia.generic.data.network.transport.TransportConfig
import eu.dotshell.massilia.generic.data.network.transport.TransportLineRules
import eu.dotshell.massilia.generic.data.network.transport.TransportLineService
import eu.dotshell.massilia.generic.data.network.TrafficAlertsService
import eu.dotshell.massilia.generic.data.network.VehiclePositionsService
import eu.dotshell.massilia.generic.ui.theme.TransportTheme
import eu.dotshell.massilia.generic.ui.screens.about.AboutScreenContract
import eu.dotshell.massilia.generic.data.config.AppConfigLoader
import eu.dotshell.massilia.generic.data.config.AppTransportConfig
import eu.dotshell.massilia.generic.data.config.AppTransportLineRules
import eu.dotshell.massilia.generic.data.config.AppTrafficAlertsService
import eu.dotshell.massilia.generic.data.config.AppVehiclePositionsService
import eu.dotshell.massilia.generic.data.config.NoopTrafficAlertsService
import eu.dotshell.massilia.generic.data.config.NoopVehiclePositionsService
import eu.dotshell.massilia.generic.data.config.RealtimeConfigData
import eu.dotshell.massilia.generic.ui.screens.about.GenericAboutScreen
import eu.dotshell.massilia.generic.ui.theme.GenericTransportTheme
import eu.dotshell.massilia.specific.data.network.RtmLocalClient
import eu.dotshell.massilia.generic.data.config.AppMapStyleConfig
import eu.dotshell.massilia.platform.FileSystem
import eu.dotshell.massilia.platform.PlatformContext
import eu.dotshell.massilia.specific.TransportLineServiceImpl

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
    private lateinit var realtimeConfig: RealtimeConfigData

    /**
     * Initializes the provider with the Marseille RTM configuration
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

        // Create the API backed by bundled RTM data (no network)
        transportApi = RtmLocalClient(context)

        // Rules for matching/normalizing line names
        transportLineRules = AppTransportLineRules(appConfig.rules)

        // Real-time feature flags (no-op services + hidden UI when disabled)
        realtimeConfig = appConfig.realtime

        // Traffic alerts service
        trafficAlertsService = if (realtimeConfig.trafficAlertsEnabled) {
            AppTrafficAlertsService(appConfig.transport, transportApi)
        } else {
            NoopTrafficAlertsService()
        }

        // Vehicle positions service
        vehiclePositionsService = if (realtimeConfig.vehiclePositionsEnabled) {
            AppVehiclePositionsService(appConfig.transport, appConfig.rules)
        } else {
            NoopVehiclePositionsService()
        }

        // Theme
        transportTheme = GenericTransportTheme(appConfig.theme)

        // "About" screens
        aboutScreen = GenericAboutScreen(appConfig.about)

        // Apply the default theme
        eu.dotshell.massilia.generic.ui.theme.TransportThemeProvider.setTheme(transportTheme)
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

    /**
     * Gets the real-time feature flags (used to hide Live/alert-report UI when disabled)
     */
    fun getRealtimeConfig(): RealtimeConfigData {
        if (!::realtimeConfig.isInitialized) {
            error("TransportServiceProvider not initialized. Call initialize() first.")
        }
        return realtimeConfig
    }
}
