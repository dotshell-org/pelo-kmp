package com.pelotcl.app.generic.ui.screens.plan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

internal data class UserAlertSubmissionResult(
    val isSuccess: Boolean,
    val errorMessage: String? = null,
    val httpCode: Int? = null
)

private val userAlertSubmissionHttpClient = OkHttpClient()

internal suspend fun submitUserAlert(
    alertTypeId: String,
    stopName: String?,
    stopIdFallback: Int?,
    lineId: String?
): UserAlertSubmissionResult = withContext(Dispatchers.IO) {
    try {
        val url = "https://api.dotshell.eu/pelo/v1/users-alerts"
        val json = JSONObject().apply {
            put("type", alertTypeId)
            when {
                !stopName.isNullOrBlank() -> put("stopId", stopName)
                stopIdFallback != null -> put("stopId", stopIdFallback.toString())
            }
            if (!lineId.isNullOrBlank()) {
                put("lineId", lineId)
            }
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        userAlertSubmissionHttpClient.newCall(request).execute().use { response ->
            return@withContext when (response.code) {
                201, 400 -> UserAlertSubmissionResult(isSuccess = true, httpCode = response.code)
                404 -> UserAlertSubmissionResult(
                    isSuccess = false,
                    errorMessage = "L'arrêt ou la ligne sélectionnée n'a pas été trouvée.",
                    httpCode = response.code
                )
                500 -> UserAlertSubmissionResult(
                    isSuccess = false,
                    errorMessage = "Erreur serveur. Veuillez réessayer plus tard.",
                    httpCode = response.code
                )
                else -> UserAlertSubmissionResult(
                    isSuccess = false,
                    errorMessage = "Une erreur est survenue (code: ${response.code}). Veuillez réessayer.",
                    httpCode = response.code
                )
            }
        }
    } catch (_: Exception) {
        UserAlertSubmissionResult(
            isSuccess = false,
            errorMessage = "Erreur de connexion. Veuillez vérifier votre connexion internet."
        )
    }
}
