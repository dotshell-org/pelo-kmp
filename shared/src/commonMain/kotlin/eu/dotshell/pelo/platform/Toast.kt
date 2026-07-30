package eu.dotshell.pelo.platform

/**
 * Shows a short, transient, fire-and-forget message to the user.
 * Android: Toast. iOS: best-effort (logs for now; a native overlay can replace it later).
 */
expect fun showToast(context: PlatformContext, message: String)
