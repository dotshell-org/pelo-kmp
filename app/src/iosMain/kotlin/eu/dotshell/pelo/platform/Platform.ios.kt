package eu.dotshell.pelo.platform

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import platform.Foundation.NSBundle

actual abstract class PlatformContext

actual fun createHttpClientEngine(): HttpClientEngineFactory<*> = Darwin

actual fun appVersionName(context: PlatformContext): String =
    NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "unknown"

// TODO(iOS): a real check needs NWPathMonitor, which reports asynchronously — it does
// not fit this synchronous shape. Until that lands, return the conservative default
// (false), so iOS never auto-downloads a dataset over a possibly-metered link. The
// feature is effectively off on iOS, matching its currently unverified status.
actual fun isUnmeteredNetwork(context: PlatformContext): Boolean = false

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default

actual fun exportFile(context: PlatformContext, filename: String, content: String) {
    try {
        val tempDir = platform.Foundation.NSTemporaryDirectory()
        val filePath = tempDir + filename
        val url = platform.Foundation.NSURL.fileURLWithPath(filePath)
        
        val stringContent = content as platform.Foundation.NSString
        stringContent.writeToFile(filePath, atomically = true, encoding = platform.Foundation.NSUTF8StringEncoding, error = null)
        
        val activityViewController = platform.UIKit.UIActivityViewController(
            activityItems = listOf(url),
            applicationActivities = null
        )
        
        val window = platform.UIKit.UIApplication.sharedApplication.keyWindow
        val rootViewController = window?.rootViewController
        
        if (platform.UIKit.UIDevice.currentDevice.userInterfaceIdiom == platform.UIKit.UIUserInterfaceIdiomPad) {
            activityViewController.popoverPresentationController?.sourceView = rootViewController?.view
        }
        
        rootViewController?.presentViewController(activityViewController, animated = true, completion = null)
    } catch (e: Exception) {
        println("Export error: ${e.message}")
    }
}
