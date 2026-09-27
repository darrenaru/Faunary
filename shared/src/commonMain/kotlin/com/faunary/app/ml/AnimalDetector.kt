package com.faunary.app.ml

import com.faunary.app.data.Detection

/** Where a detection result came from. */
enum class DetectionSource { ONLINE, ON_DEVICE }

data class DetectionResult(
    val detections: List<Detection>,
    val source: DetectionSource,
    /** Only a picture of an animal was found (screen, poster, toy…), not a real one. */
    val depictionOnly: Boolean = false,
)

/**
 * Animal detection: identified online (Gemini, via [CloudAnimalDetector]) when possible, otherwise on the
 * phone (ML Kit on Android, Apple Vision on iOS) — offline, unconfigured, quota used up, or errors.
 */
interface AnimalDetector {
    /** Null when the photo can't be read. */
    suspend fun detect(photoPath: String): DetectionResult?
}

/** Result of the online detector, shaped the same on every platform. */
fun CloudAnimalDetector.Result.toDetectionResult() = DetectionResult(
    detections.filter { it.label.isNotBlank() }.sortedByDescending { it.confidence },
    DetectionSource.ONLINE,
    depictionOnly,
)
