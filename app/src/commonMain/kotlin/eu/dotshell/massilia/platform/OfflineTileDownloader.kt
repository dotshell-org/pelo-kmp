package eu.dotshell.massilia.platform

import eu.dotshell.massilia.generic.data.offline.MapTilesDownloadState
import kotlinx.coroutines.flow.StateFlow

/**
 * Cross-platform abstraction over offline map-tile downloading.
 *
 * The actual tile engine is platform-specific (Android = MapLibre `OfflineManager`,
 * iOS = best-effort stub for now), so this stays behind an expect/actual factory while
 * the orchestration ([eu.dotshell.massilia.generic.data.offline.OfflineDataManager]) lives in
 * commonMain. See migration plan phase 6d.
 */
interface OfflineTileDownloader {
    val downloadState: StateFlow<MapTilesDownloadState>
    fun startDownload(styleUrl: String, regionName: String)
    fun resetState()
    fun cancelDownload()
    fun regionNameForStyle(styleKey: String): String
}

expect fun createOfflineTileDownloader(context: PlatformContext): OfflineTileDownloader
