package eu.dotshell.massilia.platform

import platform.Foundation.NSUserDefaults

/**
 * iOS implementation backed by NSUserDefaults for storing telemetry daily IDs.
 */
actual class SecureStorage actual constructor(context: PlatformContext, private val name: String) {

    private val defaults = NSUserDefaults(suiteName = name)

    actual fun getString(key: String): String? = defaults.stringForKey(key)

    actual fun putString(key: String, value: String) {
        defaults.setObject(value, forKey = key)
        defaults.synchronize()
    }

    actual fun getLong(key: String, defaultValue: Long): Long =
        if (defaults.objectForKey(key) != null) defaults.integerForKey(key) else defaultValue

    actual fun putLong(key: String, value: Long) {
        defaults.setInteger(value, forKey = key)
        defaults.synchronize()
    }

    actual fun remove(key: String) {
        defaults.removeObjectForKey(key)
        defaults.synchronize()
    }
}
