package com.faunary.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object FaunaryIcons {
    /** Filled cat head: pointed ears, eyes and nose cut out. */
    val Cat: ImageVector by lazy {
        ImageVector.Builder("Cat", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(3.6f, 2.6f)
                lineTo(8.8f, 7.2f)
                quadTo(12f, 6.3f, 15.2f, 7.2f)
                lineTo(20.4f, 2.6f)
                lineTo(20.6f, 10.4f)
                curveTo(21.6f, 12.2f, 21.6f, 15.2f, 20.4f, 17.1f)
                curveTo(18.8f, 19.8f, 15.6f, 21.2f, 12f, 21.2f)
                curveTo(8.4f, 21.2f, 5.2f, 19.8f, 3.6f, 17.1f)
                curveTo(2.4f, 15.2f, 2.4f, 12.2f, 3.4f, 10.4f)
                close()
                // Eyes
                moveTo(8f, 13.2f)
                arcToRelative(1.35f, 1.6f, 0f, true, false, 2.7f, 0f)
                arcToRelative(1.35f, 1.6f, 0f, true, false, -2.7f, 0f)
                close()
                moveTo(13.3f, 13.2f)
                arcToRelative(1.35f, 1.6f, 0f, true, false, 2.7f, 0f)
                arcToRelative(1.35f, 1.6f, 0f, true, false, -2.7f, 0f)
                close()
                // Nose
                moveTo(10.7f, 16.1f)
                lineTo(13.3f, 16.1f)
                lineTo(12f, 17.7f)
                close()
            }
        }.build()
    }

    /** Filled dog head: floppy ears beside a rounded head, eyes and snout cut out. */
    val Dog: ImageVector by lazy {
        ImageVector.Builder("Dog", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                // Head
                moveTo(7.6f, 4.6f)
                curveTo(9.4f, 3.6f, 14.6f, 3.6f, 16.4f, 4.6f)
                curveTo(18f, 5.5f, 18.6f, 7.6f, 18.6f, 9.8f)
                lineTo(18.6f, 14.4f)
                curveTo(18.6f, 18.6f, 15.6f, 21.4f, 12f, 21.4f)
                curveTo(8.4f, 21.4f, 5.4f, 18.6f, 5.4f, 14.4f)
                lineTo(5.4f, 9.8f)
                curveTo(5.4f, 7.6f, 6f, 5.5f, 7.6f, 4.6f)
                close()
                // Eyes
                moveTo(8.3f, 11.2f)
                arcToRelative(1.3f, 1.3f, 0f, true, false, 2.6f, 0f)
                arcToRelative(1.3f, 1.3f, 0f, true, false, -2.6f, 0f)
                close()
                moveTo(13.1f, 11.2f)
                arcToRelative(1.3f, 1.3f, 0f, true, false, 2.6f, 0f)
                arcToRelative(1.3f, 1.3f, 0f, true, false, -2.6f, 0f)
                close()
                // Nose
                moveTo(9.9f, 15.6f)
                arcToRelative(2.1f, 1.5f, 0f, true, false, 4.2f, 0f)
                arcToRelative(2.1f, 1.5f, 0f, true, false, -4.2f, 0f)
                close()
            }
            // Ears, a separate shape so they don't cut holes where they meet the head
            path(fill = SolidColor(Color.Black)) {
                moveTo(4.5f, 4.9f)
                curveTo(2.7f, 5.1f, 1.2f, 7f, 1.4f, 9.8f)
                curveTo(1.6f, 12.4f, 2.7f, 14.5f, 4.5f, 14.7f)
                close()
                moveTo(19.5f, 4.9f)
                curveTo(21.3f, 5.1f, 22.8f, 7f, 22.6f, 9.8f)
                curveTo(22.4f, 12.4f, 21.3f, 14.5f, 19.5f, 14.7f)
                close()
            }
        }.build()
    }

    /** Plain outlined isometric cube for the 3D map toggle (tinted by Icon). */
    val Cube: ImageVector by lazy {
        ImageVector.Builder("Cube", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Outer hexagon
                moveTo(12f, 2.8f)
                lineTo(20.2f, 7.4f)
                lineTo(20.2f, 16.6f)
                lineTo(12f, 21.2f)
                lineTo(3.8f, 16.6f)
                lineTo(3.8f, 7.4f)
                close()
                // Inner edges meeting at the front corner
                moveTo(3.8f, 7.4f)
                lineTo(12f, 12f)
                lineTo(20.2f, 7.4f)
                moveTo(12f, 12f)
                lineTo(12f, 21.2f)
            }
        }.build()
    }

    /** Filled binoculars (two barrels with lens holes) for the "explorers online" layer. */
    val Binoculars: ImageVector by lazy {
        ImageVector.Builder("Binoculars", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                // Left barrel: narrow top tapering out into a round lens
                moveTo(7f, 4f)
                lineTo(10f, 4f)
                lineTo(10.6f, 12.4f)
                arcTo(4.4f, 4.4f, 0f, true, true, 2.9f, 15.6f)
                lineTo(6f, 5f)
                close()
                // Right barrel (mirror)
                moveTo(17f, 4f)
                lineTo(14f, 4f)
                lineTo(13.4f, 12.4f)
                arcTo(4.4f, 4.4f, 0f, true, false, 21.1f, 15.6f)
                lineTo(18f, 5f)
                close()
                // Lens holes
                moveTo(9.4f, 16.6f)
                arcTo(2f, 2f, 0f, true, true, 5.4f, 16.6f)
                arcTo(2f, 2f, 0f, true, true, 9.4f, 16.6f)
                close()
                moveTo(18.6f, 16.6f)
                arcTo(2f, 2f, 0f, true, true, 14.6f, 16.6f)
                arcTo(2f, 2f, 0f, true, true, 18.6f, 16.6f)
                close()
            }
            // Bridge between the barrels
            path(fill = SolidColor(Color.Black)) {
                moveTo(10.2f, 8.5f)
                lineTo(13.8f, 8.5f)
                lineTo(13.8f, 11f)
                lineTo(10.2f, 11f)
                close()
            }
        }.build()
    }
}
