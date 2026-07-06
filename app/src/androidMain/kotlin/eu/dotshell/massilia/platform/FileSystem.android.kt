package eu.dotshell.massilia.platform

import java.io.File
import java.io.InputStream
import java.io.InputStreamReader

actual class FileSystem actual constructor(private val context: PlatformContext) {

    actual fun readAsset(path: String): String {
        return openAsset(path).use { stream ->
            InputStreamReader(stream).readText()
        }
    }

    actual fun readAssetBytes(path: String): ByteArray {
        return openAsset(path).use { it.readBytes() }
    }

    actual fun assetExists(path: String): Boolean {
        return try {
            openAsset(path).use { true }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Opens a bundled asset. Files declared under `commonMain/composeResources/files/` are packed
     * by Compose Resources into the APK at `assets/composeResources/<resourcePackage>/files/<path>`,
     * NOT at the assets root. We resolve that location (discovering the resource-package directory
     * at runtime so it's not hard-coded) and fall back to the raw assets root for anything placed
     * directly under `assets/`.
     */
    private fun openAsset(path: String): InputStream {
        val resolved = composeResourcesFilePath(path)
        if (resolved != null) {
            try {
                return context.assets.open(resolved)
            } catch (_: Exception) {
                // fall through to the raw root path
            }
        }
        return context.assets.open(path)
    }

    private val composeResourcesDir: String? by lazy {
        runCatching {
            context.assets.list("composeResources")?.firstOrNull { it.isNotBlank() }
        }.getOrNull()
    }

    private fun composeResourcesFilePath(path: String): String? {
        val dir = composeResourcesDir ?: return null
        return "composeResources/$dir/files/$path"
    }

    actual fun filesDir(): String = context.filesDir.absolutePath

    actual fun cacheDir(): String = context.cacheDir.absolutePath

    actual fun readFile(path: String): String? {
        val file = File(context.filesDir, path)
        return if (file.exists()) file.readText() else null
    }

    actual fun writeFile(path: String, content: String) {
        val file = File(context.filesDir, path)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    actual fun deleteFile(path: String): Boolean {
        return File(context.filesDir, path).delete()
    }

    actual fun fileExists(path: String): Boolean {
        return File(context.filesDir, path).exists()
    }
}
