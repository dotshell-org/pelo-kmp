package eu.dotshell.pelo.generic.service

import eu.dotshell.pelo.generic.data.network.mapstyle.MapStyleConfig
import eu.dotshell.pelo.generic.data.network.transport.TransportApi
import eu.dotshell.pelo.generic.data.network.transport.TransportConfig
import eu.dotshell.pelo.generic.data.network.transport.TransportLineRules
import eu.dotshell.pelo.generic.data.network.transport.TransportLineService
import eu.dotshell.pelo.generic.data.network.TrafficAlertsService
import eu.dotshell.pelo.generic.data.network.VehiclePositionsService
import eu.dotshell.pelo.generic.ui.theme.TransportTheme
import eu.dotshell.pelo.generic.ui.screens.about.AboutScreenContract
import eu.dotshell.pelo.generic.data.config.AppConfigLoader
import eu.dotshell.pelo.generic.data.config.AppTransportConfig
import eu.dotshell.pelo.generic.data.config.AppTransportLineRules
import eu.dotshell.pelo.generic.data.config.AppTrafficAlertsService
import eu.dotshell.pelo.generic.data.config.AppVehiclePositionsService
import eu.dotshell.pelo.generic.data.config.LineSpeedBaselineData
import eu.dotshell.pelo.generic.data.config.NoopTrafficAlertsService
import eu.dotshell.pelo.generic.data.config.NoopVehiclePositionsService
import eu.dotshell.pelo.generic.data.config.RealtimeConfigData
import eu.dotshell.pelo.generic.ui.screens.about.GenericAboutScreen
import eu.dotshell.pelo.generic.ui.theme.GenericTransportTheme
import eu.dotshell.pelo.specific.data.network.LyonKtorClient
import eu.dotshell.pelo.generic.data.config.AppMapStyleConfig
import eu.dotshell.pelo.platform.FileSystem
import eu.dotshell.pelo.platform.PlatformContext
import eu.dotshell.pelo.specific.TransportLineServiceImpl

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
    private var vehicleSpeedBaseline: Map<String, LineSpeedBaselineData> = emptyMap()

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
        val fileSystem = FileSystem(context)
        transportApi = LyonKtorClient(transportConfig.baseUrl, fileSystem)

        // Rules for matching/normalizing line names
        transportLineRules = AppTransportLineRules(appConfig.rules)

        // Real-time feature flags (defaults keep everything enabled for TCL)
        realtimeConfig = appConfig.realtime
        vehicleSpeedBaseline = appConfig.transport.vehicleSpeedBaseline

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
        eu.dotshell.pelo.generic.ui.theme.TransportThemeProvider.setTheme(transportTheme)
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
     * Measured per-line commercial speeds for live dead reckoning. May be empty.
     */
    fun getVehicleSpeedBaseline(): Map<String, LineSpeedBaselineData> = vehicleSpeedBaseline

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
