package com.faunary.app.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.LruCache
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlin.math.max
import kotlin.math.min

/**
 * Draws the round map markers:
 * - OWN: photo inside a Canyon ring
 * - COMMUNITY: photo inside a blue ring (other people's finds)
 * - LIVE: initial letter in an olive ring with the user's name underneath (pulsed by FaunaMap)
 * Bitmaps are cached, so re-syncing annotations stays cheap.
 */
class MarkerFactory(private val context: Context, private val density: Float) {
    private val cache = LruCache<String, Bitmap>(160)

    suspend fun marker(marker: MapMarker, selected: Boolean, dark: Boolean): Bitmap {
        val cacheKey = "${marker.kind}|${marker.photo}|${marker.category}|${marker.label}|$selected|$dark"
        cache.get(cacheKey)?.let { return it }
        val bmp = when (marker.kind) {
            MarkerKind.LIVE -> liveMarker(marker.label ?: "?", selected, dark)
            else -> photoMarker(marker, selected, dark)
        }
        cache.put(cacheKey, bmp)
        return bmp
    }

    private suspend fun photoMarker(marker: MapMarker, selected: Boolean, dark: Boolean): Bitmap {
        val sizeDp = if (selected) 62f else if (marker.kind == MarkerKind.COMMUNITY) 42f else 48f
        val pad = 6f * density // room for the shadow / halo
        val diameter = sizeDp * density
        val full = (diameter + pad * 2).toInt()
        val bmp = Bitmap.createBitmap(full, full, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val cx = full / 2f
        val cy = full / 2f
        val r = diameter / 2f

        val ringColor = if (marker.kind == MarkerKind.COMMUNITY) 0xFF7395BF.toInt() else 0xFFDF6D41.toInt()
        val surface = if (dark) 0xFF342B25.toInt() else 0xFFFBF8F1.toInt()

        if (selected) {
            canvas.drawCircle(cx, cy, r + 4f * density, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99F7D89A.toInt() })
        }
        canvas.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = surface
            setShadowLayer(4f * density, 0f, 1.5f * density, 0x334A3023)
        })
        val ring = (if (selected) 3.5f else 2.5f) * density
        canvas.drawCircle(cx, cy, r - ring / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = ring
            color = ringColor
        })

        val inner = r - ring - 2f * density
        val photo = marker.photo?.let { loadSquare(it, (inner * 2).toInt()) }
        if (photo != null) {
            val shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(Matrix().apply {
                    val s = (inner * 2) / photo.width
                    postScale(s, s)
                    postTranslate(cx - inner, cy - inner)
                })
            }
            canvas.drawCircle(cx, cy, inner, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })
        } else {
            canvas.drawCircle(cx, cy, inner, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF7D89A.toInt() })
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = inner; textAlign = Paint.Align.CENTER }
            canvas.drawText(marker.category.emoji, cx, cy - (text.descent() + text.ascent()) / 2, text)
        }
        return bmp
    }

    /**
     * Avatar with the initial and a name pill underneath. The avatar is centred on the bitmap so
     * it sits exactly on the coordinate, where the heartbeat ring (drawn by the map) pulses out.
     */
    private fun liveMarker(name: String, selected: Boolean, dark: Boolean): Bitmap {
        val avatar = (if (selected) 44f else 36f) * density
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f * density
            typeface = Typeface.DEFAULT_BOLD
            color = if (dark) 0xFFF5EDE0.toInt() else 0xFF4A3023.toInt()
        }
        val shortName = if (name.length > 14) name.take(13) + "…" else name
        val labelW = labelPaint.measureText(shortName) + 14f * density
        val labelH = 18f * density
        val gap = 2f * density
        val shadow = 4f * density
        val width = (max(avatar + shadow * 2, labelW) + 4).toInt()
        // Mirror the label's space above the avatar so the avatar centre is the bitmap centre.
        val height = (avatar + (gap + labelH + shadow) * 2).toInt()
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val cx = width / 2f
        val cy = height / 2f

        canvas.drawCircle(cx, cy, avatar / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFAAA648.toInt()
            setShadowLayer(3f * density, 0f, 1f * density, 0x334A3023)
        })
        canvas.drawCircle(cx, cy, avatar / 2 - 3f * density, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (dark) 0xFF342B25.toInt() else 0xFFFBF8F1.toInt()
        })
        val initial = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = avatar * 0.45f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            color = if (dark) 0xFFF5EDE0.toInt() else 0xFF7A4E28.toInt()
        }
        canvas.drawText(name.take(1).uppercase(), cx, cy - (initial.descent() + initial.ascent()) / 2, initial)

        val top = cy + avatar / 2 + gap
        val rect = RectF(cx - labelW / 2, top, cx + labelW / 2, top + labelH)
        canvas.drawRoundRect(rect, labelH / 2, labelH / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (dark) 0xE6342B25.toInt() else 0xE6FBF8F1.toInt()
        })
        canvas.drawText(shortName, cx - labelPaint.measureText(shortName) / 2, rect.centerY() - (labelPaint.descent() + labelPaint.ascent()) / 2, labelPaint)
        return bmp
    }

    /** Loads a centre-cropped square roughly [size] px wide from a file path or URL. */
    private suspend fun loadSquare(source: String, size: Int): Bitmap? {
        val src = if (source.startsWith("http")) loadRemote(source, size) else decodeFile(source, size)
        src ?: return null
        val side = min(src.width, src.height)
        return Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
    }

    private suspend fun loadRemote(url: String, size: Int): Bitmap? = runCatching {
        val request = ImageRequest.Builder(context).data(url).size(size * 2).allowHardware(false).build()
        (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
    }.getOrNull()

    private fun decodeFile(path: String, size: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= size) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}
