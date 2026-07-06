package eu.dotshell.massilia.platform

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import platform.Foundation.NSBundle

actual abstract class PlatformContext

actual fun createHttpClientEngine(): HttpClientEngineFactory<*> = Darwin

actual fun appVersionName(context: PlatformContext): String =
    NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "unknown"

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
