package eu.dotshell.pelo.generic.data.dataset

import eu.dotshell.pelo.platform.FileSystem
import eu.dotshell.pelo.platform.Log

/**
 * Resolves the files that make up a dataset — the pipeline's `dataset.json` and the
 * binary timetables under `raptor/` — from either a downloaded override or the bundle.
 *
 * The bundle is the permanent floor: a fresh install, an unreachable server, or a
 * format the server no longer publishes all leave the app fully working on its
 * shipped data. A downloaded dataset, once applied, simply takes precedence.
 *
 * The choice is made ONCE per instance, keyed on a sentinel file, not re-decided per
 * file. Reading `dataset.json` from the override while a `.bin` still came from the
 * bundle would pair a new manifest with stale timetables; picking one root up front
 * rules that out. Step 3's atomic swap is what guarantees the override tree is only
 * ever complete — here we only choose which root to read.
 */
class DatasetStore internal constructor(
    private val bundledBytes: (String) -> ByteArray,
    private val bundledText: (String) -> String,
    private val overrideBytes: (String) -> ByteArray?,
    private val overrideText: (String) -> String?,
    val usingOverride: Boolean,
) {
    /**
     * Reads a dataset file as raw bytes. Falls back to the bundle if an override is
     * active but this particular file is unexpectedly missing — degrading rather than
     * crashing; step 3's validation is what keeps a broken override from going active.
     */
    fun readBytes(path: String): ByteArray =
        (if (usingOverride) overrideBytes(path) else null) ?: bundledBytes(path)

    /** As [readBytes], for text files (`dataset.json`). */
    fun readText(path: String): String =
        (if (usingOverride) overrideText(path) else null) ?: bundledText(path)

    companion object {
        private const val TAG = "DatasetStore"

        /** Location, relative to `filesDir()`, of an applied downloaded dataset. */
        const val OVERRIDE_ROOT = "datasets/current"

        /**
         * A downloaded dataset is considered applied only when this file is present at
         * the override root. Step 3 writes it LAST, after every `.bin`, so its presence
         * means the whole tree is in place.
         */
        const val SENTINEL = "dataset.json"

        /** Wires the store to real platform I/O, deciding the active root once. */
        fun from(fileSystem: FileSystem): DatasetStore {
            val active = fileSystem.fileExists("$OVERRIDE_ROOT/$SENTINEL")
            if (active) Log.i(TAG, "Using downloaded dataset at $OVERRIDE_ROOT")
            return DatasetStore(
                bundledBytes = { fileSystem.readAssetBytes(it) },
                bundledText = { fileSystem.readAsset(it) },
                overrideBytes = { fileSystem.readFileBytes("$OVERRIDE_ROOT/$it") },
                overrideText = { fileSystem.readFileBytes("$OVERRIDE_ROOT/$it")?.decodeToString() },
                usingOverride = active,
            )
        }
    }
}
