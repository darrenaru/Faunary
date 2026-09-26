package com.faunary.app.ui.map

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.locationcomponent.DefaultLocationProvider
import com.mapbox.maps.plugin.locationcomponent.LocationConsumer
import com.mapbox.maps.plugin.locationcomponent.LocationProvider
import androidx.core.graphics.createBitmap

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

/** "You are here" arrow: blue chevron with a white rim and soft shadow, pointing up (north at bearing 0). */
fun locationArrowBitmap(density: Float): Bitmap {
    val size = (48 * density).toInt()
    val bmp = createBitmap(size, size)
    val canvas = Canvas(bmp)
    val s = size.toFloat()
    val path = Path().apply {
        moveTo(s * 0.50f, s * 0.12f) // tip
        lineTo(s * 0.80f, s * 0.84f)
        lineTo(s * 0.50f, s * 0.68f) // notch
        lineTo(s * 0.20f, s * 0.84f)
        close()
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
