package eu.dotshell.pelo.generic.data.offline

/**
 * State of the map tiles download.
 */
sealed class MapTilesDownloadState {
    data object Idle : MapTilesDownloadState()
    data class Downloading(val progress: Float) : MapTilesDownloadState()
    data object Complete : MapTilesDownloadState()
    data class Error(val message: String) : MapTilesDownloadState()
}
