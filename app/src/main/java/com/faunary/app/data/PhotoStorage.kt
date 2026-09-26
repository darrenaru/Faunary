package com.faunary.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

data class StoredPhoto(val path: String, val width: Int, val height: Int)

data class ExifLocation(val latitude: Double, val longitude: Double)

/**
 * Keeps photos in app-private storage (scoped storage, never shared).
 * Every photo is normalised to an upright JPEG so bounding boxes line up with what is drawn.
 */
@Singleton
class PhotoStorage @Inject constructor(@ApplicationContext private val context: Context) {

    private val dir: File get() = File(context.filesDir, "photos").apply { mkdirs() }

    fun newCaptureFile(): File = File(dir, "capture_${System.currentTimeMillis()}.jpg")

    /** Copies a picked image into private storage. Returns the file plus GPS EXIF when present. */
    suspend fun importFromUri(uri: Uri): Pair<File, ExifLocation?> = withContext(Dispatchers.IO) {
        val target = File(dir, "import_${System.currentTimeMillis()}.jpg")
        val source = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { MediaStore.setRequireOriginal(uri) }.getOrDefault(uri)
        } else uri
        val input = runCatching { context.contentResolver.openInputStream(source) }.getOrNull()
            ?: context.contentResolver.openInputStream(uri)
            ?: error("Tidak bisa membuka foto")
        input.use { i -> target.outputStream().use { o -> i.copyTo(o) } }
        target to readExifLocation(target)
    }

    private fun readExifLocation(file: File): ExifLocation? = runCatching {
        ExifInterface(file).latLong?.let { (lat, lng) ->
            if (lat == 0.0 && lng == 0.0) null else ExifLocation(lat, lng)
        }
    }.getOrNull()

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

    fun delete(path: String) {
        runCatching { File(path).takeIf { it.parentFile == dir }?.delete() }
    }
}
