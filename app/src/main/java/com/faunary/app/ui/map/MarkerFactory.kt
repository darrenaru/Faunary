package com.faunary.app.ui.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.util.LruCache
import com.faunary.app.domain.AnimalCategory
import kotlin.math.min

/**
 * Draws the round photo markers: soft-cream disc, Canyon ring, animal photo inside.
 * Bitmaps are cached per (photo, selected) so re-syncing annotations stays cheap.
 */
class MarkerFactory(private val density: Float) {
    private val cache = LruCache<String, Bitmap>(80)

    fun marker(photoPath: String?, category: AnimalCategory, selected: Boolean, dark: Boolean): Bitmap {
        val key = "$photoPath|$category|$selected|$dark"
        cache.get(key)?.let { return it }

        val sizeDp = if (selected) 62f else 48f
        val pad = 6f * density // room for the shadow / halo
        val diameter = sizeDp * density
        val full = (diameter + pad * 2).toInt()
        val bmp = Bitmap.createBitmap(full, full, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val cx = full / 2f
        val cy = full / 2f
        val r = diameter / 2f

        val canyon = 0xFFDF6D41.toInt()
        val surface = if (dark) 0xFF342B25.toInt() else 0xFFFBF8F1.toInt()

        if (selected) {
            canvas.drawCircle(cx, cy, r + 4f * density, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99F7D89A.toInt() })
        }
        // Soft brown shadow + surface disc
        canvas.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = surface
            setShadowLayer(4f * density, 0f, 1.5f * density, 0x334A3023)
        })
        // Canyon ring
        val ring = (if (selected) 3.5f else 2.5f) * density
        canvas.drawCircle(cx, cy, r - ring / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = ring
            color = canyon
        })

        val inner = r - ring - 2f * density
        val photo = photoPath?.let { decodeSquare(it, (inner * 2).toInt()) }
        if (photo != null) {
            val shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(Matrix().apply {
                    val s = (inner * 2) / photo.width
                    postScale(s, s)
                    postTranslate(cx - inner, cy - inner)
                })
            }
            canvas.drawCircle(cx, cy, inner, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })
            photo.recycle()
        } else {
            canvas.drawCircle(cx, cy, inner, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF7D89A.toInt() })
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = inner; textAlign = Paint.Align.CENTER }
            canvas.drawText(category.emoji, cx, cy - (text.descent() + text.ascent()) / 2, text)
        }
        cache.put(key, bmp)
        return bmp
    }

    /** Decodes a centre-cropped square roughly [size] px wide. */
    private fun decodeSquare(path: String, size: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= size) sample *= 2
        val src = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val side = min(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        if (square !== src) src.recycle()
        square
    }.getOrNull()
}
