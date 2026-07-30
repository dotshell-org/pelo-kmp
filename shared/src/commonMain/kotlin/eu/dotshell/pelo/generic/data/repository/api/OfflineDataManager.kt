package eu.dotshell.pelo.generic.data.repository.api

interface OfflineDataManager {
    suspend fun downloadAllOfflineData()
    fun cancelDownload()
}
