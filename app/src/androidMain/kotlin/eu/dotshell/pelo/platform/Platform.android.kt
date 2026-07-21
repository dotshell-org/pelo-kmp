package eu.dotshell.pelo.platform

import android.content.Context
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual typealias PlatformContext = Context

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

actual fun createHttpClientEngine(): HttpClientEngineFactory<*> = OkHttp

actual fun isUnmeteredNetwork(context: PlatformContext): Boolean = try {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork)
    caps != null &&
        caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED) &&
        caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
} catch (_: Exception) {
    false
}

actual fun appVersionName(context: PlatformContext): String = try {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    info.versionName ?: "unknown"
} catch (e: Exception) {
    "unknown"
}

actual fun exportFile(context: PlatformContext, filename: String, content: String) {
    try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                showToast(context, "Fichier téléchargé dans les Téléchargements")
            } else {
                showToast(context, "Erreur lors du téléchargement")
            }
        } else {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("JSON", content)
            clipboard.setPrimaryClip(clip)
            showToast(context, "Copié dans le presse-papiers (version Android trop ancienne)")
        }
    } catch (e: Exception) {
        showToast(context, "Erreur : ${e.message}")
    }
}
