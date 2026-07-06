package eu.dotshell.massilia.generic.utils.location

import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import eu.dotshell.massilia.platform.PlatformContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

actual class LocationProvider actual constructor(context: PlatformContext) {

    private val client = LocationServices.getFusedLocationProviderClient(context)
    private var callback: LocationCallback? = null

    @Suppress("MissingPermission") // permission checked by the caller
    actual suspend fun getLastKnownLocation(): GeoPoint? {
        return try {
            suspendCancellableCoroutine { continuation ->
                client.lastLocation
                    .addOnSuccessListener { location ->
                        continuation.resume(location?.let { GeoPoint(it.latitude, it.longitude) })
                    }
                    .addOnFailureListener { continuation.resume(null) }
            }
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("MissingPermission") // permission checked by the caller
    actual fun startUpdates(onLocation: (GeoPoint) -> Unit) {
        // Idempotent: remove any previously registered callback first, otherwise a second
        // startUpdates() leaks the old callback (it keeps receiving updates and stopUpdates()
        // only ever removes the most recent one).
        stopUpdates()
        try {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L).apply {
                setMinUpdateIntervalMillis(3000L)
                setWaitForAccurateLocation(false)
            }.build()
            callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { onLocation(GeoPoint(it.latitude, it.longitude)) }
                }
            }
            client.requestLocationUpdates(request, callback!!, Looper.getMainLooper())
        } catch (_: SecurityException) {
            // permission denied — no-op
        }
    }

    actual fun stopUpdates() {
        callback?.let {
            client.removeLocationUpdates(it)
            callback = null
        }
    }
}
