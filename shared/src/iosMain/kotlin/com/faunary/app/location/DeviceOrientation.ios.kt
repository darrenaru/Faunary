package com.faunary.app.location

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.useContents
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import platform.CoreMotion.CMAttitudeReferenceFrameXTrueNorthZVertical
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSOperationQueue
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Device orientation from CoreMotion, in the true-north frame (so no declination is needed): the same
 * "screen-up + back camera" heading and tilt as Android's rotation-vector code.
 */
@Composable
actual fun rememberOrientationSource(): OrientationSource? = remember {
    val available = CMMotionManager.availableAttitudeReferenceFrames() and CMAttitudeReferenceFrameXTrueNorthZVertical != 0uL
    if (!available) null else OrientationSource { _ ->
        callbackFlow {
            val motion = CMMotionManager().apply { deviceMotionUpdateInterval = 1.0 / 50 }
            motion.startDeviceMotionUpdatesUsingReferenceFrame(CMAttitudeReferenceFrameXTrueNorthZVertical, NSOperationQueue.mainQueue) { data, _ ->
                // Rows of the rotation matrix are the device axes in the reference frame (x = north, y = west, z = up).
                data?.attitude?.rotationMatrix?.useContents {
                    val north = m21 - m31
                    val east = -(m22 - m32)
                    if (hypot(east, north) < 1e-3) return@useContents
                    val heading = (atan2(east, north) * 180 / PI + 360) % 360
                    val tilt = acos(m33.coerceIn(-1.0, 1.0)) * 180 / PI
                    trySend(DeviceOrientation(heading, tilt.coerceIn(0.0, 90.0)))
                }
            }
            awaitClose { motion.stopDeviceMotionUpdates() }
        }.conflate()
    }
}
