package com.faunary.app.location

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow

/** Where the phone points: [heading] in degrees from true north, [tilt] 0° lying flat … 90° held upright. */
data class DeviceOrientation(val heading: Double, val tilt: Double)

/** Live device orientation; [declinationAt] gives the current position (magnetic → true north). */
fun interface OrientationSource {
    fun orientation(declinationAt: () -> GeoPoint?): Flow<DeviceOrientation>
}

/** The phone's orientation sensor, or null when it has none (rotation vector on Android, CoreMotion on iOS). */
@Composable
expect fun rememberOrientationSource(): OrientationSource?
