package com.faunary.app.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** The shared Format hand-rolls Indonesian dates; it must match what java.text printed before. */
class FormatTest {
    private val id = Locale.forLanguageTag("id-ID")

    private fun javaFormat(pattern: String, ts: Long) = SimpleDateFormat(pattern, id).format(Date(ts))

    /** Every day of 2026 at varying times, in two time zones (WIB and UTC). */
    private val samples: List<Long> = listOf("Asia/Jakarta", "UTC").flatMap { zone ->
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
        (0 until 366).map { day ->
            Calendar.getInstance().apply {
                clear()
                set(2026, Calendar.JANUARY, 1, (day * 7) % 24, (day * 13) % 60)
                add(Calendar.DAY_OF_YEAR, day)
            }.timeInMillis
        }
    }

    @Test
    fun datesMatchJavaText() {
        for (zone in listOf("Asia/Jakarta", "UTC")) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone))
            for (ts in samples) {
                assertEquals(javaFormat("d MMM", ts), Format.shortDate(ts))
                assertEquals(javaFormat("EEEE, d MMMM yyyy", ts), Format.fullDate(ts))
                assertEquals(javaFormat("HH:mm", ts), Format.time(ts))
                assertEquals(javaFormat("MMMM yyyy", ts), Format.monthYear(ts))
            }
        }
    }

    @Test
    fun numbersMatchJavaText() {
        val coords = listOf(-6.2088 to 106.8456, 51.5007292 to -0.1246254, -33.8688197 to 151.2092955, 0.0000004 to -12.0)
        for ((lat, lng) in coords) {
            assertEquals(String.format(Locale.US, "%.6f, %.6f", lat, lng), Format.coordinates(lat, lng))
        }
        for (m in listOf(1000.0, 1049.0, 1051.0, 12_345.6, 4_807_412.0)) {
            assertEquals(String.format(id, "%.1f km", m / 1000), Format.distance(m))
        }
        assertEquals("999 m", Format.distance(999.4))
        // Deliberate difference: java.text printed "-0.000000" for tiny negative values.
        assertEquals("0.000000, 0.000000", Format.coordinates(0.0, -0.0000004))
    }

    @Test
    fun relativeTimes() {
        val now = 1_790_000_000_000L
        assertEquals("Baru saja", Format.relative(now - 30_000, now))
        assertEquals("5m lalu", Format.relative(now - 5 * 60_000, now))
        assertEquals("3j lalu", Format.relative(now - 3 * 3_600_000, now))
        assertEquals("2 hari lalu", Format.relative(now - 2 * 86_400_000, now))
    }
}
