package eu.dotshell.massilia.platform

import android.content.Context
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual typealias PlatformContext = Context

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

actual fun createHttpClientEngine(): HttpClientEngineFactory<*> = OkHttp

actual fun appVersionName(context: PlatformContext): String = try {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    info.versionName ?: "unknown"
} catch (e: Exception) {
    "unknown"
}
