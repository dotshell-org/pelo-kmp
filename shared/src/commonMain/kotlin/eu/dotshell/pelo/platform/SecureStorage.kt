package eu.dotshell.pelo.platform

/**
 * Multiplatform encrypted key-value storage.
 * Android: EncryptedSharedPreferences (Keystore-backed), with a graceful fallback to
 * plain SharedPreferences if the Keystore is broken. iOS: NSUserDefaults (best-effort;
 * a Keychain-backed implementation can replace it later).
 */
expect class SecureStorage(context: PlatformContext, name: String) {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun getLong(key: String, defaultValue: Long): Long
    fun putLong(key: String, value: Long)
    fun remove(key: String)
}
