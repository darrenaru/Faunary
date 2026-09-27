package com.faunary.app.ui.map

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Place
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asSkiaPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.faunary.app.ui.components.icon
import com.faunary.app.ui.components.photoModel
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeJoin
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.TextLine
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.UIKit.UIImage
import platform.UIKit.UIScreen
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.graphics.Path as ComposePath

/** A drawn marker plus a stable id for Mapbox's image registry. */
class MarkerImage(val image: UIImage, val id: String)

/**
 * Draws the map markers for iOS with Skia, matching Android's MarkerFactory: photo pins (with the
 * category badge, or the count for a stack), live explorers, shared markers and the searched place.
 * Images are cached by what they show.
 */
class IosMarkerArt {
    private val scale = UIScreen.mainScreen.scale.toFloat()
    private val cache = LinkedHashMap<String, MarkerImage>()
    private val bold by lazy { FontMgr.default.matchFamilyStyle(null, FontStyle.BOLD) }

    suspend fun marker(marker: MapMarker, selected: Boolean, dark: Boolean, count: Int = 1): MarkerImage {
        val key = "${marker.kind}|${marker.photo}|${marker.category}|${marker.label}|${marker.pinIcon}|$selected|$dark|$count"
        cache[key]?.let { return it }
        val png = when (marker.kind) {
            MarkerKind.LIVE -> liveMarker(marker.label ?: "?", selected, dark)
            MarkerKind.PIN -> pinMarker((marker.pinIcon ?: PinIcon.FLAG).argb, (marker.pinIcon ?: PinIcon.FLAG).glyph(), selected, dark)
            MarkerKind.PLACE -> pinMarker(PLACE_COLOR, Icons.Rounded.Place, selected = true, dark, sizeScale = 1.15f)
            else -> photoMarker(marker, selected, dark, count)
        }
        val image = MarkerImage(png.toUIImage(scale), key.hashCode().toString())
        if (cache.size >= CACHE_SIZE) cache.remove(cache.keys.first())
        cache[key] = image
        return image
    }

