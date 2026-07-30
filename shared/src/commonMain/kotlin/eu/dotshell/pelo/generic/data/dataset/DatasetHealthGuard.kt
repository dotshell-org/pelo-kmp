package eu.dotshell.pelo.generic.data.dataset

import eu.dotshell.pelo.platform.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class DatasetHealth(
    /** The override version the [fails] count refers to; null when on the bundle. */
    val version: String? = null,
    val fails: Int = 0,
    /** Versions that broke init and must not be downloaded again. */
    val quarantine: List<String> = emptyList()
)

/** What a caller should do with the active override before trusting it to load. */
enum class InitPlan { PROCEED, REVERT }

/**
 * Keeps a downloaded dataset from bricking the app. A verified download can still be
 * semantically broken in a way that only surfaces when RaptorLibrary tries to load it;
 * this guard notices repeated init failures against the same version and falls the app
 * back to the bundle, quarantining the bad version so it is not fetched again.
 *
 * The failure count is written AHEAD of each init attempt, so a hard crash mid-load
 * still counts — that is the case a caught exception would miss.
 */
class DatasetHealthGuard(
    private val storage: DatasetStorage,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private companion object {
        const val TAG = "DatasetHealthGuard"
        const val MAX_FAILS = 2
    }

    // The version already counted in THIS process. The failure tally must grow once per
    // launch, not once per attempt: an in-session retry of initialize() must not inflate
    // it, or two transient failures in one run could quarantine a perfectly good dataset.
    private var countedVersion: String? = null

    fun isQuarantined(version: String): Boolean = load().quarantine.contains(version)

    /**
     * Records that an init is about to be attempted against [activeVersion] and says
     * whether to trust it. Returns [InitPlan.REVERT] once a version has failed
     * [MAX_FAILS] times; the caller then discards the override and calls [quarantine].
     * On the bundle ([activeVersion] null) there is nothing to guard.
     */
    fun beforeInit(activeVersion: String?): InitPlan {
        if (activeVersion == null) return InitPlan.PROCEED
        val health = load()
        val priorFails = if (health.version == activeVersion) health.fails else 0
        if (priorFails >= MAX_FAILS) {
            Log.e(TAG, "Dataset $activeVersion failed init $priorFails times; reverting to bundle")
            return InitPlan.REVERT
        }
        if (countedVersion != activeVersion) {
            save(health.copy(version = activeVersion, fails = priorFails + 1))
            countedVersion = activeVersion
        }
        return InitPlan.PROCEED
    }

    /** Clears the failure count after a successful load of [activeVersion]. */
    fun recordSuccess(activeVersion: String?) {
        if (activeVersion == null) return
        save(load().copy(version = activeVersion, fails = 0))
        // Cleared: a later failure in a subsequent launch should count again.
        countedVersion = null
    }

    /** Bans [version] from future downloads and clears the failure count. */
    fun quarantine(version: String) {
        val health = load()
        val banned = (health.quarantine + version).distinct()
        save(DatasetHealth(version = null, fails = 0, quarantine = banned))
        Log.i(TAG, "Quarantined dataset $version")
    }

    private fun load(): DatasetHealth {
        val raw = storage.readText(storage.healthFile) ?: return DatasetHealth()
        return try {
            json.decodeFromString(DatasetHealth.serializer(), raw)
        } catch (_: Exception) {
            DatasetHealth()
        }
    }

    private fun save(health: DatasetHealth) {
        storage.writeBytes(
            storage.healthFile,
            json.encodeToString(DatasetHealth.serializer(), health).encodeToByteArray()
        )
    }
}
