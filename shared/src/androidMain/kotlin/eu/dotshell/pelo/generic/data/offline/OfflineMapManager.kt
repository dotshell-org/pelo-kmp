package eu.dotshell.pelo.generic.data.offline

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

/**
 * Manages offline map tile downloads for the Lyon TCL region using MapLibre OfflineManager.
 */
class OfflineMapManager(private val context: Context) {

    companion object {
        private const val TAG = "OfflineMapManager"
        private const val REGION_NAME_PREFIX = "pelo_lyon_tcl_"

        // Lyon metropolitan area bounding box (covers the full TCL network)
        private val LYON_BOUNDS = LatLngBounds.Builder()
            .include(LatLng(45.55, 4.65))  // Southwest
            .include(LatLng(45.95, 5.10))  // Northeast
            .build()

        // Zoom range: 8 (regional overview) to 16 (street-level detail)
        private const val MIN_ZOOM = 8.0
        private const val MAX_ZOOM = 16.0

        // Pixel ratio
        private const val PIXEL_RATIO = 1.0f

        fun regionNameForStyle(styleKey: String): String = "$REGION_NAME_PREFIX$styleKey"
    }

    private val _downloadState = MutableStateFlow<MapTilesDownloadState>(MapTilesDownloadState.Idle)
    val downloadState: StateFlow<MapTilesDownloadState> = _downloadState.asStateFlow()

    private var currentRegion: OfflineRegion? = null

    /**
     * Starts downloading map tiles for the Lyon region with a given style.
     * The style URL must be a remote URL (not asset://).
     */
    fun startDownload(styleUrl: String, regionName: String) {
        _downloadState.value = MapTilesDownloadState.Downloading(0f)

        val definition = OfflineTilePyramidRegionDefinition(
            styleUrl,
            LYON_BOUNDS,
            MIN_ZOOM,
            MAX_ZOOM,
            PIXEL_RATIO
        )

        val metadata = regionName.toByteArray(Charsets.UTF_8)

        OfflineManager.getInstance(context).createOfflineRegion(
            definition,
            metadata,
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    currentRegion = offlineRegion
                    Log.i(TAG, "Offline region created, starting download")

                    offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(status: OfflineRegionStatus) {
                            val completedResources = status.completedResourceCount
                            val requiredResources = status.requiredResourceCount

                            if (requiredResources > 0) {
                                val progress =
                                    completedResources.toFloat() / requiredResources.toFloat()
                                _downloadState.value =
                                    MapTilesDownloadState.Downloading(progress.coerceIn(0f, 1f))
                            }

                            if (status.isComplete) {
                                Log.i(
                                    TAG,
                                    "Offline region download complete: $completedResources resources"
                                )
                                _downloadState.value = MapTilesDownloadState.Complete
                            }
                        }

                        override fun onError(error: OfflineRegionError) {
                            Log.e(TAG, "Offline region error: ${error.reason} - ${error.message}")
                            _downloadState.value = MapTilesDownloadState.Error(
                                error.message
                            )
                        }

                        override fun mapboxTileCountLimitExceeded(limit: Long) {
                            Log.w(TAG, "Tile count limit exceeded: $limit")
                            _downloadState.value = MapTilesDownloadState.Error(
                                "Limite de tuiles atteinte ($limit)"
                            )
                        }
                    })

                    offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                }

                override fun onError(error: String) {
                    Log.e(TAG, "Error creating offline region: $error")
                    _downloadState.value = MapTilesDownloadState.Error(error)
                }
            }
        )
    }

    /**
     * Resets the download state to Idle.
     * Must be called before starting a new download to avoid stale terminal states.
     */
    fun resetState() {
        _downloadState.value = MapTilesDownloadState.Idle
    }

    /**
     * Cancels the current download.
     */
    fun cancelDownload() {
        currentRegion?.setDownloadState(OfflineRegion.STATE_INACTIVE)
        _downloadState.value = MapTilesDownloadState.Idle
    }

}
