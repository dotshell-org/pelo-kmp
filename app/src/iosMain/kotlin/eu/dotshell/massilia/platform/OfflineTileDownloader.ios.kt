package eu.dotshell.massilia.platform

import eu.dotshell.massilia.generic.data.offline.MapTilesDownloadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * iOS actual: best-effort stub. Offline tile prefetch is not implemented yet on iOS;
 * [startDownload] reports immediate completion so the data-download orchestration
 * (lines/stops/schedules) still succeeds. A real MapLibre-iOS offline pack manager
 * can replace this later.
 */
actual fun createOfflineTileDownloader(context: PlatformContext): OfflineTileDownloader =
    IosOfflineTileDownloader()

private class IosOfflineTileDownloader : OfflineTileDownloader {
    private val _downloadState = MutableStateFlow<MapTilesDownloadState>(MapTilesDownloadState.Idle)
    override val downloadState: StateFlow<MapTilesDownloadState> = _downloadState.asStateFlow()

    override fun startDownload(styleUrl: String, regionName: String) {
        _downloadState.value = MapTilesDownloadState.Complete
    }

    override fun resetState() {
        _downloadState.value = MapTilesDownloadState.Idle
    }

    override fun cancelDownload() {
        _downloadState.value = MapTilesDownloadState.Idle
    }

    override fun regionNameForStyle(styleKey: String): String = "massilia_rtm_$styleKey"
}
