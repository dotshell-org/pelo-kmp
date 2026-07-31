package eu.dotshell.pelo.platform

import android.widget.Toast

actual fun showToast(context: PlatformContext, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
