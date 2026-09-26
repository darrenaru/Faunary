package com.faunary.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private val ID = Locale.forLanguageTag("id-ID")

object Format {
    fun relative(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val diff = now - timestamp
        val min = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        return when {
            min < 1 -> "Baru saja"
            min < 60 -> "${min}m lalu"
            hours < 24 -> "${hours}j lalu"
            days < 7 -> "${days} hari lalu"
            else -> shortDate(timestamp)
        }
    }

    fun shortDate(timestamp: Long): String = SimpleDateFormat("d MMM", ID).format(Date(timestamp))

    fun fullDate(timestamp: Long): String = SimpleDateFormat("EEEE, d MMMM yyyy", ID).format(Date(timestamp))

    fun time(timestamp: Long): String = SimpleDateFormat("HH:mm", ID).format(Date(timestamp))

    fun monthYear(timestamp: Long): String = SimpleDateFormat("MMMM yyyy", ID).format(Date(timestamp))

    fun percent(confidence: Float): String = "${(confidence * 100).roundToInt()}%"

    fun coordinates(lat: Double, lng: Double): String =
        String.format(Locale.US, "%.6f, %.6f", lat, lng)

    fun distance(meters: Double): String = when {
        meters < 1000 -> "${meters.roundToInt()} m"
        else -> String.format(ID, "%.1f km", meters / 1000)
    }

    /** Label for the part of the day, e.g. "Pagi hari". */
    fun dayPart(timestamp: Long): String {
        val h = Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.HOUR_OF_DAY)
        return when (h) {
            in 4..10 -> "Pagi hari"
            in 11..14 -> "Siang hari"
            in 15..17 -> "Sore hari"
            else -> "Malam hari"
        }
    }
}

object Geo {
    /** Haversine distance in metres. */
    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).let { it * it }
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Groups coordinates into ~100m cells, used to count distinct spots. */
    fun cellKey(lat: Double, lng: Double): String = "${(lat * 1000).roundToInt()}:${(lng * 1000).roundToInt()}"

    fun isNear(a: Double, b: Double) = abs(a - b) < 1e-9
}
