package eu.dotshell.pelo.platform

import eu.dotshell.pelo.generic.data.offline.MapTilesDownloadState
import eu.dotshell.pelo.generic.data.offline.OfflineMapManager
import kotlinx.coroutines.flow.StateFlow

/**
 * Android actual: delegates to the MapLibre-backed [OfflineMapManager]
 * (`org.maplibre.android.offline.*`), which remains Android-only.
 */
actual fun createOfflineTileDownloader(context: PlatformContext): OfflineTileDownloader =
    AndroidOfflineTileDownloader(OfflineMapManager(context))

private class AndroidOfflineTileDownloader(
    private val manager: OfflineMapManager
) : OfflineTileDownloader {
    override val downloadState: StateFlow<MapTilesDownloadState> = manager.downloadState
    override fun startDownload(styleUrl: String, regionName: String) =
        manager.startDownload(styleUrl, regionName)
    override fun resetState() = manager.resetState()
    override fun cancelDownload() = manager.cancelDownload()
    override fun regionNameForStyle(styleKey: String): String =
        OfflineMapManager.regionNameForStyle(styleKey)
}
