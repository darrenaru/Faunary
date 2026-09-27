package com.faunary.app.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.util.LruCache
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Place
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.faunary.app.ui.components.icon
import kotlin.math.max
import kotlin.math.min

/** Searched-place marker: the classic map red, apart from every shared-marker colour. */
private val PlaceMarkerColor = 0xFFD93A34.toInt()

/**
 * Draws the map markers:
 * - OWN: photo pin with a Canyon outline and a category-icon badge
 * - COMMUNITY: slightly smaller photo pin with a blue outline (other people's finds)
 * - LIVE: initial letter in an olive ring with the user's name underneath (pulsed by FaunaMap)
 * - PIN: teardrop in the marker's icon colour with its glyph, for shared markers (route destinations)
 * - PLACE: larger red teardrop for the place picked in search
 * Bitmaps are cached, so re-syncing annotations stays cheap.
 */
class MarkerFactory(private val context: Context, private val density: Float) {
    private val cache = LruCache<String, Bitmap>(160)

    /** Inter (variable font, shipped by the shared module) at a bold weight; falls back to the system bold if it can't load. */
    private val interBold: Typeface by lazy {
        val base = runCatching { Typeface.createFromAsset(context.assets, SHARED_INTER_ASSET) }.getOrNull() ?: return@lazy Typeface.DEFAULT_BOLD
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(base, 700, false) else Typeface.create(base, Typeface.BOLD)
    }

    /** [count] > 1 draws a stack: cards fanned behind the top photo and the count in the badge. */
    suspend fun marker(marker: MapMarker, selected: Boolean, dark: Boolean, count: Int = 1): Bitmap {
        val cacheKey = "${marker.kind}|${marker.photo}|${marker.category}|${marker.label}|${marker.pinIcon}|$selected|$dark|$count"
        cache.get(cacheKey)?.let { return it }
        val bmp = when (marker.kind) {
            MarkerKind.LIVE -> liveMarker(marker.label ?: "?", selected, dark)
            MarkerKind.PIN -> pinMarker(marker.pinIcon ?: PinIcon.FLAG, selected, dark)
            MarkerKind.PLACE -> pinMarker(PlaceMarkerColor, { Icons.Rounded.Place }, selected = true, dark, scale = 1.15f)
            else -> photoMarker(marker, selected, dark, count)
        }
        cache.put(cacheKey, bmp)
        return bmp
    }

