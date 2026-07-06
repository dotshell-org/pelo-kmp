package eu.dotshell.massilia.platform

import platform.Foundation.NSUserDefaults

actual class Settings actual constructor(context: PlatformContext, name: String) {
    private val defaults = NSUserDefaults(suiteName = name)

    actual fun getString(key: String, defaultValue: String): String =
        defaults.stringForKey(key) ?: defaultValue

    actual fun putString(key: String, value: String) = defaults.setObject(value, forKey = key)

    actual fun getInt(key: String, defaultValue: Int): Int =
        if (defaults.objectForKey(key) != null) defaults.integerForKey(key).toInt() else defaultValue

    actual fun putInt(key: String, value: Int) = defaults.setInteger(value.toLong(), forKey = key)

    actual fun getLong(key: String, defaultValue: Long): Long =
        if (defaults.objectForKey(key) != null) defaults.integerForKey(key) else defaultValue

    actual fun putLong(key: String, value: Long) = defaults.setInteger(value, forKey = key)

    actual fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        if (defaults.objectForKey(key) != null) defaults.boolForKey(key) else defaultValue

    actual fun putBoolean(key: String, value: Boolean) = defaults.setBool(value, forKey = key)

    actual fun getFloat(key: String, defaultValue: Float): Float =
        if (defaults.objectForKey(key) != null) defaults.floatForKey(key) else defaultValue

    actual fun putFloat(key: String, value: Float) = defaults.setFloat(value, forKey = key)

    actual fun contains(key: String): Boolean = defaults.objectForKey(key) != null

    actual fun remove(key: String) = defaults.removeObjectForKey(key)

    actual fun clear() {
        defaults.dictionaryRepresentation().keys.forEach { key ->
            defaults.removeObjectForKey(key as String)
        }
    }

    actual fun getStringSet(key: String, defaultValue: Set<String>): Set<String> {
        val array = defaults.arrayForKey(key) ?: return defaultValue
        // filterIsInstance instead of an unchecked `as List<String>`: a stored array that isn't
        // all strings (legacy/corrupt data) would otherwise crash on the cast.
        return array.filterIsInstance<String>().toSet()
    }

    actual fun putStringSet(key: String, value: Set<String>) =
        defaults.setObject(value.toList(), forKey = key)
}
