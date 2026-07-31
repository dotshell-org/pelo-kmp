package eu.dotshell.pelo.platform

/**
 * Multiplatform key-value storage abstraction (replaces SharedPreferences).
 */
expect class Settings(context: PlatformContext, name: String) {
    fun getString(key: String, defaultValue: String): String
    fun putString(key: String, value: String)
    fun getInt(key: String, defaultValue: Int): Int
    fun putInt(key: String, value: Int)
    fun getLong(key: String, defaultValue: Long): Long
    fun putLong(key: String, value: Long)
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getFloat(key: String, defaultValue: Float): Float
    fun putFloat(key: String, value: Float)
    fun contains(key: String): Boolean
    fun remove(key: String)
    fun clear()
    fun getStringSet(key: String, defaultValue: Set<String>): Set<String>
    fun putStringSet(key: String, value: Set<String>)
}