    private suspend fun photoMarker(marker: MapMarker, selected: Boolean, dark: Boolean, count: Int): ByteArray {
        val d = scale
        val body = (if (selected) 60f else if (marker.kind == MarkerKind.COMMUNITY) 42f else 46f) * d
        val frame = (if (selected) 3.5f else 3f) * d
        val tailH = (if (selected) 9f else 7f) * d
        val tailW = (if (selected) 16f else 13f) * d
        val badge = (if (selected) 22f else 18f) * d
        val side = (if (count > 1) 14f else 8f) * d
        val width = (body + side * 2).roundToInt()
        val height = (side + body + tailH).roundToInt()
        val photo = marker.photo?.let { loadSquare(it, (body - frame * 2).roundToInt()) }

        return draw(width, height) { canvas ->
            val accent = if (marker.kind == MarkerKind.COMMUNITY) 0xFF7395BF.toInt() else 0xFFDF6D41.toInt()
            val surface = if (dark) 0xFF342B25.toInt() else 0xFFFBF8F1.toInt()
            val cx = width / 2f
            val rect = Rect.makeLTRB(cx - body / 2, side, cx + body / 2, side + body)
            val corner = body * 0.3f
            // Frame + tail as one shape, so outline and shadow run around both.
            val pin = ComposePath().apply {
                addRoundRect(RoundRect(rect.left, rect.top, rect.right, rect.bottom, CornerRadius(corner)))
                op(this, ComposePath().apply {
                    moveTo(cx - tailW / 2, rect.bottom - corner / 2)
                    lineTo(cx + tailW / 2, rect.bottom - corner / 2)
                    lineTo(cx, rect.bottom + tailH)
                    close()
                }, PathOperation.Union)
            }.asSkiaPath()

            // Stack: one or two cards peeking out behind the top photo, slightly rotated.
            if (count > 1) {
                for (deg in if (count == 2) listOf(8f) else listOf(10f, -7f)) {
                    canvas.save()
                    canvas.rotate(deg, (rect.left + rect.right) / 2, rect.bottom)
                    val card = RRect.makeLTRB(rect.left, rect.top, rect.right, rect.bottom, corner)
                    canvas.drawRRect(card, fill(surface, shadow = 3f * d))
                    canvas.drawRRect(card, stroke((accent and 0x00FFFFFF) or 0x99000000.toInt(), 1.5f * d))
                    canvas.restore()
                }
            }
            if (selected) canvas.drawPath(pin, stroke((accent and 0x00FFFFFF) or 0x40000000, 7f * d))
            canvas.drawPath(pin, fill(surface, shadow = 5f * d))

            // Photo (or the category icon when there is none), clipped to the inner rounded square.
            val inner = RRect.makeLTRB(rect.left + frame, rect.top + frame, rect.right - frame, rect.bottom - frame, corner - frame)
            if (photo != null) {
                canvas.save()
                canvas.clipRRect(inner, true)
                val s = min(photo.width, photo.height).toFloat()
                val src = Rect.makeXYWH((photo.width - s) / 2, (photo.height - s) / 2, s, s)
                canvas.drawImageRect(photo, src, Rect.makeLTRB(inner.left, inner.top, inner.right, inner.bottom))
                canvas.restore()
            } else {
                canvas.drawRRect(inner, fill(0xFFF7D89A.toInt()))
                drawIcon(canvas, marker.category.icon, (inner.left + inner.right) / 2, (inner.top + inner.bottom) / 2, (inner.right - inner.left) * 0.55f, 0xFF7A4E28.toInt())
            }
            canvas.drawPath(pin, stroke(accent, (if (selected) 2.5f else 1.5f) * d))

            val bx = rect.right - 3f * d
            val by = rect.top + 3f * d
            if (count > 1) {
                // Stack: the number of photos in a badge on the top-right corner.
                val text = if (count > 99) "99+" else count.toString()
                val font = Font(bold, badge * 0.58f)
                val line = TextLine.make(text, font)
                val w = max(badge, line.width + 10f * d)
                canvas.drawRRect(RRect.makeLTRB(bx - w / 2, by - badge / 2, bx + w / 2, by + badge / 2, badge / 2), fill(surface, shadow = 2f * d))
                val inset = 1.5f * d
                canvas.drawRRect(RRect.makeLTRB(bx - w / 2 + inset, by - badge / 2 + inset, bx + w / 2 - inset, by + badge / 2 - inset, badge / 2 - inset), fill(accent))
                drawCentered(canvas, line, font, bx, by, 0xFFFFFFFF.toInt())
            } else if (photo != null) {
                // Category badge on the top-right corner.
                canvas.drawCircle(bx, by, badge / 2, fill(surface, shadow = 2f * d))
                canvas.drawCircle(bx, by, badge / 2 - 1.5f * d, fill(accent))
                drawIcon(canvas, marker.category.icon, bx, by, badge * 0.58f, 0xFFFFFFFF.toInt())
            }
        }
    }

    /** Teardrop in [fillColor] with [glyph]; its tip is the bottom centre (the anchor). */
    private fun pinMarker(fillColor: Int, glyph: ImageVector, selected: Boolean, dark: Boolean, sizeScale: Float = 1f): ByteArray {
        val d = scale * sizeScale
        val r = (if (selected) 17f else 13f) * d
        val tail = r * 1.25f
        val pad = 4f * d
        val width = (r * 2 + pad * 2).roundToInt()
        val height = (pad + r + tail + pad / 2).roundToInt()
        return draw(width, height) { canvas ->
            val cx = width / 2f
            val cy = pad + r
            val angle = (acos((r / tail).toDouble()) * 180 / kotlin.math.PI).toFloat()
            // Teardrop: a circle whose two tangents meet at the tip.
            val body = ComposePath().apply {
                moveTo(cx, cy + tail)
                arcTo(ComposeRect(cx - r, cy - r, cx + r, cy + r), 90f + angle, 360f - 2 * angle, false)
                close()
            }.asSkiaPath()
            canvas.drawPath(body, fill(fillColor, shadow = 3f * d))
            canvas.drawPath(body, stroke(if (dark) 0xFF29231F.toInt() else 0xFFFBF8F1.toInt(), (if (selected) 2.5f else 2f) * d))
            drawIcon(canvas, glyph, cx, cy, r * 1.15f, 0xFFFBF8F1.toInt())
        }
    }

