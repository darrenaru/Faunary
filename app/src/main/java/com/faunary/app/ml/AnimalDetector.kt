package com.faunary.app.ml

import android.graphics.Bitmap
import com.faunary.app.data.Detection
import javax.inject.Inject
import javax.inject.Singleton

/** Where a detection result came from. */
enum class DetectionSource { ONLINE, ON_DEVICE }

data class DetectionResult(
    val detections: List<Detection>,
    val source: DetectionSource,
    /** Only a picture of an animal was found (screen, poster, toy…), not a real one. */
    val depictionOnly: Boolean = false,
)

/**
 * Animal detection: identified online (Gemini, via [CloudAnimalDetector]) when possible, otherwise
 * on the phone with ML Kit ([OnDeviceAnimalDetector]) — offline, unconfigured, quota used up, or errors.
 */
@Singleton
class AnimalDetector @Inject constructor(
    private val cloud: CloudAnimalDetector,
    private val onDevice: OnDeviceAnimalDetector,
) {
    suspend fun detect(bitmap: Bitmap): DetectionResult {
        val online = cloud.detect(bitmap)
        if (online != null) {
            return DetectionResult(
                online.detections.filter { it.label.isNotBlank() }.sortedByDescending { it.confidence },
                DetectionSource.ONLINE,
                online.depictionOnly,
            )
        }
        return DetectionResult(onDevice.detect(bitmap), DetectionSource.ON_DEVICE)
    }
}
