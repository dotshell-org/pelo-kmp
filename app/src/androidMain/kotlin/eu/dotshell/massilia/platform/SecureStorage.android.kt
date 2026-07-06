package eu.dotshell.massilia.platform

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

actual class SecureStorage actual constructor(context: PlatformContext, name: String) {

    private val prefs: SharedPreferences = buildPrefs(context, name)

    actual fun getString(key: String): String? = prefs.getString(key, null)

    actual fun putString(key: String, value: String) = prefs.edit { putString(key, value) }

    actual fun getLong(key: String, defaultValue: Long): Long = prefs.getLong(key, defaultValue)

    actual fun putLong(key: String, value: Long) = prefs.edit { putLong(key, value) }

    actual fun remove(key: String) = prefs.edit { remove(key) }

    private fun buildPrefs(context: Context, name: String): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                name,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w("SecureStorage", "EncryptedSharedPreferences unavailable, falling back to plain prefs", e)
            context.getSharedPreferences("${name}_plain", Context.MODE_PRIVATE)
        }
    }
}
