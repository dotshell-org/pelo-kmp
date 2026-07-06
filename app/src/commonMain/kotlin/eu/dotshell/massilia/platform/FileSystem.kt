package eu.dotshell.massilia.platform

/**
 * Multiplatform file system abstraction for app-local storage.
 */
expect class FileSystem(context: PlatformContext) {
    /**
     * Read a bundled resource/asset file as a string.
     */
    fun readAsset(path: String): String

    /**
     * Read a bundled resource/asset file as bytes.
     */
    fun readAssetBytes(path: String): ByteArray

    /**
     * Check if a bundled asset exists.
     */
    fun assetExists(path: String): Boolean

    /**
     * Get the app-private files directory path.
     */
    fun filesDir(): String

    /**
     * Get the app-private cache directory path.
     */
    fun cacheDir(): String

    /**
     * Read a file from the app-private files directory.
     */
    fun readFile(path: String): String?

    /**
     * Write a file to the app-private files directory.
     */
    fun writeFile(path: String, content: String)

    /**
     * Delete a file from the app-private files directory.
     */
    fun deleteFile(path: String): Boolean

    /**
     * Check if a file exists in the app-private files directory.
     */
    fun fileExists(path: String): Boolean
}
