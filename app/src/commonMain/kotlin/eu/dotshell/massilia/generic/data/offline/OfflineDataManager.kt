package eu.dotshell.massilia.generic.data.offline

import eu.dotshell.massilia.platform.ioDispatcher

import eu.dotshell.massilia.platform.Log
import eu.dotshell.massilia.platform.PlatformContext
import eu.dotshell.massilia.platform.createOfflineTileDownloader
import eu.dotshell.massilia.generic.data.network.transport.TransportApi
import eu.dotshell.massilia.generic.data.network.transport.TransportLinesQuery
import eu.dotshell.massilia.generic.data.models.geojson.Feature
import eu.dotshell.massilia.generic.data.models.geojson.StopFeature
import eu.dotshell.massilia.generic.data.models.realtime.alerts.official.TrafficAlertsResponse
import eu.dotshell.massilia.generic.service.TransportServiceProvider
import eu.dotshell.massilia.generic.utils.network.withRetry
import eu.dotshell.massilia.generic.data.repository.api.OfflineDataManager as ApiOfflineDataManager
import eu.dotshell.massilia.generic.data.repository.offline.SchedulesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Orchestrates the download of all offline data:
 * transport lines (including ALL bus lines), stops, traffic alerts, and map tiles.
 * Now uses dependency injection for TransportApi.
 */
