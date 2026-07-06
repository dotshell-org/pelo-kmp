package eu.dotshell.massilia.platform

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

actual class Settings actual constructor(context: PlatformContext, name: String) {
    private val prefs: SharedPreferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    actual fun getString(key: String, defaultValue: String): String =
        prefs.getString(key, defaultValue) ?: defaultValue

    actual fun putString(key: String, value: String) = prefs.edit { putString(key, value) }

    actual fun getInt(key: String, defaultValue: Int): Int = prefs.getInt(key, defaultValue)

    actual fun putInt(key: String, value: Int) = prefs.edit { putInt(key, value) }

    actual fun getLong(key: String, defaultValue: Long): Long = prefs.getLong(key, defaultValue)

    actual fun putLong(key: String, value: Long) = prefs.edit { putLong(key, value) }

    actual fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        prefs.getBoolean(key, defaultValue)

    actual fun putBoolean(key: String, value: Boolean) = prefs.edit { putBoolean(key, value) }

    actual fun getFloat(key: String, defaultValue: Float): Float =
        prefs.getFloat(key, defaultValue)

    actual fun putFloat(key: String, value: Float) = prefs.edit { putFloat(key, value) }

    actual fun contains(key: String): Boolean = prefs.contains(key)

    actual fun remove(key: String) = prefs.edit { remove(key) }

    actual fun clear() = prefs.edit { clear() }

    actual fun getStringSet(key: String, defaultValue: Set<String>): Set<String> =
        prefs.getStringSet(key, defaultValue) ?: defaultValue

    actual fun putStringSet(key: String, value: Set<String>) =
        prefs.edit { putStringSet(key, value) }
}
