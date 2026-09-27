package com.faunary.app.ml

import android.graphics.Bitmap
import com.faunary.app.data.PhotoStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** [AnimalDetector] on Android: Gemini via [CloudAnimalDetector], else ML Kit via [OnDeviceAnimalDetector]. */
class AndroidAnimalDetector(
    private val photos: PhotoStorage,
    private val cloud: CloudAnimalDetector,
    private val onDevice: OnDeviceAnimalDetector,
) : AnimalDetector {
    override suspend fun detect(photoPath: String): DetectionResult? {
        val bitmap = runCatching { photos.loadForInference(photoPath) }.getOrNull() ?: return null
        try {
            val online = cloud.detect(withContext(Dispatchers.Default) { encode(bitmap) })
            if (online != null) return online.toDetectionResult()
            return DetectionResult(onDevice.detect(bitmap), DetectionSource.ON_DEVICE)
        } finally {
            bitmap.recycle()
        }
    }

    /** Downscaled JPEG for the online detector. */
    private fun encode(bitmap: Bitmap): ByteArray {
        val scale = CloudAnimalDetector.MAX_SIDE.toFloat() / max(bitmap.width, bitmap.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt(), true)
        } else bitmap
        val bytes = ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, CloudAnimalDetector.JPEG_QUALITY, out)
            out.toByteArray()
        }
        if (scaled !== bitmap) scaled.recycle()
        return bytes
    }
}
