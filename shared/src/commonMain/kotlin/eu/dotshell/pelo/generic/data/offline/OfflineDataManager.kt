package eu.dotshell.pelo.generic.data.offline

import eu.dotshell.pelo.platform.ioDispatcher

import eu.dotshell.pelo.platform.Log
import eu.dotshell.pelo.platform.PlatformContext
import eu.dotshell.pelo.platform.createOfflineTileDownloader
import eu.dotshell.pelo.generic.data.network.transport.TransportApi
import eu.dotshell.pelo.generic.service.TransportServiceProvider
import eu.dotshell.pelo.generic.data.repository.api.OfflineDataManager as ApiOfflineDataManager
import eu.dotshell.pelo.generic.data.repository.offline.SchedulesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
                _downloadState.value = OfflineDownloadState.Downloading(0f, "Préparation...")

                // Step: Map tiles for ALL standard styles
                val mapStyleConfig = TransportServiceProvider.getMapStyleConfig()
                val stylesToDownload = eu.dotshell.pelo.generic.data.repository.offline.mapstyle.MapStyleCompat.getByCategory(
                    eu.dotshell.pelo.generic.data.network.mapstyle.MapStyleCategory.STANDARD, 
                    mapStyleConfig
                ).filter { it.styleUrl.startsWith("http") } // Exclude asset:// styles (e.g. Satellite)

                val completedStyles = mutableSetOf<String>()
                val weightPerStyle = if (stylesToDownload.isNotEmpty()) 1.0f / stylesToDownload.size else 1.0f

                for ((styleIndex, style) in stylesToDownload.withIndex()) {
                    val regionName = offlineMapManager.regionNameForStyle(style.key)
                    val styleBaseProgress = styleIndex * weightPerStyle
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
                                    val totalProgress = styleBaseProgress + (mapState.progress * weightPerStyle)
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
                                Log.e(TAG, "Map tiles for ${style.key} failed: ${terminalState.message}")
                            }
                            else -> {}
                        }
                    }
                }

                // Mark all successfully downloaded styles
                offlineRepository.setDownloadedMapStyles(completedStyles)
                
                // Mark data download as complete
                offlineRepository.markDownloadComplete()
                
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
