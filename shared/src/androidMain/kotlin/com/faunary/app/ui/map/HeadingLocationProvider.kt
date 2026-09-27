package com.faunary.app.ui.map

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.PathParser
import androidx.core.graphics.createBitmap
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.locationcomponent.DefaultLocationProvider
import com.mapbox.maps.plugin.locationcomponent.LocationConsumer
import com.mapbox.maps.plugin.locationcomponent.LocationProvider
import kotlin.math.max

/**
 * GPS position (and accuracy) from Mapbox's default provider, but the puck's direction comes from
 * the phone's own orientation sensor via [updateHeading], so the arrow turns the instant the phone does.
 * Until a heading arrives (or on phones without the sensor) the GPS course is used instead.
 */
class HeadingLocationProvider(context: Context) : LocationProvider {
    private val gps = DefaultLocationProvider(context).apply { updatePuckBearing(PuckBearing.COURSE) }
    /** Map consumer → proxy registered on [gps] (the proxy drops GPS bearings while we have a heading). */
    private val consumers = mutableMapOf<LocationConsumer, LocationConsumer>()
    private var heading: Double? = null

    override fun registerLocationConsumer(locationConsumer: LocationConsumer) {
        val proxy = object : LocationConsumer by locationConsumer {
            override fun onBearingUpdated(vararg bearing: Double, options: (ValueAnimator.() -> Unit)?) {
                if (heading == null) locationConsumer.onBearingUpdated(*bearing, options = options)
            }
        }
        consumers[locationConsumer] = proxy
        gps.registerLocationConsumer(proxy)
        heading?.let { locationConsumer.onBearingUpdated(it, options = instant) }
    }

    override fun unRegisterLocationConsumer(locationConsumer: LocationConsumer) {
        consumers.remove(locationConsumer)?.let(gps::unRegisterLocationConsumer)
    }

    /** Degrees clockwise from true north; call on the main thread. */
    fun updateHeading(degrees: Double) {
        heading = degrees
        consumers.keys.forEach { it.onBearingUpdated(degrees, options = instant) }
    }

    private companion object {
        /** Headings arrive ~50×/s already smoothed: apply them without the puck's default easing. */
        val instant: ValueAnimator.() -> Unit = { duration = 0 }
    }
}

/**
 * "You are here" arrow (Jam Icons "gps-f"): blue with a white rim and soft shadow, pointing up
 * (north at bearing 0) so the puck can rotate it with the heading.
 */
fun locationArrowBitmap(density: Float): Bitmap {
    val size = (48 * density).toInt()
    val bmp = createBitmap(size, size)
    val canvas = Canvas(bmp)
    val s = size.toFloat()
    val path = PathParser.createPathFromPathData(GPS_ARROW_PATH).apply {
        // The icon points to the upper right; turn it to point straight up.
        transform(Matrix().apply { setRotate(-45f) })
        // Centre it on the bitmap and scale it to leave room for the rim and shadow.
        val bounds = RectF().also { computeBounds(it, true) }
        val scale = s * 0.62f / max(bounds.width(), bounds.height())
        transform(Matrix().apply {
            setTranslate(-bounds.centerX(), -bounds.centerY())
            postScale(scale, scale)
            postTranslate(s / 2, s / 2)
        })
    }
    val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f * density
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
        setShadowLayer(3f * density, 0f, 1f * density, 0x66000000)
    }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF2F7DE1.toInt()
    }
    canvas.drawPath(path, rim)
    canvas.drawPath(path, fill)
    return bmp
}

/** Path data of the Jam Icons "gps-f" arrow (source viewBox -2 -2 24 24). */
private const val GPS_ARROW_PATH =
    "m18.919 2.635l-5.953 16.08c-.376 1.016-1.459 1.538-2.418 1.165a1.85 1.85 0 0 1-1.045-1.054l-1.887-4.77a3.7 3.7 0 0 0-1.955-2.052l-4.542-1.981C.174 9.61-.256 8.465.157 7.465a1.97 1.97 0 0 1 1.067-1.079L16.54.136c.967-.395 2.04.101 2.395 1.109c.157.446.151.94-.015 1.39z"
