package eu.dotshell.pelo.generic.data.offline

/**
 * Overall download state for all offline data.
 */
sealed class OfflineDownloadState {
    data object Idle : OfflineDownloadState()
    data class Downloading(val progress: Float, val stepDescription: String) :
        OfflineDownloadState()

    data object Complete : OfflineDownloadState()
    data class Error(val message: String) : OfflineDownloadState()
}
