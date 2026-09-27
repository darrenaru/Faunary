package com.faunary.app.ml

import android.graphics.Bitmap
import android.graphics.Rect
import com.faunary.app.data.Detection
import com.faunary.app.domain.Species
import com.faunary.app.domain.SpeciesCatalog
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * On-device animal detection, used when the online detector ([CloudAnimalDetector]) isn't available.
 *
 * ML Kit's object detector finds *where* things are but its built-in classifier has no animal
 * classes, so each detected box is cropped and passed to the image labeler (400+ labels incl.
 * Cat/Dog/Bird). If no box yields an animal, the whole frame is labelled as a fallback.
 */
class OnDeviceAnimalDetector {

    private val objectDetector by lazy {
        ObjectDetection.getClient(
            ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                .enableMultipleObjects()
                .build(),
        )
    }

    private val labeler by lazy {
        ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.3f).build())
    }

    suspend fun detect(bitmap: Bitmap): List<Detection> = withContext(Dispatchers.Default) {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val objects = runCatching { objectDetector.process(InputImage.fromBitmap(bitmap, 0)).await() }
            .getOrDefault(emptyList())
            .sortedByDescending { it.boundingBox.width() * it.boundingBox.height() }
            .take(MAX_OBJECTS)

        val results = mutableListOf<Detection>()
        for (obj in objects) {
            val crop = cropPadded(bitmap, obj.boundingBox) ?: continue
            val best = bestAnimal(crop)
            if (crop !== bitmap) crop.recycle()
            if (best != null && best.second >= MIN_CONFIDENCE) {
                val box = obj.boundingBox
                results += Detection(
                    label = best.first.displayName,
                    category = best.first.category.name,
                    confidence = best.second,
                    left = (box.left / w).coerceIn(0f, 1f),
                    top = (box.top / h).coerceIn(0f, 1f),
                    right = (box.right / w).coerceIn(0f, 1f),
                    bottom = (box.bottom / h).coerceIn(0f, 1f),
                    rawLabel = best.third,
                )
            }
        }

        if (results.isEmpty()) {
            // Fallback: classify the whole frame and attach it to the largest object, if any.
            val best = bestAnimal(bitmap)
            if (best != null && best.second >= MIN_CONFIDENCE) {
                val box = objects.firstOrNull()?.boundingBox
                results += Detection(
                    label = best.first.displayName,
                    category = best.first.category.name,
                    confidence = best.second,
                    left = box?.let { it.left / w } ?: 0f,
                    top = box?.let { it.top / h } ?: 0f,
                    right = box?.let { it.right / w } ?: 0f,
                    bottom = box?.let { it.bottom / h } ?: 0f,
                    rawLabel = best.third,
                )
            }
        }
        results.sortedByDescending { it.confidence }
    }

    /** Returns (species, confidence, rawLabel) for the most likely animal label, or null. */
    private suspend fun bestAnimal(image: Bitmap): Triple<Species, Float, String>? {
        val labels = runCatching { labeler.process(InputImage.fromBitmap(image, 0)).await() }
            .getOrDefault(emptyList())
        return labels.mapNotNull { l -> SpeciesCatalog.lookup(l.text)?.let { Triple(it, l.confidence, l.text) } }
            // Prefer specific labels ("Cat") over generic ones ("Pet") unless much more confident.
            .maxByOrNull { (species, conf, _) -> conf + species.priority * 0.02f }
    }

    private fun cropPadded(src: Bitmap, box: Rect): Bitmap? {
        val padX = (box.width() * 0.1f).toInt()
        val padY = (box.height() * 0.1f).toInt()
        val l = max(0, box.left - padX)
        val t = max(0, box.top - padY)
        val r = min(src.width, box.right + padX)
        val b = min(src.height, box.bottom + padY)
        if (r - l < 16 || b - t < 16) return null
        return Bitmap.createBitmap(src, l, t, r - l, b - t)
    }

    private companion object {
        const val MAX_OBJECTS = 5
        /** The base labeler guesses wildly below this; lower-confidence labels are ignored. */
        const val MIN_CONFIDENCE = 0.5f
    }
}