class OfflineDataManager(
    private val transportApi: TransportApi,
    context: PlatformContext
) : ApiOfflineDataManager {

    companion object {
        private const val TAG = "OfflineDataManager"

        // Weight of each step in the overall progress (total = 1.0)
        private const val WEIGHT_METRO_TRAM = 0.05f
        private const val WEIGHT_NAVIGONE_TRAMBUS = 0.03f
        private const val WEIGHT_BUS = 0.10f
        private const val WEIGHT_RX = 0.02f
        private const val WEIGHT_STOPS = 0.05f
        private const val WEIGHT_ALERTS = 0.02f
        private const val WEIGHT_MAP_TILES = 0.73f
    }

    private val offlineRepository by lazy { OfflineRepository(context) }
    private val offlineMapManager by lazy { createOfflineTileDownloader(context) }
    private val schedulesRepository by lazy { SchedulesRepository.getInstance(context) }

    private val _downloadState = MutableStateFlow<OfflineDownloadState>(OfflineDownloadState.Idle)
    val downloadState: StateFlow<OfflineDownloadState> = _downloadState.asStateFlow()

    private val _offlineDataInfo = MutableStateFlow(OfflineDataInfo())
    val offlineDataInfo: StateFlow<OfflineDataInfo> = _offlineDataInfo.asStateFlow()

    suspend fun refreshOfflineDataInfo() {
        withContext(ioDispatcher) {
            _offlineDataInfo.value = offlineRepository.getOfflineDataInfo()
        }
    }

    /**
     * Cancels an ongoing download.
     * Already-saved data is preserved (partial data is still useful offline).
     * The coroutine Job must also be cancelled externally by TransportViewModel.
     */
    override fun cancelDownload() {
        offlineMapManager.cancelDownload()
        _downloadState.value = OfflineDownloadState.Idle
        _offlineDataInfo.value = offlineRepository.getOfflineDataInfo()
    }

    /**
     * Downloads all offline data sequentially.
     * Each step updates the progress flow.
     */
    override suspend fun downloadAllOfflineData() {
        if (_downloadState.value is OfflineDownloadState.Downloading) return

        withContext(ioDispatcher) {
            try {
                var cumulativeProgress: Float
                val dataWeight =
                    WEIGHT_METRO_TRAM + WEIGHT_NAVIGONE_TRAMBUS + WEIGHT_RX + WEIGHT_STOPS + WEIGHT_ALERTS

                // ============================================================
                // BATCH 1: Parallel API calls for all non-bus data
                // All calls are independent (different API endpoints, different files).
                // ============================================================
                _downloadState.value =
                    OfflineDownloadState.Downloading(0f, "Téléchargement des données...")

                // Launch all API calls in parallel. Non-critical calls have try/catch
                // inside async so they return null on failure instead of cancelling others.
                data class BatchResults(
                    val metroFeatures: List<Feature>?,
                    val tramFeatures: List<Feature>?,
                    val navigoneFeatures: List<Feature>?,
                    val trambusFeatures: List<Feature>?,
                    val rxFeatures: List<Feature>?,
                    val stopsFeatures: List<StopFeature>?,
                    val alertsResponse: TrafficAlertsResponse?
                )

                val batchResults: BatchResults
                try {
                    batchResults = coroutineScope {
                        // Strong lines are provided in a single call.
                        val strongLines = withRetry(
                            maxRetries = 2,
                            initialDelayMs = 1000
                        ) { transportApi.getLines(TransportLinesQuery.StrongLines) }

                        val stops = withRetry(
                            maxRetries = 2,
                            initialDelayMs = 1000
                        ) { transportApi.getTransportStops() }

                        // Traffic alerts are non-critical.
                        val alerts = runCatching {
                            withRetry(
                                maxRetries = 2,
                                initialDelayMs = 500
                            ) { transportApi.getTrafficAlerts() }
                        }.getOrNull()

                        // Bucket strong lines by their feature transportType (network-agnostic):
                        // metro, tram and water lines have dedicated buckets; the remaining
                        // strong bus-type lines (e.g. RTM BHNS B1-B5) go to the trambus bucket.
                        fun typeOf(f: eu.dotshell.massilia.generic.data.models.geojson.Feature) =
                            f.properties.transportType.uppercase()

                        val metroFeatures = strongLines.features.filter { typeOf(it) == "METRO" || typeOf(it) == "FUNICULAR" }
                        val tramFeatures = strongLines.features.filter { typeOf(it) == "TRAM" }
                        val navigoneFeatures = strongLines.features.filter { typeOf(it) == "NAVIGONE" }
                        val trambusFeatures = strongLines.features.filter {
                            typeOf(it) !in setOf("METRO", "FUNICULAR", "TRAM", "NAVIGONE")
                        }
                        val rxFeatures = emptyList<eu.dotshell.massilia.generic.data.models.geojson.Feature>()

                        BatchResults(
                            metroFeatures = metroFeatures,
                            tramFeatures = tramFeatures,
                            navigoneFeatures = navigoneFeatures,
                            trambusFeatures = trambusFeatures,
                            rxFeatures = rxFeatures,
                            stopsFeatures = stops.features,
                            alertsResponse = alerts
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to download critical data (metro/tram/stops)", e)
                    _downloadState.value =
                        OfflineDownloadState.Error("Échec du téléchargement: ${e.message}")
                    return@withContext
                }

                // Save all results — each writes a different file, no conflicts
                _downloadState.value =
                    OfflineDownloadState.Downloading(0.02f, "Sauvegarde des données...")

                offlineRepository.saveMetroLines(batchResults.metroFeatures ?: emptyList())
                offlineRepository.saveTramLines(batchResults.tramFeatures ?: emptyList())

                batchResults.navigoneFeatures?.let { features ->
                    offlineRepository.saveNavigoneLines(features)
                    Log.i(TAG, "Navigone: saved ${features.size} features")
                }

                batchResults.trambusFeatures?.let { features ->
                    Log.i(
                        TAG,
                        "Trambus API returned ${features.size} features: ${
                            features.map { it.properties.lineName }.distinct()
                        }"
                    )
                    if (features.isNotEmpty()) {
                        offlineRepository.saveTrambusLines(features)
                        val verifyLoad = offlineRepository.loadTrambusLines()
                        Log.i(
                            TAG,
                            "Trambus: verify after save = ${verifyLoad?.size ?: "NULL (write failed!)"} features"
                        )
                    }
                }

                batchResults.rxFeatures?.let { features ->
                    if (features.isNotEmpty()) offlineRepository.saveRxLines(features)
                }

                offlineRepository.saveStops(batchResults.stopsFeatures ?: emptyList())

                batchResults.alertsResponse?.let { response ->
                    if (response.success && response.alerts.isNotEmpty()) {
                        offlineRepository.saveTrafficAlerts(response.alerts)
                    }
                }

                cumulativeProgress = dataWeight
                Log.i(TAG, "Batch 1 complete: all non-bus data downloaded and saved")

                // ============================================================
                // BATCH 2: Bus lines — sequential pages (OOM protection)
                // ============================================================
                _downloadState.value =
                    OfflineDownloadState.Downloading(cumulativeProgress, "Lignes de bus...")
                try {
                    offlineRepository.clearBusLines()
                    val busLikeNames = schedulesRepository.getAllBusLikeRouteNames()
                    val busNameBySafe = busLikeNames.associateBy {
                        it.uppercase().replace(Regex("[^A-Za-z0-9_-]"), "_")
                    }
                    val pageSize = 500
                    var startIndex = 0
                    var totalDownloaded = 0
                    var hasMore = true
                    val rescuedTrambus = mutableListOf<Feature>()

                    Log.i(TAG, "Starting paginated bus download (pageSize=$pageSize)")

                    while (hasMore) {
                        Log.i(TAG, "Fetching bus page: startIndex=$startIndex, count=$pageSize")
                        val page = withRetry(maxRetries = 2, initialDelayMs = 1000) {
                            transportApi.getLines(
                                TransportLinesQuery.BusPage(
                                    startIndex = startIndex,
                                    count = pageSize
                                )
                            )
                        }
                        val features = page.features
                        Log.i(
                            TAG,
                            "Bus page response: ${features.size} features, totalFeatures=${page.totalFeatures}, numberMatched=${page.numberMatched}, numberReturned=${page.numberReturned}"
                        )

                        if (features.isNotEmpty()) {
                            val trambusInPage =
                                features.filter { it.properties.lineName.uppercase().startsWith("TB") }
                            val busFeatures = features.filter {
                                !it.properties.lineName.uppercase().startsWith("TB")
                            }

                            if (trambusInPage.isNotEmpty()) {
                                rescuedTrambus.addAll(trambusInPage)
                                Log.i(
                                    TAG,
                                    "Rescued ${trambusInPage.size} trambus features from bus page"
                                )
                            }

                            if (busFeatures.isNotEmpty()) {
                                offlineRepository.saveBusLinesPage(busFeatures)
                                totalDownloaded += busFeatures.size
                            }

                            startIndex += features.size
                            Log.i(
                                TAG,
                                "Bus page saved: ${busFeatures.size} bus features (total: $totalDownloaded), trambus rescued: ${rescuedTrambus.size}"
                            )
                            _downloadState.value = OfflineDownloadState.Downloading(
                                cumulativeProgress + WEIGHT_BUS * (totalDownloaded.toFloat() / 10000f).coerceAtMost(
                                    0.95f
                                ),
                                "Lignes de bus ($totalDownloaded)..."
                            )
                        } else {
                            startIndex += pageSize
                        }
                        hasMore = features.size >= pageSize
                        Log.i(
                            TAG,
                            "hasMore=$hasMore (features.size=${features.size} >= pageSize=$pageSize)"
                        )
                    }

                    // Save rescued trambus if batch 1 failed to download them
                    if (rescuedTrambus.isNotEmpty()) {
                        val existingTrambus = offlineRepository.loadTrambusLines()
                        if (existingTrambus.isNullOrEmpty()) {
                            offlineRepository.saveTrambusLines(rescuedTrambus)
                            Log.i(
                                TAG,
                                "Saved ${rescuedTrambus.size} rescued trambus features (batch 1 had failed)"
                            )
                        } else {
                            Log.i(
                                TAG,
                                "Trambus already saved from batch 1 (${existingTrambus.size} features), skipping rescued ones"
                            )
                        }
                    }

                    var busFiles = offlineRepository.getAvailableBusLineNames()
                    Log.i(
                        TAG,
                        "Bus download complete: $totalDownloaded features total, ${busFiles.size} line files on disk: ${
                            busFiles.take(10)
                        }"
                    )

                    // Fallback: fetch missing lines in parallel batches of 5
                    if (busNameBySafe.isNotEmpty()) {
                        val busFilesSafe = busFiles.map { it.uppercase() }.toSet()
                        val missingSafe = busNameBySafe.keys - busFilesSafe
                        if (missingSafe.isNotEmpty()) {
                            Log.w(
                                TAG,
                                "Bulk bus download missing ${missingSafe.size} lines, starting per-line fallback (batched)"
                            )
                            var done = 0
                            val total = missingSafe.size
                            val missingList = missingSafe.toList()

                            for (batch in missingList.chunked(5)) {
                                coroutineScope {
                                    batch.map { safeName ->
                                        async {
                                            val lineName = busNameBySafe[safeName] ?: return@async
                                            try {
                                                val pageFeatures = withRetry(
                                                    maxRetries = 2,
                                                    initialDelayMs = 1000
                                                ) {
                                                    transportApi
                                                        .getLines(
                                                            TransportLinesQuery.LineByName(lineName)
                                                        )
                                                        .features
                                                }
                                                if (pageFeatures.isNotEmpty()) {
                                                    offlineRepository.saveBusLinesPage(pageFeatures)
                                                }
                                            } catch (e: Exception) {
                                                Log.w(
                                                    TAG,
                                                    "Fallback bus fetch failed for $lineName: ${e.message}"
                                                )
                                            }
                                        }
                                    }.awaitAll()
                                }
                                done += batch.size
                                _downloadState.value = OfflineDownloadState.Downloading(
                                    cumulativeProgress + WEIGHT_BUS * (done.toFloat() / total).coerceAtMost(
                                        0.95f
                                    ),
                                    "Lignes de bus ($done/$total)..."
                                )
                            }
                            busFiles = offlineRepository.getAvailableBusLineNames()
                            Log.i(TAG, "Bus fallback complete: ${busFiles.size} line files on disk")
                        }
                    }
                } catch (e: OutOfMemoryError) {
                    Log.e(TAG, "OutOfMemoryError downloading bus lines", e)
                    _downloadState.value =
                        OfflineDownloadState.Error("Mémoire insuffisante pour télécharger les lignes de bus")
                    return@withContext
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to download bus lines", e)
                    _downloadState.value =
                        OfflineDownloadState.Error("Échec du téléchargement des lignes de bus: ${e.message}")
                    return@withContext
                }
                cumulativeProgress += WEIGHT_BUS

                // Mark data download as complete
                offlineRepository.markDownloadComplete()
                // Log offline info right after marking complete (before map tiles)
                val infoAfterData = offlineRepository.getOfflineDataInfo()
                Log.i(
                    TAG,
                    "After data download: busLinesCount=${infoAfterData.busLinesCount}, totalSize=${infoAfterData.totalSizeBytes}, isAvailable=${infoAfterData.isAvailable}"
                )
                _offlineDataInfo.value = infoAfterData

                // Step 7: Map tiles for each selected style
                val selectedStyleKeys = offlineRepository.getSelectedMapStyles()
                val mapStyleConfig = TransportServiceProvider.getMapStyleConfig()
                val stylesToDownload = selectedStyleKeys.mapNotNull { key ->
                    mapStyleConfig.getMapStyleByKey(key)
                }
                    .filter { it.styleUrl.startsWith("http") } // Exclude asset:// styles (e.g. Satellite)

                val completedStyles = mutableSetOf<String>()
                val weightPerStyle =
                    if (stylesToDownload.isNotEmpty()) WEIGHT_MAP_TILES / stylesToDownload.size else WEIGHT_MAP_TILES

                for ((styleIndex, style) in stylesToDownload.withIndex()) {
                    val regionName = offlineMapManager.regionNameForStyle(style.key)
                    val styleBaseProgress = cumulativeProgress + (styleIndex * weightPerStyle)
                    _downloadState.value = OfflineDownloadState.Downloading(
                        styleBaseProgress,
                        "Tuiles ${style.displayName} (${styleIndex + 1}/${stylesToDownload.size})..."
                    )

                    // Reset to Idle before starting so first{} doesn't see stale Complete
                    offlineMapManager.resetState()
                    offlineMapManager.startDownload(style.styleUrl, regionName)

                    // Monitor progress in a child coroutine, wait for terminal state
                    coroutineScope {
                        val progressJob = launch {
                            offlineMapManager.downloadState.collect { mapState ->
                                if (mapState is MapTilesDownloadState.Downloading) {
                                    val totalProgress =
                                        styleBaseProgress + (mapState.progress * weightPerStyle)
                                    _downloadState.value = OfflineDownloadState.Downloading(
                                        totalProgress.coerceIn(0f, 1f),
                                        "Tuiles ${style.displayName} (${(mapState.progress * 100).toInt()}%)..."
                                    )
                                }
                            }
                        }

                        // Wait for terminal state (Complete or Error)
                        val terminalState = offlineMapManager.downloadState.first { state ->
                            state is MapTilesDownloadState.Complete || state is MapTilesDownloadState.Error
                        }
                        progressJob.cancel()

                        when (terminalState) {
                            is MapTilesDownloadState.Complete -> {
                                completedStyles.add(style.key)
                                Log.i(TAG, "Map tiles for ${style.key} complete")
                            }

                            is MapTilesDownloadState.Error -> {
                                Log.e(
                                    TAG,
                                    "Map tiles for ${style.key} failed: ${terminalState.message}"
                                )
                            }

                            else -> {}
                        }
                    }
                }

                // Also keep any previously downloaded styles that are still selected
                val previouslyDownloaded = offlineRepository.getDownloadedMapStyles()
                val allDownloaded =
                    completedStyles + previouslyDownloaded.filter { it in selectedStyleKeys }
                offlineRepository.setDownloadedMapStyles(allDownloaded)
                _downloadState.value = OfflineDownloadState.Complete
                _offlineDataInfo.value = offlineRepository.getOfflineDataInfo()

            } catch (e: CancellationException) {
                Log.i(TAG, "Download cancelled by user")
                offlineMapManager.cancelDownload()
                _downloadState.value = OfflineDownloadState.Idle
                _offlineDataInfo.value = offlineRepository.getOfflineDataInfo()
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during offline download", e)
                _downloadState.value = OfflineDownloadState.Error("Erreur inattendue: ${e.message}")
            }
        }
    }

}
