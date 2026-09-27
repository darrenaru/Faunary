package com.faunary.app.util

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    /** Haversine distance in metres. */
    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        val dLat = toRadians(lat2 - lat1)
        val dLng = toRadians(lng2 - lng1)
        val a = sin(dLat / 2).let { it * it } +
            cos(toRadians(lat1)) * cos(toRadians(lat2)) * sin(dLng / 2).let { it * it }
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Groups coordinates into ~100m cells, used to count distinct spots. */
    fun cellKey(lat: Double, lng: Double): String = "${(lat * 1000).roundToInt()}:${(lng * 1000).roundToInt()}"

    private fun toRadians(degrees: Double) = degrees * PI / 180.0
}
