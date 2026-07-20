package eu.dotshell.pelo.generic.data.dataset

import okio.FileSystem
import okio.HashingSink
import okio.Path
import okio.Path.Companion.toPath
import okio.blackholeSink
import okio.buffer

/**
 * okio-backed file operations for the downloaded-dataset lifecycle, rooted at a
 * single directory so tests can point it at a temp dir and the app at `filesDir`.
 *
 * Layout under [root]:
 *
 *   current/    the applied dataset DatasetStore reads (dataset.json + raptor/…)
 *   pending/    a fully downloaded, verified dataset awaiting promotion at cold start
 *   staging/    an in-progress download; never read, cleared on start
 *   current.trash/  transient, only exists mid-swap
 *   health.json     init-health + quarantine bookkeeping
 *
 * `current`, `pending` and `staging` are siblings under one root, hence one
 * filesystem, so `atomicMove` between them is a real atomic rename.
 */
class DatasetStorage(val root: String, private val fs: FileSystem = FileSystem.SYSTEM) {

    val currentDir: String get() = "$root/current"
    val pendingDir: String get() = "$root/pending"
    val stagingDir: String get() = "$root/staging"
    private val trashDir: String get() = "$root/current.trash"
    val healthFile: String get() = "$root/health.json"

    private fun Path.exists() = fs.exists(this)

    fun exists(path: String): Boolean = path.toPath().exists()

    /** True when a directory holds a complete applied dataset (its sentinel is present). */
    fun hasDataset(dir: String): Boolean = exists("$dir/${DatasetStore.SENTINEL}")

    fun ensureDir(dir: String) = fs.createDirectories(dir.toPath())

    fun deleteRecursively(dir: String) {
        val p = dir.toPath()
        if (p.exists()) fs.deleteRecursively(p)
    }

    fun writeBytes(path: String, bytes: ByteArray) {
        val p = path.toPath()
        p.parent?.let { fs.createDirectories(it) }
        fs.write(p) { write(bytes) }
    }

    fun readText(path: String): String? {
        val p = path.toPath()
        if (!p.exists()) return null
        return fs.read(p) { readUtf8() }
    }

    /** Lowercase hex SHA-256 of a file, streamed so a large `.bin` is never fully in memory. */
    fun sha256(path: String): String {
        val hashing = HashingSink.sha256(blackholeSink())
        fs.source(path.toPath()).buffer().use { source ->
            hashing.buffer().use { sink -> source.readAll(sink) }
        }
        return hashing.hash.hex()
    }

    fun sizeOf(path: String): Long = fs.metadata(path.toPath()).size ?: -1L

    /**
     * Replaces [dstDir] with [srcDir] as atomically as the filesystem allows: the
     * destination is renamed aside first, the source renamed into place, then the old
     * one deleted. [recover] repairs the one crash window this leaves.
     */
    fun replaceDir(srcDir: String, dstDir: String) {
        deleteRecursively(trashDir)
        if (dstDir.toPath().exists()) fs.atomicMove(dstDir.toPath(), trashDir.toPath())
        dstDir.toPath().parent?.let { fs.createDirectories(it) }
        fs.atomicMove(srcDir.toPath(), dstDir.toPath())
        deleteRecursively(trashDir)
    }

    /**
     * Repairs the interrupted state [replaceDir] can leave and clears scratch dirs.
     * Idempotent, safe to run at every startup before anything reads `current`.
     */
    fun recover() {
        // A swap died after moving current aside but before/around moving the new one in.
        if (!currentDir.toPath().exists() && trashDir.toPath().exists()) {
            fs.atomicMove(trashDir.toPath(), currentDir.toPath())
        }
        deleteRecursively(trashDir)
        deleteRecursively(stagingDir)
    }
}
