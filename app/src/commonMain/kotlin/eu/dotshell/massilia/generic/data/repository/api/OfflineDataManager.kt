package eu.dotshell.massilia.generic.data.repository.api

interface OfflineDataManager {
    suspend fun downloadAllOfflineData()
    fun cancelDownload()
}
