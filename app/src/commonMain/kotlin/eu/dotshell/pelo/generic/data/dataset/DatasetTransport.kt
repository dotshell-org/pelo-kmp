package eu.dotshell.pelo.generic.data.dataset

import eu.dotshell.pelo.generic.data.network.PUBLIC_SERVICES_USER_AGENT
import eu.dotshell.pelo.platform.Log
import eu.dotshell.pelo.platform.createHttpClientEngine
import io.ktor.client.HttpClient
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

/**
 * Fetches dataset resources. Split behind an interface so the update orchestrator can
 * be unit-tested with a fake, and the Ktor implementation kept thin.
 */
interface DatasetTransport {
    /** GETs a small text document (the manifest), or null on any non-2xx / failure. */
    suspend fun getText(url: String): String?

    /** GETs a binary file, or null on any non-2xx / failure. */
    suspend fun getBytes(url: String): ByteArray?
}

/** Ktor-backed [DatasetTransport] over the app's platform HTTP engine. */
class KtorDatasetTransport(
    private val client: HttpClient = HttpClient(createHttpClientEngine()) {
        install(UserAgent) { agent = PUBLIC_SERVICES_USER_AGENT }
    }
) : DatasetTransport {

    private companion object { const val TAG = "DatasetTransport" }

    override suspend fun getText(url: String): String? = request(url) { it.bodyAsText() }

    override suspend fun getBytes(url: String): ByteArray? = request(url) { it.bodyAsBytes() }

    private suspend fun <T> request(url: String, extract: suspend (io.ktor.client.statement.HttpResponse) -> T): T? =
        try {
            val response = client.get(url)
            if (response.status.isSuccess()) {
                extract(response)
            } else {
                Log.w(TAG, "GET $url -> ${response.status}")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "GET $url failed: ${e.message}")
            null
        }
}
