package eu.dotshell.pelo.generic.data.dataset

import eu.dotshell.pelo.platform.FileSystem
import eu.dotshell.pelo.platform.Log
import kotlin.concurrent.Volatile
import kotlinx.serialization.json.Json

/**
 * Loads the bundled `dataset.json` written by raptor-gtfs-pipeline next to the
 * binary timetables under `raptor/`.
 *
 * Unlike `config.json`, this is NON-ESSENTIAL metadata: the app routes perfectly
 * well without it. So every failure path returns null instead of throwing — a
 * missing or malformed file must degrade to "we don't display a validity date",
 * never to a broken app. Datasets produced before this file existed are exactly
 * that case.
 */
object DatasetInfoLoader {
    private const val TAG = "DatasetInfoLoader"
    private const val DATASET_FILE = "dataset.json"

    @Volatile
    private var cached: DatasetInfo? = null

    @Volatile
    private var attempted = false

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /** Returns the parsed dataset metadata, or null when it is absent or unreadable. */
    fun load(fileSystem: FileSystem): DatasetInfo? {
        cached?.let { return it }
        if (attempted) return null
        attempted = true

        return try {
            val store = DatasetStore.from(fileSystem)
            // Present if a downloaded dataset is active (its sentinel IS dataset.json) or
            // the bundle ships one. Datasets built before this file existed have neither.
            val present = store.usingOverride || fileSystem.assetExists(DATASET_FILE)
            if (!present) {
                Log.i(TAG, "$DATASET_FILE not available; skipping dataset metadata")
                return null
            }
            val parsed = json.decodeFromString(DatasetInfo.serializer(), store.readText(DATASET_FILE))
            cached = parsed
            parsed
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load $DATASET_FILE; continuing without it", e)
            null
        }
    }
}
