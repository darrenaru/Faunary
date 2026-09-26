package com.faunary.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.EmojiNature
import androidx.compose.material.icons.rounded.FlutterDash
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.faunary.app.data.Detection
import com.faunary.app.domain.AnimalCategory
import com.faunary.app.ui.theme.FaunaryTheme
import com.faunary.app.ui.theme.Radius
import com.faunary.app.util.Format
import java.io.File
import kotlin.math.roundToInt

/** Accepts either a local file path or a remote (https) URL. */
fun photoModel(pathOrUrl: String): Any = if (pathOrUrl.startsWith("http")) pathOrUrl else File(pathOrUrl)

val AnimalCategory.icon: ImageVector
    get() = when (this) {
        AnimalCategory.BIRD -> Icons.Rounded.FlutterDash
        AnimalCategory.WILD -> Icons.Rounded.EmojiNature
        else -> Icons.Rounded.Pets
    }

/**
 * Photo shown at its native aspect ratio with soft, rounded detection boxes on top
 * (olive stroke 2px, radius 8px, buttercream label pill).
 */
@Composable
fun DetectionPhoto(
    photoPath: String,
    aspectRatio: Float,
    detections: List<Detection>,
    modifier: Modifier = Modifier,
    showBoxes: Boolean = true,
    selectedIndex: Int = 0,
    onSelect: ((Int) -> Unit)? = null,
    shape: RoundedCornerShape = RoundedCornerShape(Radius.lg),
) {
    val c = FaunaryTheme.colors
    val density = LocalDensity.current
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio.coerceIn(0.5f, 2f))
            .clip(shape)
            .background(c.surfaceMuted),
    ) {
        AsyncImage(
            model = photoModel(photoPath),
            contentDescription = detections.firstOrNull()?.label ?: "Foto satwa",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        AnimatedVisibility(showBoxes, enter = fadeIn(tween(250)), exit = fadeOut(tween(200))) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wPx = constraints.maxWidth.toFloat()
                val hPx = constraints.maxHeight.toFloat()
                val boxed = detections.withIndex().filter { it.value.hasBox }
                boxed.forEach { (index, d) ->
                    val selected = index == selectedIndex
                    val alpha by animateFloatAsState(if (selected || boxed.size == 1) 1f else 0.55f, tween(200), label = "boxAlpha")
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = with(density) { (if (selected) 2.5.dp else 2.dp).toPx() }
                        val radius = with(density) { 8.dp.toPx() }
                        drawRoundRect(
                            color = c.detectionBox.copy(alpha = alpha),
                            topLeft = Offset(d.left * wPx, d.top * hPx),
                            size = Size((d.right - d.left) * wPx, (d.bottom - d.top) * hPx),
                            cornerRadius = CornerRadius(radius),
                            style = Stroke(stroke),
                        )
                    }
                    val labelY = (d.top * hPx - with(density) { 30.dp.toPx() }).coerceAtLeast(with(density) { 6.dp.toPx() })
                    Box(
                        Modifier
                            .offset { IntOffset((d.left * wPx).roundToInt() + 4, labelY.roundToInt()) }
                            .then(if (onSelect != null) Modifier.clickable { onSelect(index) } else Modifier),
                    ) {
                        Pill(
                            text = "${d.label} · ${Format.percent(d.confidence)}",
                            icon = Icons.Rounded.AutoAwesome,
                            color = if (selected) c.highlight else c.highlight.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PhotoThumb(
    photoPath: String,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(Radius.md),
    contentDescription: String? = null,
) {
    AsyncImage(
        model = photoModel(photoPath),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(shape).background(FaunaryTheme.colors.surfaceMuted),
    )
}

@Composable
fun CategoryAvatar(category: AnimalCategory, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 40.dp) {
    val c = FaunaryTheme.colors
    val bg: Color = when (category) {
        AnimalCategory.CAT -> c.highlight
        AnimalCategory.DOG -> c.badgeSoft
        AnimalCategory.BIRD -> c.badgeInfo
        AnimalCategory.WILD -> c.badgeNature
        AnimalCategory.OTHER -> c.surfaceMuted
    }
    Box(modifier.size(size).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) {
        androidx.compose.material3.Icon(category.icon, null, Modifier.size(size * 0.5f), tint = if (bg == c.highlight) c.onHighlight else c.brand)
    }
}
