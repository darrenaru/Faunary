package com.faunary.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * Keeps photos in app-private storage (scoped storage, never shared).
 * Every photo is normalised to an upright JPEG so bounding boxes line up with what is drawn.
 */
class PhotoStorage(private val context: Context) : PhotoProcessor {

    private val dir: File get() = File(context.filesDir, "photos").apply { mkdirs() }

    fun newCaptureFile(): File = File(dir, "capture_${System.currentTimeMillis()}.jpg")

    override suspend fun normalize(sourcePath: String): StoredPhoto = normalize(File(sourcePath))

    /**
     * Rotates according to EXIF, downsizes to [maxSize] px and rewrites as a clean JPEG.
     * The source file is replaced by the normalised one.
     */
    suspend fun normalize(source: File, maxSize: Int = 2048): StoredPhoto = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSize) sample *= 2

        val decoded = BitmapFactory.decodeFile(
            source.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: error("Foto tidak valid")

        val rotation = runCatching {
            ExifInterface(source).rotationDegrees
        }.getOrDefault(0)
        val flipped = runCatching { ExifInterface(source).isFlipped }.getOrDefault(false)

        val longest = max(decoded.width, decoded.height)
        val scale = if (longest > maxSize) maxSize.toFloat() / longest else 1f
        val matrix = Matrix().apply {
            if (scale != 1f) postScale(scale, scale)
            if (flipped) postScale(-1f, 1f)
            if (rotation != 0) postRotate(rotation.toFloat())
        }
        val upright = if (matrix.isIdentity) decoded
        else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

        val target = File(dir, "sighting_${System.currentTimeMillis()}.jpg")
        target.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val result = StoredPhoto(target.absolutePath, upright.width, upright.height)
        if (upright !== decoded) decoded.recycle()
        upright.recycle()
        // The review screen was left meanwhile: its cleanup only knows the source, so drop the copy.
        if (!isActive) {
            target.delete()
            ensureActive()
        }
        if (source.absolutePath != target.absolutePath) source.delete()
        result
    }

    /** Loads a bitmap for ML inference, downscaled so detection stays under ~1s. */
    suspend fun loadForInference(path: String, maxSize: Int = 1280): Bitmap = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSize) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Foto tidak valid")
    }

    override fun delete(path: String) {
        runCatching { File(path).takeIf { it.parentFile == dir }?.delete() }
    }
}