    /** Avatar with the initial and a name pill underneath, centred on the spot. */
    private fun liveMarker(name: String, selected: Boolean, dark: Boolean): ByteArray {
        val d = scale
        val avatar = (if (selected) 44f else 36f) * d
        val labelFont = Font(bold, 11f * d)
        val shortName = if (name.length > 14) name.take(13) + "…" else name
        val label = TextLine.make(shortName, labelFont)
        val labelW = label.width + 14f * d
        val labelH = 18f * d
        val gap = 2f * d
        val shadow = 4f * d
        val width = (max(avatar + shadow * 2, labelW) + 4).roundToInt()
        // Mirror the label's space above the avatar so the avatar centre is the image centre.
        val height = (avatar + (gap + labelH + shadow) * 2).roundToInt()
        return draw(width, height) { canvas ->
            val cx = width / 2f
            val cy = height / 2f
            canvas.drawCircle(cx, cy, avatar / 2, fill(0xFFAAA648.toInt(), shadow = 3f * d))
            canvas.drawCircle(cx, cy, avatar / 2 - 3f * d, fill(if (dark) 0xFF342B25.toInt() else 0xFFFBF8F1.toInt()))
            val initialFont = Font(bold, avatar * 0.45f)
            drawCentered(canvas, TextLine.make(name.take(1).uppercase(), initialFont), initialFont, cx, cy, if (dark) 0xFFF5EDE0.toInt() else 0xFF7A4E28.toInt())
            val top = cy + avatar / 2 + gap
            canvas.drawRRect(RRect.makeLTRB(cx - labelW / 2, top, cx + labelW / 2, top + labelH, labelH / 2), fill(if (dark) 0xE6342B25.toInt() else 0xE6FBF8F1.toInt()))
            drawCentered(canvas, label, labelFont, cx, top + labelH / 2, if (dark) 0xFFF5EDE0.toInt() else 0xFF4A3023.toInt())
        }
    }

    private fun draw(width: Int, height: Int, block: (Canvas) -> Unit): ByteArray {
        val surface = Surface.makeRasterN32Premul(width, height)
        block(surface.canvas)
        return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)?.bytes ?: ByteArray(0)
    }

    private fun fill(color: Int, shadow: Float = 0f) = Paint().apply {
        this.color = color
        isAntiAlias = true
        if (shadow > 0f) imageFilter = ImageFilter.makeDropShadow(0f, shadow / 2.5f, shadow / 2, shadow / 2, 0x404A3023)
    }

    private fun stroke(color: Int, width: Float) = Paint().apply {
        this.color = color
        isAntiAlias = true
        mode = PaintMode.STROKE
        strokeWidth = width
        strokeJoin = PaintStrokeJoin.ROUND
    }

    private fun drawCentered(canvas: Canvas, line: TextLine, font: Font, cx: Float, cy: Float, color: Int) {
        val metrics = font.metrics
        canvas.drawTextLine(line, cx - line.width / 2, cy - (metrics.ascent + metrics.descent) / 2, fill(color))
    }

    /** Draws a Compose [ImageVector] (category and marker icons), centred and tinted. */
    private fun drawIcon(canvas: Canvas, vector: ImageVector, cx: Float, cy: Float, size: Float, color: Int) {
        val s = size / vector.viewportWidth
        val matrix = Matrix().apply {
            translate(cx - size / 2, cy - vector.viewportHeight * s / 2)
            scale(s, s)
        }
        val paint = fill(color)
        fun drawGroup(group: VectorGroup) {
            for (node in group) when (node) {
                is VectorPath -> {
                    val path = node.pathData.toPath().apply {
                        fillType = node.pathFillType
                        transform(matrix)
                    }
                    canvas.drawPath(path.asSkiaPath(), paint)
                }
                is VectorGroup -> drawGroup(node)
            }
        }
        drawGroup(vector.root)
    }

    /** The photo as a Skia image about [size] px across, from a local path or a URL. */
    private suspend fun loadSquare(source: String, size: Int): Image? = runCatching {
        val context = PlatformContext.INSTANCE
        val request = ImageRequest.Builder(context).data(photoModel(source)).size(size).build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult
        result?.image?.toBitmap()?.let { Image.makeFromBitmap(it) }
    }.getOrNull()

    private companion object {
        const val CACHE_SIZE = 160
        /** Searched place: the classic map red, apart from every shared-marker colour. */
        val PLACE_COLOR = 0xFFD93A34.toInt()
    }
}

private fun ByteArray.toUIImage(scale: Float): UIImage {
    val data = usePinned { NSData.dataWithBytes(it.addressOf(0), size.toULong()) }
    return UIImage.imageWithData(data, scale.toDouble()) ?: UIImage()
}
