package eu.dotshell.massilia.generic.data.offline

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.gzip

/**
 * Cross-platform gzip + UTF-8 file IO helpers built on okio (replaces
 * java.io.File + java.util.zip.GZIP*Stream). Works on Android (JVM) and iOS
 * (Native) via [FileSystem.SYSTEM]. Paths are plain strings rooted at the
 * app-private dir provided by [eu.dotshell.massilia.platform.FileSystem.filesDir].
 */
internal object GzipFileStore {

    private val fs: FileSystem = FileSystem.SYSTEM

    fun ensureDir(dir: String) {
        fs.createDirectories(dir.toPath())
    }

    fun writeGzip(filePath: String, content: String) {
        val path = filePath.toPath()
        path.parent?.let { fs.createDirectories(it) }
        val sink = fs.sink(path).gzip().buffer()
        try {
            sink.writeUtf8(content)
        } finally {
            sink.close()
        }
    }

    fun readGzip(filePath: String): String? {
        val path = filePath.toPath()
        if (!fs.exists(path)) return null
        val source = fs.source(path).gzip().buffer()
        return try {
            source.readUtf8()
        } finally {
            source.close()
        }
    }

    fun delete(filePath: String) {
        val path = filePath.toPath()
        if (fs.exists(path)) fs.delete(path)
    }

    fun exists(filePath: String): Boolean = fs.exists(filePath.toPath())

    /** Lists the entries of [dir], or an empty list if it does not exist. */
    fun list(dir: String): List<Path> {
        val path = dir.toPath()
        return if (fs.exists(path)) fs.list(path) else emptyList()
    }

    fun size(filePath: String): Long {
        val path = filePath.toPath()
        return if (fs.exists(path)) fs.metadata(path).size ?: 0L else 0L
    }

    fun lastModified(filePath: String): Long {
        val path = filePath.toPath()
        return if (fs.exists(path)) fs.metadata(path).lastModifiedAtMillis ?: 0L else 0L
    }
}
