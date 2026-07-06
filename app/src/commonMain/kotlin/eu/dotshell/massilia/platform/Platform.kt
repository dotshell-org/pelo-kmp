package eu.dotshell.massilia.platform

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Platform context abstraction.
 * On Android this wraps android.content.Context.
 * On iOS this is a no-op object.
 */
expect abstract class PlatformContext

/**
 * Provides the platform-specific HTTP client engine factory.
 */
expect fun createHttpClientEngine(): io.ktor.client.engine.HttpClientEngineFactory<*>

/**
 * Human-readable application version (e.g. "1.4.2"). Falls back to "unknown" if it
 * cannot be resolved. Android: PackageManager versionName. iOS: CFBundleShortVersionString.
 */
expect fun appVersionName(context: PlatformContext): String

/**
 * Dispatcher for blocking IO work. Android → Dispatchers.IO; iOS/Native → Dispatchers.Default.
 * (Dispatchers.IO is JVM-only / internal in commonMain — cf. KMP_IOS_HANDOFF §4.1.)
 */
expect val ioDispatcher: CoroutineDispatcher
