package eu.dotshell.pelo.generic.data.dataset

import eu.dotshell.pelo.platform.Log
import kotlinx.serialization.json.Json

/** Outcome of a network update check, for logging and (step 4) the settings UI. */
sealed interface CheckResult {
    data object UpToDate : CheckResult
    data object AlreadyPending : CheckResult
    /** Downloaded and staged; takes effect at the next cold start. */
    data class Downloaded(val version: String) : CheckResult
    data class Incompatible(val schema: Int, val stopsSchema: Int) : CheckResult
    data class Failed(val reason: String) : CheckResult
}

/**
 * The network half: fetch the manifest, decide, and — only when a newer compatible
 * dataset exists — download and stage it. Never touches `current`; the staged tree is
 * promoted at the next cold start by [DatasetLifecycle]. Safe to call in the
 * background; the heavy work happens only on the rare turns something actually changed.
 */
class DatasetUpdateManager(
    private val lifecycle: DatasetLifecycle,
    private val transport: DatasetTransport,
    private val baseUrl: String,
    private val city: String,
    private val activeCreatedAt: suspend () -> String?,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) {
    private companion object { const val TAG = "DatasetUpdateManager" }

    suspend fun checkForUpdate(): CheckResult {
        val manifestText = transport.getText(DatasetUrls.latest(baseUrl, city))
            ?: return CheckResult.Failed("manifest unreachable")

        val manifest = try {
            json.decodeFromString(RemoteDatasetManifest.serializer(), manifestText)
        } catch (e: Exception) {
            return CheckResult.Failed("manifest parse: ${e.message}")
        }

        // A version that broke loading before must not be pulled again.
        if (lifecycle.guard.isQuarantined(manifest.version)) {
            return CheckResult.UpToDate
        }
        // Already downloaded and waiting for the next launch — don't fetch it twice.
        if (lifecycle.installer.pendingVersion() == manifest.version) {
            return CheckResult.AlreadyPending
        }

        when (val decision = DatasetUpdatePolicy.decide(activeCreatedAt(), manifest)) {
            is UpdateDecision.UpToDate -> return CheckResult.UpToDate
            is UpdateDecision.Incompatible ->
                return CheckResult.Incompatible(decision.remoteSchema, decision.remoteStopsSchema)
            is UpdateDecision.Update -> Unit // fall through to download
        }

        return download(manifest)
    }

    private suspend fun download(manifest: RemoteDatasetManifest): CheckResult {
        val storage = lifecycle.storage
        storage.deleteRecursively(storage.stagingDir)

        for (file in manifest.files) {
            val url = DatasetUrls.file(baseUrl, city, manifest.version, file.path)
            val bytes = transport.getBytes(url)
                ?: return fail(storage, "download failed: ${file.path}")
            storage.writeBytes("${storage.stagingDir}/${file.path}", bytes)
        }

        return when (val result = lifecycle.installer.stageDownloaded(manifest)) {
            is StageResult.Installed -> {
                Log.i(TAG, "Dataset ${manifest.version} downloaded; applies at next launch")
                CheckResult.Downloaded(manifest.version)
            }
            is StageResult.Rejected -> CheckResult.Failed(result.reason)
        }
    }

    private fun fail(storage: DatasetStorage, reason: String): CheckResult {
        Log.w(TAG, reason)
        storage.deleteRecursively(storage.stagingDir)
        return CheckResult.Failed(reason)
    }
}