    /**
     * Photo pin: rounded-square photo in a cream frame with a pointed tail whose tip is the
     * bitmap's bottom-centre (the annotation is anchored there, so the pin stands on its spot).
     * An accent outline (Canyon = own, blue = community) and a category badge top-right tell the
     * finds apart; the selected pin is larger with a soft glow.
     */
    private suspend fun photoMarker(marker: MapMarker, selected: Boolean, dark: Boolean, count: Int = 1): Bitmap {
        val d = density
        val body = (if (selected) 60f else if (marker.kind == MarkerKind.COMMUNITY) 42f else 46f) * d
        val frame = (if (selected) 3.5f else 3f) * d
        val tailH = (if (selected) 9f else 7f) * d
        val tailW = (if (selected) 16f else 13f) * d
        val badge = (if (selected) 22f else 18f) * d
        val side = (if (count > 1) 14f else 8f) * d // room for shadow, glow, badge overhang and stacked cards
        val width = (body + side * 2).toInt()
        val height = (side + body + tailH).toInt()
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val accent = if (marker.kind == MarkerKind.COMMUNITY) 0xFF7395BF.toInt() else 0xFFDF6D41.toInt()
        val surface = if (dark) 0xFF342B25.toInt() else 0xFFFBF8F1.toInt()
        val cx = width / 2f
        val rect = RectF(cx - body / 2, side, cx + body / 2, side + body)
        val corner = body * 0.3f

        // Frame + tail as one shape, so outline and shadow run around both.
        val pin = Path().apply {
            addRoundRect(rect, corner, corner, Path.Direction.CW)
            op(Path().apply {
                moveTo(cx - tailW / 2, rect.bottom - corner / 2)
                lineTo(cx + tailW / 2, rect.bottom - corner / 2)
                lineTo(cx, rect.bottom + tailH)
                close()
            }, Path.Op.UNION)
        }
        // Stack: one or two cards peeking out behind the top photo, slightly rotated.
        if (count > 1) {
            val tilts = if (count == 2) listOf(8f) else listOf(10f, -7f)
            for (deg in tilts) {
                canvas.save()
                canvas.rotate(deg, rect.centerX(), rect.bottom)
                canvas.drawRoundRect(rect, corner, corner, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = surface
                    setShadowLayer(3f * d, 0f, 1f * d, 0x334A3023)
                })
                canvas.drawRoundRect(rect, corner, corner, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 1.5f * d
                    color = (accent and 0x00FFFFFF) or 0x99000000.toInt()
                })
                canvas.restore()
            }
        }
        if (selected) {
            canvas.drawPath(pin, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 7f * d
                strokeJoin = Paint.Join.ROUND
                color = (accent and 0x00FFFFFF) or 0x40000000
            })
        }
        canvas.drawPath(pin, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = surface
            setShadowLayer(5f * d, 0f, 2f * d, 0x404A3023)
        })

        // Photo (or the category icon when there is none), clipped to the inner rounded square.
        val inner = RectF(rect.left + frame, rect.top + frame, rect.right - frame, rect.bottom - frame)
        val innerCorner = corner - frame
        val photo = marker.photo?.let { loadSquare(it, inner.width().toInt()) }
        if (photo != null) {
            val shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(Matrix().apply {
                    val scale = inner.width() / photo.width
                    postScale(scale, scale)
                    postTranslate(inner.left, inner.top)
                })
            }
            canvas.drawRoundRect(inner, innerCorner, innerCorner, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })
        } else {
            canvas.drawRoundRect(inner, innerCorner, innerCorner, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF7D89A.toInt() })
            drawIcon(canvas, marker.category.icon, inner.centerX(), inner.centerY(), inner.width() * 0.55f, 0xFF7A4E28.toInt())
        }

        canvas.drawPath(pin, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (if (selected) 2.5f else 1.5f) * d
            strokeJoin = Paint.Join.ROUND
            color = accent
        })

        // Stack: the number of photos in a badge on the top-right corner.
        if (count > 1) {
            val text = if (count > 99) "99+" else count.toString()
            val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = badge * 0.58f
                typeface = interBold
                color = 0xFFFFFFFF.toInt()
                textAlign = Paint.Align.CENTER
            }
            val w = max(badge, label.measureText(text) + 10f * d)
            val bx = rect.right - 3f * d
            val by = rect.top + 3f * d
            val pill = RectF(bx - w / 2, by - badge / 2, bx + w / 2, by + badge / 2)
            canvas.drawRoundRect(pill, badge / 2, badge / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = surface
                setShadowLayer(2f * d, 0f, 1f * d, 0x334A3023)
            })
            val inset = 1.5f * d
            val inner2 = RectF(pill.left + inset, pill.top + inset, pill.right - inset, pill.bottom - inset)
            canvas.drawRoundRect(inner2, inner2.height() / 2, inner2.height() / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
            canvas.drawText(text, bx, by - (label.descent() + label.ascent()) / 2, label)
        } else if (photo != null) {
            // Category badge on the top-right corner.
            val bx = rect.right - 3f * d
            val by = rect.top + 3f * d
            canvas.drawCircle(bx, by, badge / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = surface
                setShadowLayer(2f * d, 0f, 1f * d, 0x334A3023)
            })
            canvas.drawCircle(bx, by, badge / 2 - 1.5f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
            drawIcon(canvas, marker.category.icon, bx, by, badge * 0.58f, 0xFFFFFFFF.toInt())
        }
        return bmp
    }

    /** Draws a Compose [ImageVector] (the category icons) onto a plain Android canvas, centred and tinted. */
    private fun drawIcon(canvas: Canvas, vector: ImageVector, cx: Float, cy: Float, size: Float, color: Int) {
        val scale = size / vector.viewportWidth
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(cx - size / 2, cy - vector.viewportHeight * scale / 2)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        fun draw(group: VectorGroup) {
            for (node in group) when (node) {
                is VectorPath -> {
                    val path = node.pathData.toPath().asAndroidPath()
                    path.fillType = if (node.pathFillType == PathFillType.EvenOdd) android.graphics.Path.FillType.EVEN_ODD
                    else android.graphics.Path.FillType.WINDING
                    path.transform(matrix)
                    canvas.drawPath(path, paint)
                }
                is VectorGroup -> draw(node)
            }
        }
        draw(vector.root)
    }

    /**
     * Classic map-pin teardrop in [icon]'s colour with its glyph, tip at the bitmap's bottom centre
     * (the annotation is anchored there). The shape keeps it apart from the square photo pins.
     */
    private fun pinMarker(icon: PinIcon, selected: Boolean, dark: Boolean): Bitmap = pinMarker(icon.argb, icon.glyph, selected, dark)

    private fun pinMarker(fill: Int, glyph: () -> ImageVector, selected: Boolean, dark: Boolean, scale: Float = 1f): Bitmap {
        val d = density * scale
        val r = (if (selected) 17f else 13f) * d // head radius
        val tail = r * 1.25f // head centre to tip
        val pad = 4f * d // shadow room
        val width = (r * 2 + pad * 2).toInt()
        val height = (pad + r + tail + pad / 2).toInt()
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val cx = width / 2f
        val cy = pad + r
        val tipY = cy + tail

        // Teardrop: a circle whose two tangents meet at the tip.
        val body = Path().apply {
            val angle = Math.toDegrees(kotlin.math.acos((r / tail).toDouble())).toFloat()
            moveTo(cx, tipY)
            arcTo(RectF(cx - r, cy - r, cx + r, cy + r), 90f + angle, 360f - 2 * angle)
            close()
        }
        canvas.drawPath(body, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fill
            setShadowLayer(3f * d, 0f, 1.5f * d, 0x554A3023)
        })
        canvas.drawPath(body, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (if (selected) 2.5f else 2f) * d
            color = if (dark) 0xFF29231F.toInt() else 0xFFFBF8F1.toInt()
        })

        drawIcon(canvas, glyph(), cx, cy, r * 1.15f, 0xFFFBF8F1.toInt())
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
            typeface = interBold
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
            typeface = interBold
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

/** Where compose resources package the shared module's Inter font inside the APK. */
private const val SHARED_INTER_ASSET = "composeResources/com.faunary.shared.resources/font/inter.ttf"
