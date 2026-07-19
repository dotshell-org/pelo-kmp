package eu.dotshell.pelo.generic.utils.location

import eu.dotshell.pelo.platform.PlatformContext

/**
 * Cross-platform device heading (compass) access, mirroring [LocationProvider]. Emits the direction
 * the top of the device points, in degrees clockwise from north ([0, 360)), already smoothed and
 * rate-limited so callers can drop it straight into UI state. Android reads the rotation-vector
 * sensor (no runtime permission); iOS reads CLLocationManager heading (needs location permission).
 * Devices without a magnetometer simply never call back.
 */
expect class HeadingProvider(context: PlatformContext) {

    /** Start receiving heading updates (degrees clockwise from north, [0, 360)). */
    fun startUpdates(onHeading: (Float) -> Unit)

    /** Stop receiving heading updates and release the sensor/manager. */
    fun stopUpdates()
}
