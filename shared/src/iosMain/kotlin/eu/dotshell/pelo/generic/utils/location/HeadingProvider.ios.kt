@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package eu.dotshell.pelo.generic.utils.location

import eu.dotshell.pelo.platform.PlatformContext
import platform.CoreLocation.CLHeading
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.darwin.NSObject

/**
 * iOS actual backed by CLLocationManager heading updates, mirroring [LocationProvider]. Uses
 * `trueHeading` when available (falls back to `magneticHeading` when there is no location fix yet),
 * smoothed via [smoothHeading]. Heading requires location authorization, already requested by
 * [LocationProvider]. Not compilable on non-Apple hosts — kept in parity with LocationProvider.ios.
 */
actual class HeadingProvider actual constructor(context: PlatformContext) {

    private val manager = CLLocationManager()
    private var onHeading: ((Float) -> Unit)? = null
    private var smoothed: Float? = null

    private val headingDelegate = object : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManager(manager: CLLocationManager, didUpdateHeading: CLHeading) {
            val raw = didUpdateHeading.trueHeading
                .takeIf { it >= 0.0 }
                ?: didUpdateHeading.magneticHeading
            if (raw < 0.0) return // invalid reading
            val next = smoothHeading(smoothed, raw.toFloat(), SMOOTHING_ALPHA)
            smoothed = next
            onHeading?.invoke(next)
        }
    }

    init {
        manager.delegate = headingDelegate
        manager.headingFilter = MIN_DELTA_DEGREES // report only on ≥1° change
    }

    actual fun startUpdates(onHeading: (Float) -> Unit) {
        this.onHeading = onHeading
        smoothed = null
        manager.startUpdatingHeading()
    }

    actual fun stopUpdates() {
        manager.stopUpdatingHeading()
        onHeading = null
        smoothed = null
    }

    private companion object {
        const val SMOOTHING_ALPHA = 0.2f
        const val MIN_DELTA_DEGREES = 1.0 // CLLocationManager.headingFilter is in degrees (Double)
    }
}
