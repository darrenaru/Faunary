package com.faunary.app.util

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Instant

// Indonesian names (the same ones java.text used for the id-ID locale).
private val MONTHS = listOf(
    "Januari", "Februari", "Maret", "April", "Mei", "Juni",
    "Juli", "Agustus", "September", "Oktober", "November", "Desember",
)
private val MONTHS_SHORT = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")
private val DAYS = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS
private const val DAY_MS = 24 * HOUR_MS

/** Wall-clock date/time of [timestamp] in the device's time zone. */
fun localDateTime(timestamp: Long): LocalDateTime =
    Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(TimeZone.currentSystemDefault())

object Format {
    fun relative(timestamp: Long, now: Long = currentTimeMillis()): String {
        val diff = now - timestamp
        val min = diff / MINUTE_MS
        val hours = diff / HOUR_MS
        val days = diff / DAY_MS
        return when {
            min < 1 -> "Baru saja"
            min < 60 -> "${min}m lalu"
            hours < 24 -> "${hours}j lalu"
            days < 7 -> "${days} hari lalu"
            else -> shortDate(timestamp)
        }
    }

    /** e.g. "25 Sep". */
    fun shortDate(timestamp: Long): String = localDateTime(timestamp).let { "${it.day} ${MONTHS_SHORT[it.month.number - 1]}" }

    /** e.g. "Kamis, 25 September 2026". */
    fun fullDate(timestamp: Long): String = localDateTime(timestamp).let {
        "${DAYS[it.dayOfWeek.isoDayNumber - 1]}, ${it.day} ${MONTHS[it.month.number - 1]} ${it.year}"
    }

    /** e.g. "07:05". */
    fun time(timestamp: Long): String = localDateTime(timestamp).let { "${pad2(it.hour)}:${pad2(it.minute)}" }

    /** e.g. "September 2026". */
    fun monthYear(timestamp: Long): String = localDateTime(timestamp).let { "${MONTHS[it.month.number - 1]} ${it.year}" }

    fun duration(seconds: Double): String {
        val totalMin = (seconds / 60).roundToInt().coerceAtLeast(1)
        return if (totalMin < 60) "$totalMin mnt" else "${totalMin / 60} j ${totalMin % 60} mnt"
    }

    fun percent(confidence: Float): String = "${(confidence * 100).roundToInt()}%"

    fun coordinates(lat: Double, lng: Double): String = "${decimal(lat, 6, '.')}, ${decimal(lng, 6, '.')}"

    fun distance(meters: Double): String = when {
        meters < 1000 -> "${meters.roundToInt()} m"
        else -> "${decimal(meters / 1000, 1, ',')} km"
    }

    /** Label for the part of the day, e.g. "Pagi hari". */
    fun dayPart(timestamp: Long): String = when (localDateTime(timestamp).hour) {
        in 4..10 -> "Pagi hari"
        in 11..14 -> "Siang hari"
        in 15..17 -> "Sore hari"
        else -> "Malam hari"
    }

    private fun pad2(n: Int) = n.toString().padStart(2, '0')

    /** [value] rounded half-up to [decimals] places, like "%.nf" ([separator] '.' as in Locale.US, ',' as in id-ID). */
    fun decimal(value: Double, decimals: Int, separator: Char = '.'): String {
        var factor = 1L
        repeat(decimals) { factor *= 10 }
        val scaled = (abs(value) * factor).roundToLong()
        val whole = scaled / factor
        val frac = (scaled % factor).toString().padStart(decimals, '0')
        val sign = if (value < 0 && scaled != 0L) "-" else ""
        return if (decimals == 0) "$sign$whole" else "$sign$whole$separator$frac"
    }
}
