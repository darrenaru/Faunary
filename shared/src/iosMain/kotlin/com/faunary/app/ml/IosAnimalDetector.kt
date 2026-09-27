package com.faunary.app.ml

import com.faunary.app.data.Detection
import com.faunary.app.domain.SpeciesCatalog
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.Vision.VNClassificationObservation
import platform.Vision.VNClassifyImageRequest
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeAnimalsRequest
import platform.Vision.VNRecognizedObjectObservation
import platform.posix.memcpy
import kotlin.math.max

/**
 * [AnimalDetector] on iOS: Gemini via [CloudAnimalDetector], else Apple Vision on the phone. Vision finds
 * cats and dogs with boxes (VNRecognizeAnimalsRequest); anything else comes from its image classifier,
 * whose English labels go through the same [SpeciesCatalog] as ML Kit's on Android.
 */
class IosAnimalDetector(private val cloud: CloudAnimalDetector) : AnimalDetector {

    override suspend fun detect(photoPath: String): DetectionResult? {
        val image = UIImage.imageWithContentsOfFile(photoPath) ?: return null
        val jpeg = withContext(Dispatchers.Default) { uploadJpeg(image) }
        if (jpeg != null) cloud.detect(jpeg)?.let { return it.toDetectionResult() }
        return DetectionResult(withContext(Dispatchers.Default) { onDevice(image) }, DetectionSource.ON_DEVICE)
    }

    /** Photo downscaled to [CloudAnimalDetector.MAX_SIDE] as JPEG bytes for the online detector. */
    private fun uploadJpeg(image: UIImage): ByteArray? {
        val (width, height) = image.size.useContents { width * image.scale to height * image.scale }
        val factor = (CloudAnimalDetector.MAX_SIDE / max(width, height)).coerceAtMost(1.0)
        val w = width * factor
        val h = height * factor
        val format = UIGraphicsImageRendererFormat.defaultFormat().apply { scale = 1.0 }
        val scaled = UIGraphicsImageRenderer(size = CGSizeMake(w, h), format = format)
            .imageWithActions { image.drawInRect(CGRectMake(0.0, 0.0, w, h)) }
        return UIImageJPEGRepresentation(scaled, CloudAnimalDetector.JPEG_QUALITY / 100.0)?.toByteArray()
    }

    private fun onDevice(image: UIImage): List<Detection> {
        val cgImage = image.CGImage ?: return emptyList()
        val animals = VNRecognizeAnimalsRequest()
        val classify = VNClassifyImageRequest()
        // Photos are stored upright (see IosPhotoProcessor), so no orientation is passed.
        val handler = VNImageRequestHandler(cGImage = cgImage, options = emptyMap<Any?, Any>())
        runCatching { handler.performRequests(listOf(animals, classify), error = null) }

        val boxed = animals.results.orEmpty().filterIsInstance<VNRecognizedObjectObservation>().mapNotNull { obs ->
            val label = obs.labels.filterIsInstance<VNClassificationObservation>().maxByOrNull { it.confidence } ?: return@mapNotNull null
            val species = SpeciesCatalog.lookup(label.identifier) ?: return@mapNotNull null
            if (label.confidence < MIN_CONFIDENCE) return@mapNotNull null
            // Vision boxes are normalised with the origin at the bottom left.
            obs.boundingBox.useContents {
                Detection(
                    label = species.displayName,
                    category = species.category.name,
                    confidence = label.confidence,
                    left = origin.x.toFloat().coerceIn(0f, 1f),
                    top = (1 - origin.y - size.height).toFloat().coerceIn(0f, 1f),
                    right = (origin.x + size.width).toFloat().coerceIn(0f, 1f),
                    bottom = (1 - origin.y).toFloat().coerceIn(0f, 1f),
                    rawLabel = label.identifier,
                )
            }
        }
        if (boxed.isNotEmpty()) return boxed.sortedByDescending { it.confidence }

        // Fallback: classify the whole frame (no box), preferring specific labels like ML Kit's path does.
        val best = classify.results.orEmpty().filterIsInstance<VNClassificationObservation>()
            .mapNotNull { obs -> SpeciesCatalog.lookup(obs.identifier.replace('_', ' '))?.let { Triple(it, obs.confidence, obs.identifier) } }
            .filter { it.second >= MIN_CLASSIFY_CONFIDENCE }
            .maxByOrNull { (species, conf, _) -> conf + species.priority * 0.02f }
            ?: return emptyList()
        return listOf(
            Detection(
                label = best.first.displayName,
                category = best.first.category.name,
                confidence = best.second,
                left = 0f, top = 0f, right = 0f, bottom = 0f,
                rawLabel = best.third,
            ),
        )
    }

    private fun NSData.toByteArray(): ByteArray = ByteArray(length.toInt()).also { bytes ->
        if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    }

    private companion object {
        const val MIN_CONFIDENCE = 0.5f
        /** Vision's classifier spreads confidence over ~1300 labels, so a lower bar than ML Kit's. */
        const val MIN_CLASSIFY_CONFIDENCE = 0.3f
    }
}
