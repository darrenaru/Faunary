package com.faunary.app.location

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.faunary.app.util.currentTimeMillis
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot

/** True when the phone has the fused rotation sensor (gyroscope + accelerometer + magnetometer). */
fun hasOrientationSensor(context: Context): Boolean =
    (context.getSystemService(Context.SENSOR_SERVICE) as SensorManager).getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null

/**
 * Live device orientation from the rotation-vector sensor (~50 Hz). [declinationAt] gives the
 * current position so magnetic north is corrected to true north, matching the map.
 */
fun deviceOrientation(context: Context, declinationAt: () -> GeoPoint?): Flow<DeviceOrientation> = callbackFlow {
    val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val sensor = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    if (sensor == null) {
        close()
        return@callbackFlow
    }
    val raw = FloatArray(9)
    val r = FloatArray(9)
    var declination = 0f
    var declinationFrom: GeoPoint? = null

    val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            SensorManager.getRotationMatrixFromVector(raw, event.values)
            // Express the matrix in screen axes, so "up" is the top of the screen in any rotation.
            val (ax, ay) = when (displayRotation(context)) {
                Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
            }
            SensorManager.remapCoordinateSystem(raw, ax, ay, r)
            // World frame: x = east, y = north, z = up. Screen-up (Y) and the back camera (-Z), projected
            // on the ground, both point "forward": their sum stays stable from flat to fully upright.
            val east = r[1] - r[2]
            val north = r[4] - r[5]
            if (hypot(east, north) < 1e-3f) return
            val here = declinationAt()
            if (here != null && here != declinationFrom) {
                declinationFrom = here
                declination = GeomagneticField(here.latitude.toFloat(), here.longitude.toFloat(), 0f, currentTimeMillis()).declination
            }
            val heading = (Math.toDegrees(atan2(east, north).toDouble()) + declination + 360.0) % 360.0
            val tilt = Math.toDegrees(acos(r[8].coerceIn(-1f, 1f).toDouble()))
            trySend(DeviceOrientation(heading, tilt.coerceIn(0.0, 90.0)))
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }
    sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
    awaitClose { sensors.unregisterListener(listener) }
}.conflate()

@Suppress("DEPRECATION")
private fun displayRotation(context: Context): Int =
    runCatching {
        // Context.display needs a visual (Activity) context; pass the one from the screen.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) context.display.rotation
        else (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
    }.getOrDefault(Surface.ROTATION_0)

@Composable
actual fun rememberOrientationSource(): OrientationSource? {
    val context = LocalContext.current
    return remember(context) {
        if (hasOrientationSensor(context)) OrientationSource { declinationAt -> deviceOrientation(context, declinationAt) } else null
    }
}
