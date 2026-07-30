package eu.dotshell.pelo.generic.data.dataset

import eu.dotshell.pelo.platform.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** App-local provenance written beside a downloaded dataset — not part of the feed data. */
@Serializable
data class DatasetProvenance(
    val version: String,
    @SerialName("created_at") val createdAt: String? = null
)

/** Result of staging a downloaded dataset. */
sealed interface StageResult {
    data object Installed : StageResult
    data class Rejected(val reason: String) : StageResult
}

/**
 * Owns the on-disk moves of the dataset lifecycle: verifying a freshly downloaded
 * tree, parking it as `pending`, and promoting `pending` into `current` at cold start.
 *
 * Verification and promotion are deliberately split. A download is verified and parked
 * mid-session, but never swapped under a running RaptorLibrary — the promotion happens
 * once at the next cold start, before anything reads `current`, so the active data is
 * immutable for the life of a session.
 */
class DatasetInstaller(
    val storage: DatasetStorage,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private companion object {
        const val TAG = "DatasetInstaller"
        const val PROVENANCE = ".provenance.json"
    }

    /** Repairs any interrupted swap and clears scratch dirs. Run first, every startup. */
    fun recover() = storage.recover()

    /**
     * Verifies the files downloaded into `staging` against [manifest] — every listed
     * file must be present with the exact size and SHA-256 — then atomically parks the
     * whole tree as `pending`. Any mismatch rejects the lot and clears staging, so a
     * corrupt or truncated download can never become pending.
     */
    fun stageDownloaded(manifest: RemoteDatasetManifest): StageResult {
        val staging = storage.stagingDir
        for (file in manifest.files) {
            val path = "$staging/${file.path}"
            if (!storage.exists(path)) return reject("missing ${file.path}")
            val size = storage.sizeOf(path)
            if (size != file.bytes) return reject("size ${file.path}: got $size, want ${file.bytes}")
            val sha = storage.sha256(path)
            if (!sha.equals(file.sha256, ignoreCase = true)) return reject("sha256 ${file.path}")
        }
        if (!storage.hasDataset(staging)) return reject("no ${DatasetStore.SENTINEL} among files")

        // Provenance goes in last, so a staging tree is only complete once verified.
        storage.writeBytes(
            "$staging/$PROVENANCE",
            json.encodeToString(
                DatasetProvenance.serializer(),
                DatasetProvenance(manifest.version, manifest.createdAt)
            ).encodeToByteArray()
        )
        storage.deleteRecursively(storage.pendingDir)
        storage.replaceDir(staging, storage.pendingDir)
        Log.i(TAG, "Staged dataset ${manifest.version} as pending")
        return StageResult.Installed
    }

    /** The version already downloaded and awaiting promotion, if any. */
    fun pendingVersion(): String? = provenanceIn(storage.pendingDir)?.version

    /**
     * Promotes a verified `pending` dataset into `current`. Call once at cold start,
     * before RaptorLibrary reads anything. Returns the promoted version, or null if
     * there was nothing to promote.
     */
    fun promotePending(): String? {
        if (!storage.hasDataset(storage.pendingDir)) return null
        val version = pendingVersion()
        storage.replaceDir(storage.pendingDir, storage.currentDir)
        Log.i(TAG, "Promoted dataset $version to current")
        return version
    }

    /** Identity of the active override (`current`), or null when running on the bundle. */
    fun activeVersion(): String? = provenanceIn(storage.currentDir)?.version

    /** Discards the active override so the app falls back to the bundle. */
    fun discardCurrent() {
        storage.deleteRecursively(storage.currentDir)
        Log.i(TAG, "Discarded current override; reverting to bundle")
    }

    private fun provenanceIn(dir: String): DatasetProvenance? {
        val raw = storage.readText("$dir/$PROVENANCE") ?: return null
        return try {
            json.decodeFromString(DatasetProvenance.serializer(), raw)
        } catch (_: Exception) {
            null
        }
    }

    private fun reject(reason: String): StageResult {
        Log.e(TAG, "Rejecting download: $reason")
        storage.deleteRecursively(storage.stagingDir)
        return StageResult.Rejected(reason)
    }
}
