package com.faunary.app.data

import com.faunary.app.util.currentTimeMillis
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.writeToFile
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import kotlin.math.max
import kotlin.math.roundToInt

/** Photos live in the app's Documents/photos folder ([dir]); the same rules as Android's PhotoStorage. */
class IosPhotoProcessor(private val dir: String) : PhotoProcessor {

    /** Where the camera writes a fresh shot before review. */
    fun newCapturePath(): String {
        SystemFileSystem.createDirectories(Path(dir))
        return "$dir/capture_${currentTimeMillis()}.jpg"
    }

    override suspend fun normalize(sourcePath: String): StoredPhoto = withContext(Dispatchers.Default) {
        val image = UIImage.imageWithContentsOfFile(sourcePath) ?: error("Foto tidak valid")
        // UIImage sizes are in points; the pixel size is points × scale. Drawing applies the EXIF orientation.
        val (width, height) = image.size.useContents { width * image.scale to height * image.scale }
        val longest = max(width, height)
        val factor = if (longest > MAX_SIZE) MAX_SIZE / longest else 1.0
        val w = (width * factor).roundToInt().coerceAtLeast(1)
        val h = (height * factor).roundToInt().coerceAtLeast(1)

        val format = UIGraphicsImageRendererFormat.defaultFormat().apply {
            scale = 1.0
            opaque = true
        }
        val upright = UIGraphicsImageRenderer(size = CGSizeMake(w.toDouble(), h.toDouble()), format = format)
            .imageWithActions { image.drawInRect(CGRectMake(0.0, 0.0, w.toDouble(), h.toDouble())) }
        val jpeg = UIImageJPEGRepresentation(upright, JPEG_QUALITY) ?: error("Foto tidak bisa disimpan")

        SystemFileSystem.createDirectories(Path(dir))
        val target = "$dir/sighting_${currentTimeMillis()}.jpg"
        if (!jpeg.writeToFile(target, atomically = true)) error("Foto tidak bisa disimpan")
        // The review screen was left meanwhile: its cleanup only knows the source, so drop the copy.
        if (!isActive) {
            delete(target)
            ensureActive()
        }
        if (sourcePath != target) SystemFileSystem.delete(Path(sourcePath), mustExist = false)
        StoredPhoto(target, w, h)
    }

    override fun delete(path: String) {
        val current = localPhotoPath(path)
        if (Path(current).parent?.toString() != Path(dir).toString()) return
        runCatching { SystemFileSystem.delete(Path(current), mustExist = false) }
    }

    private companion object {
        const val MAX_SIZE = 2048.0
        const val JPEG_QUALITY = 0.9
    }
}
