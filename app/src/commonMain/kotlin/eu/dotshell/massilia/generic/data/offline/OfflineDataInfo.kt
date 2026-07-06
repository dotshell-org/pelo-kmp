package eu.dotshell.massilia.generic.data.offline

import kotlinx.datetime.Clock

/**
 * Metadata about the offline data download.
 */
data class OfflineDataInfo(
    val isAvailable: Boolean = false,
    val lastDownloadTimestamp: Long = 0L,
    val totalSizeBytes: Long = 0L,
    val mapTilesDownloaded: Boolean = false,
    val downloadedMapStyles: Set<String> = emptySet(),
    val busLinesCount: Int = 0
) {
    val STALE_THRESHOLD_MS = 7L * 24 * 60 * 60 * 1000

    val isStale: Boolean
        get() = isAvailable && lastDownloadTimestamp > 0L &&
            (Clock.System.now().toEpochMilliseconds() - lastDownloadTimestamp) > STALE_THRESHOLD_MS
}
