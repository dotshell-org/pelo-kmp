package eu.dotshell.pelo.platform

actual fun showToast(context: PlatformContext, message: String) {
    // Best-effort on iOS for now — a UIAlertController/overlay can replace this later.
    Log.i("Toast", message)
}
