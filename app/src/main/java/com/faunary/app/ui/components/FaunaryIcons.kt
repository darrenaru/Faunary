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
