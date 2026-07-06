package eu.dotshell.massilia.generic.ui.screens.plan

import eu.dotshell.massilia.platform.ioDispatcher

import eu.dotshell.massilia.platform.createHttpClientEngine
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal data class UserAlertSubmissionResult(
    val isSuccess: Boolean,
    val errorMessage: String? = null,
    val httpCode: Int? = null
)

// Singleton across submissions (mirrors the old module-level OkHttpClient).
private val userAlertSubmissionHttpClient by lazy { HttpClient(createHttpClientEngine()) }

internal suspend fun submitUserAlert(
    alertTypeId: String,
    stopName: String?,
    stopIdFallback: Int?,
    lineId: String?
): UserAlertSubmissionResult = withContext(ioDispatcher) {
    try {
        val url = "https://api.dotshell.eu/massilia/v1/users-alerts"
        val payload = buildJsonObject {
            put("type", alertTypeId)
            when {
                !stopName.isNullOrBlank() -> put("stopId", stopName)
                stopIdFallback != null -> put("stopId", stopIdFallback.toString())
            }
            if (!lineId.isNullOrBlank()) {
                put("lineId", lineId)
            }
        }

        val response = userAlertSubmissionHttpClient.post(url) {
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }
        val code = response.status.value
        when (code) {
            201 -> UserAlertSubmissionResult(isSuccess = true, httpCode = code)
            400 -> UserAlertSubmissionResult(
                isSuccess = false,
                errorMessage = "Données invalides. Vérifiez votre signalement et réessayez.",
                httpCode = code
            )
            404 -> UserAlertSubmissionResult(
                isSuccess = false,
                errorMessage = "L'arrêt ou la ligne sélectionnée n'a pas été trouvée.",
                httpCode = code
            )
            500 -> UserAlertSubmissionResult(
                isSuccess = false,
                errorMessage = "Erreur serveur. Veuillez réessayer plus tard.",
                httpCode = code
            )
            else -> UserAlertSubmissionResult(
                isSuccess = false,
                errorMessage = "Une erreur est survenue (code: $code). Veuillez réessayer.",
                httpCode = code
            )
        }
    } catch (_: Exception) {
        UserAlertSubmissionResult(
            isSuccess = false,
            errorMessage = "Erreur de connexion. Veuillez vérifier votre connexion internet."
        )
    }
}
