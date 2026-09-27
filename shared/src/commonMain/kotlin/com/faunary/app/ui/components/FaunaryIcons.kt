package com.faunary.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object FaunaryIcons {
    /** Filled cat head (AT Icons "cat"). */
    val Cat: ImageVector by lazy {
        filledIcon(
            "Cat", 16f,
            "M12.293 1.707c.63-.63 1.707-.184 1.707.707V9l-.007.257A5 5 0 0 1 13.9 10h.6a.5.5 0 0 1 0 1h-.918A5 5 0 0 1 13 12h1.5a.5.5 0 0 1 0 1H12c-.835.628-1.874 1-3 1H7a4.98 4.98 0 0 1-3-1H1.5a.5.5 0 0 1 0-1H3a5 5 0 0 1-.583-1H1.5a.5.5 0 0 1 0-1h.6a5 5 0 0 1-.094-.743L2 9V2.414c0-.89 1.077-1.337 1.707-.707l2.378 2.378Q6.531 4.001 7 4h2q.47.001.914.085zM7.707 11a.707.707 0 0 0-.5 1.207l.44.44a.5.5 0 0 0 .707 0l.44-.44a.708.708 0 0 0-.5-1.207zM5.5 8a.5.5 0 0 0-.5.5v2a.5.5 0 0 0 1 0v-2a.5.5 0 0 0-.5-.5m5 0a.5.5 0 0 0-.5.5v2a.5.5 0 0 0 1 0v-2a.5.5 0 0 0-.5-.5",
        )
    }

    /** Filled dog head (Boxicons "dog-alt-filled"). */
    val Dog: ImageVector by lazy {
        filledIcon(
            "Dog", 24f,
            "M21.79 9.01c-.12-1.17-.31-2.31-.56-3.33c-.26-1.02-.56-1.83-.91-2.4C19.82 2.43 19.2 2 18.49 2s-1.33.43-1.83 1.28c-.23.39-.45.89-.65 1.49c-2.5-1.01-5.56-1.01-8.05 0c-.2-.6-.41-1.1-.64-1.48c-.5-.85-1.12-1.28-1.83-1.28s-1.33.43-1.83 1.28c-.35.58-.65 1.39-.91 2.42c-.25 1-.44 2.15-.55 3.31c-.14 1.25-.21 2.59-.21 4c0 .65.08 1.31.25 1.97c-.17.43-.25.86-.25 1.29c0 2.84 3.43 5.14 8.1 5.63c.81-.26 1.4-1 1.4-1.9v-.3c0-.41-.26-.78-.65-.93c-1.09-.43-1.85-1.43-1.85-2.44c0-.74 1.34-1.33 3-1.33s3 .6 3 1.33c0 1.01-.77 2.01-1.85 2.44c-.39.15-.65.51-.65.93v.3c0 .89.59 1.64 1.4 1.9c4.67-.5 8.1-2.79 8.1-5.63c0-.43-.08-.86-.25-1.29c.17-.66.25-1.32.25-1.97c0-1.41-.07-2.75-.21-3.99ZM7.5 14c-.83 0-1.5-.67-1.5-1.5S6.67 11 7.5 11s1.5.67 1.5 1.5S8.33 14 7.5 14m9 0c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5s1.5.67 1.5 1.5s-.67 1.5-1.5 1.5",
        )
    }

    /** Filled bird (AT Icons "bird"). */
    val Bird: ImageVector by lazy {
        filledIcon(
            "Bird", 16f,
            "M10.743 1a3.26 3.26 0 0 1 3.222 2.778L16 5l-2 1.2V7a6 6 0 0 1-5 5.915V14.5a.5.5 0 0 1-1 0V13H6v1.5a.5.5 0 0 1-1 0V13H2.868a1 1 0 0 1-.832-1.555l1.52-2.28A5.45 5.45 0 0 1 1 4.545c0-.3.245-.545.546-.545H7l1.033-1.55A3.26 3.26 0 0 1 10.743 1M11 3a1 1 0 1 0 0 2a1 1 0 0 0 0-2",
        )
    }

    /** Filled camera (Boxicons "camera-alt-filled"). */
    val Camera: ImageVector by lazy {
        filledIcon(
            "Camera", 24f,
            "M12 11a2 2 0 1 0 0 4a2 2 0 1 0 0-4",
            "M20 5h-3l-2.32-1.79a.98.98 0 0 0-.61-.21H9.93c-.22 0-.44.07-.61.21L7 5H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2m-8 12c-2.17 0-4-1.83-4-4s1.83-4 4-4s4 1.83 4 4s-1.83 4-4 4m7-8c-.55 0-1-.45-1-1s.45-1 1-1s1 .45 1 1s-.45 1-1 1",
        )
    }

    /** Filled person (Basil "user-solid"). */
    val User: ImageVector by lazy {
        filledIcon(
            "User", 24f,
            "M12 3.75a3.75 3.75 0 1 0 0 7.5a3.75 3.75 0 0 0 0-7.5m-4 9.5A3.75 3.75 0 0 0 4.25 17v1.188c0 .754.546 1.396 1.29 1.517c4.278.699 8.642.699 12.92 0a1.54 1.54 0 0 0 1.29-1.517V17A3.75 3.75 0 0 0 16 13.25h-.34q-.28.001-.544.086l-.866.283a7.25 7.25 0 0 1-4.5 0l-.866-.283a1.8 1.8 0 0 0-.543-.086z",
        )
    }

    /** Outlined bell (Cuida "notification-bell-outline"). */
    val Bell: ImageVector by lazy {
        filledIcon(
            "Bell", 24f,
            "M10.51 5.22c-.621.168-1.201.431-1.605.795c-1.103.995-1.552 2.343-1.552 4.773c0 1.732-.986 3.372-1.696 4.434c-.154.232-.202.453-.193.586c.004.06.018.088.024.098c.004.007.016.026.064.052c.692.37 1.736.64 2.943.811A26 26 0 0 0 12 17a1 1 0 1 1 0 2c-1.128 0-2.484-.065-3.786-.25c-1.282-.183-2.603-.493-3.605-1.028c-.737-.394-1.093-1.08-1.14-1.779c-.046-.659.177-1.31.525-1.831c.717-1.074 1.359-2.259 1.359-3.324c0-2.661.492-4.707 2.213-6.258c.725-.654 1.636-1.027 2.419-1.24A8 8 0 0 1 12 3a1 1 0 1 1 0 2c-.306 0-.876.052-1.49.22",
            "M13.49 5.22c.621.168 1.201.431 1.605.795c1.103.995 1.552 2.343 1.552 4.773c0 1.732.986 3.372 1.696 4.434c.154.232.202.453.193.586a.2.2 0 0 1-.024.098c-.004.007-.016.026-.064.052c-.692.37-1.736.64-2.943.811A26 26 0 0 1 12 17a1 1 0 1 0 0 2c1.128 0 2.483-.065 3.786-.25c1.282-.183 2.603-.493 3.605-1.028c.737-.394 1.093-1.08 1.14-1.779c.046-.659-.177-1.31-.525-1.831c-.717-1.074-1.359-2.259-1.359-3.324c0-2.661-.492-4.707-2.213-6.258c-.725-.654-1.636-1.027-2.419-1.24A8 8 0 0 0 12 3a1 1 0 1 0 0 2c.306 0 .876.052 1.49.22",
            "M14.647 3.68c0 .695-1.396.042-2.5.042s-2.5.653-2.5-.043c0-.695 1-1.679 2.5-1.679s2.5.984 2.5 1.68",
            "M10 18a2 2 0 1 0 4 0h2a4 4 0 0 1-8 0z",
            fillType = PathFillType.EvenOdd,
        )
    }

    /** Filled location arrow (Jam Icons "gps-f") for "my location" buttons. */
    val Gps: ImageVector by lazy {
        // The source viewBox is "-2 -2 24 24": shift the path so it sits on a plain 0..24 canvas.
        ImageVector.Builder("Gps", 24.dp, 24.dp, 24f, 24f).apply {
            group(translationX = 2f, translationY = 2f) {
                addPath(
                    addPathNodes("m18.919 2.635l-5.953 16.08c-.376 1.016-1.459 1.538-2.418 1.165a1.85 1.85 0 0 1-1.045-1.054l-1.887-4.77a3.7 3.7 0 0 0-1.955-2.052l-4.542-1.981C.174 9.61-.256 8.465.157 7.465a1.97 1.97 0 0 1 1.067-1.079L16.54.136c.967-.395 2.04.101 2.395 1.109c.157.446.151.94-.015 1.39z"),
                    fill = SolidColor(Color.Black),
                )
            }
        }.build()
    }

    /** Crosshair target for "follow my position" while navigating. */
    val Crosshair: ImageVector by lazy {
        filledIcon(
            "Crosshair", 512f,
            "m255.863281 168.699219c-48.046875 0-87.140625 39.09375-87.140625 87.144531 0 48.046875 39.09375 87.140625 87.140625 87.140625 48.050781 0 87.144531-39.09375 87.144531-87.140625 0-48.050781-39.09375-87.144531-87.144531-87.144531zm0 0",
            "m497.003906 240.84375h-55.054687c-7.269531-91.003906-80.082031-163.820312-171.089844-171.085938v-54.761718c0-8.28125-6.714844-14.996094-14.996094-14.996094s-14.996093 6.714844-14.996093 14.996094v54.761718c-91.007813 7.265626-163.820313 80.082032-171.089844 171.085938h-54.78125c-8.28125 0-14.996094 6.714844-14.996094 15 0 8.28125 6.714844 14.996094 14.996094 14.996094h54.78125c7.269531 91.003906 80.082031 163.820312 171.089844 171.089844v54.757812c0 8.28125 6.714843 14.996094 14.996093 14.996094 8.285157 0 14.996094-6.714844 14.996094-14.996094v-54.757812c91.007813-7.269532 163.824219-80.085938 171.089844-171.089844h55.054687c8.28125 0 14.996094-6.714844 14.996094-14.996094 0-8.285156-6.714844-15-14.996094-15zm-241.140625 171.695312c-86.402343 0-156.695312-70.296874-156.695312-156.695312 0-86.402344 70.292969-156.699219 156.695312-156.699219 86.402344 0 156.695313 70.296875 156.695313 156.699219 0 86.398438-70.292969 156.695312-156.695313 156.695312zm0 0",
        )
    }

    /** Outlined folded map (Hugeicons "maps"). */
    val Maps: ImageVector by lazy {
        strokedIcon(
            "Maps", 24f, 1.5f,
            "m5.253 4.196l-1.227.712c-.989.573-1.483.86-1.754 1.337C2 6.722 2 7.302 2 8.464v8.164c0 1.526 0 2.29.342 2.714c.228.282.547.472.9.535c.53.095 1.18-.282 2.478-1.035c.882-.511 1.73-1.043 2.785-.898c.48.065.937.293 1.853.748l3.813 1.896c.825.41.833.412 1.75.412H18c1.886 0 2.828 0 3.414-.599c.586-.598.586-1.562.586-3.49v-6.74c0-1.927 0-2.89-.586-3.49c-.586-.598-1.528-.598-3.414-.598h-2.079c-.917 0-.925-.002-1.75-.412L10.84 4.015C9.449 3.323 8.753 2.977 8.012 3S6.6 3.415 5.253 4.196M8 3v14.5m7-11v14",
        )
    }

    /**
     * Outlined photo gallery (Arcticons "gallery"). Arcticons are drawn with a 1-unit line on a 48 grid,
     * hairline at icon size, so the stroke is thickened to match the other 1.5/24 outlined icons.
     */
    val Gallery: ImageVector by lazy {
        strokedIcon(
            "Gallery", 48f, ARCTICONS_STROKE,
            "M31.315 12.123a4.465 4.465 0 1 1 0 8.93a4.465 4.465 0 0 1 0-8.93m-11.294 8.909l7.224 7.223a.7.7 0 0 0 .992 0l1.383-1.383a.7.7 0 0 1 .993 0l7.807 7.807a.702.702 0 0 1-.497 1.198H10.076a.702.702 0 0 1-.577-1.101l9.45-13.648a.702.702 0 0 1 1.072-.097Z",
            "M38.5 5.5h-29a4 4 0 0 0-4 4v29a4 4 0 0 0 4 4h29a4 4 0 0 0 4-4v-29a4 4 0 0 0-4-4",
        )
    }

    /** Outlined journal with a bookmark ribbon (Arcticons "journal"); stroke thickened as for [Gallery]. */
    val Journal: ImageVector by lazy {
        strokedIcon(
            "Journal", 48f, ARCTICONS_STROKE,
            // The front cover (an SVG rounded <rect> in the source)
            "M15.201 5.5H36.329A6.171 6.171 0 0 1 42.5 11.671V32.799A6.171 6.171 0 0 1 36.329 38.97H15.201A6.171 6.171 0 0 1 9.03 32.799V11.671A6.171 6.171 0 0 1 15.201 5.5Z",
            "M38.55 38.547A6.15 6.15 0 0 1 32.8 42.5H11.673A6.17 6.17 0 0 1 5.5 36.326V15.2c0-2.63 1.64-4.865 3.953-5.75m27.376-3.93v13.213l-4.226-3.948l-4.225 3.948V5.5",
        )
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

    private const val ARCTICONS_STROKE = 3f

    /** Solid icon from SVG path data, drawn black so [androidx.compose.material3.Icon] can tint it. */
    private fun filledIcon(name: String, viewport: Float, vararg paths: String, fillType: PathFillType = PathFillType.NonZero) =
        ImageVector.Builder(name, 24.dp, 24.dp, viewport, viewport).apply {
            paths.forEach { addPath(addPathNodes(it), fill = SolidColor(Color.Black), pathFillType = fillType) }
        }.build()

    /** Outlined icon from SVG path data (round caps and joins, as in the source icons). */
    private fun strokedIcon(name: String, viewport: Float, strokeWidth: Float, vararg paths: String) =
        ImageVector.Builder(name, 24.dp, 24.dp, viewport, viewport).apply {
            paths.forEach {
                addPath(
                    addPathNodes(it),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = strokeWidth,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
}
