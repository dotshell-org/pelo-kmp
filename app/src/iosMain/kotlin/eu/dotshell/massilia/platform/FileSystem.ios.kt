@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package eu.dotshell.massilia.platform

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.memcpy
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.create

actual class FileSystem actual constructor(private val context: PlatformContext) {

    actual fun readAsset(path: String): String {
        val bundlePath = assetBundlePath(path)
        requireNotNull(bundlePath) { "Asset not found: $path" }
        return NSString.stringWithContentsOfFile(bundlePath, encoding = NSUTF8StringEncoding, error = null)
            ?: error("Failed to read asset: $path")
    }

    actual fun readAssetBytes(path: String): ByteArray {
        val bundlePath = assetBundlePath(path)
        requireNotNull(bundlePath) { "Asset not found: $path" }
        val data = NSData.dataWithContentsOfFile(bundlePath)
            ?: error("Failed to read asset bytes: $path")
        val length = data.length.toInt()
        if (length == 0) return ByteArray(0)
        return ByteArray(length).apply {
            usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, data.length) }
        }
    }

    actual fun assetExists(path: String): Boolean = assetBundlePath(path) != null

    /**
     * Compose Resources `files/` assets are copied into the app bundle under [RESOURCE_ROOT]
     * by an iosApp build phase (the static framework doesn't embed them). Resolves a relative
     * asset path (e.g. "config.json", "raptor/stops_saturday.bin") to its on-disk bundle path.
     */
    private fun assetBundlePath(path: String): String? {
        val components = path.split("/")
        val fileName = components.last()
        val nameComponents = fileName.split(".")
        val name = nameComponents.dropLast(1).joinToString(".")
        val ext = nameComponents.lastOrNull() ?: ""
        val subDir = components.dropLast(1).joinToString("/")
        val dir = if (subDir.isEmpty()) RESOURCE_ROOT else "$RESOURCE_ROOT/$subDir"
        return NSBundle.mainBundle.pathForResource(name, ofType = ext, inDirectory = dir)
    }

    private companion object {
        // Same bundle layout the Compose Resources `Res` API uses on iOS, so one build-phase
        // copy serves both: <bundle>/compose-resources/composeResources/<packageOfResClass>/files/
        const val RESOURCE_ROOT = "compose-resources/composeResources/eu.dotshell.massilia.resources/files"
    }

    actual fun filesDir(): String {
        return NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory, NSUserDomainMask, true
        ).first() as String
    }

    actual fun cacheDir(): String {
        return NSSearchPathForDirectoriesInDomains(
            NSCachesDirectory, NSUserDomainMask, true
        ).first() as String
    }

    actual fun readFile(path: String): String? {
        val fullPath = "${filesDir()}/$path"
        return if (NSFileManager.defaultManager.fileExistsAtPath(fullPath)) {
            NSString.stringWithContentsOfFile(fullPath, encoding = NSUTF8StringEncoding, error = null)
        } else null
    }

    @Suppress("CAST_NEVER_SUCCEEDS")
    actual fun writeFile(path: String, content: String) {
        val fullPath = "${filesDir()}/$path"
        val dir = fullPath.substringBeforeLast("/")
        NSFileManager.defaultManager.createDirectoryAtPath(
            dir, withIntermediateDirectories = true, attributes = null, error = null
        )
        (content as NSString).writeToFile(fullPath, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }

    actual fun deleteFile(path: String): Boolean {
        val fullPath = "${filesDir()}/$path"
        return NSFileManager.defaultManager.removeItemAtPath(fullPath, error = null)
    }

    actual fun fileExists(path: String): Boolean {
        val fullPath = "${filesDir()}/$path"
        return NSFileManager.defaultManager.fileExistsAtPath(fullPath)
    }
}
