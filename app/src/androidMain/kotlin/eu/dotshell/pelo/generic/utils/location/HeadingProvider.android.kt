package eu.dotshell.pelo.generic.utils.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.Surface
import android.view.WindowManager
import eu.dotshell.pelo.platform.PlatformContext

/**
 * Android actual backed by the rotation-vector sensor. Converts the rotation matrix to an azimuth
 * (remapped for the current display rotation so it stays correct in landscape), then smooths and
 * rate-limits via [smoothHeading]/[angularDistance] so the caller isn't flooded at the sensor rate.
 * Motion sensors need no runtime permission.
 */
actual class HeadingProvider actual constructor(context: PlatformContext) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    @Suppress("DEPRECATION") // defaultDisplay still resolves on every API level from any context
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager

    private val rotationMatrix = FloatArray(9)
    private val remapped = FloatArray(9)
    private val orientation = FloatArray(3)

    private var smoothed: Float? = null
    private var lastEmitted: Float? = null
    private var lastEmitMs: Long = 0L

    private var listener: SensorEventListener? = null

    actual fun startUpdates(onHeading: (Float) -> Unit) {
        stopUpdates()
        val manager = sensorManager ?: return
        val sensor = rotationSensor ?: return // no magnetometer/rotation vector → never calls back

        listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                // Keep the azimuth correct when the display is rotated (landscape).
                val (axisX, axisY) = when (displayRotation()) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remapped)
                SensorManager.getOrientation(remapped, orientation)

                val rawDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                val next = smoothHeading(smoothed, rawDeg, SMOOTHING_ALPHA)
                smoothed = next

                val now = SystemClock.elapsedRealtime()
                val prev = lastEmitted
                if (prev == null || (angularDistance(prev, next) >= MIN_DELTA_DEGREES && now - lastEmitMs >= MIN_INTERVAL_MS)) {
                    lastEmitted = next
                    lastEmitMs = now
                    onHeading(next)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        // null handler → callbacks on the main thread, safe for updating Compose state.
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    actual fun stopUpdates() {
        listener?.let { sensorManager?.unregisterListener(it) }
        listener = null
        smoothed = null
        lastEmitted = null
        lastEmitMs = 0L
    }

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int = windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0

    private companion object {
        const val SMOOTHING_ALPHA = 0.2f
        const val MIN_DELTA_DEGREES = 1f
        const val MIN_INTERVAL_MS = 33L // cap at ~30 fps so a fast turn can't flood recomposition
    }
}
