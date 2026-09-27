package com.faunary.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sqrt

/** Shared routes are simplified before upload; they must still follow the roads on other maps. */
class SimplifyLineTest {
    private val mLat = 110_540.0
    private val mLng = 111_320.0 * cos(Math.toRadians(1.5))

    /**
     * A city-grid route near Manado: [blocks] alternating east/north legs of [blockM] metres, with a
     * point every metre like Mapbox's full geometry. Returns the points and the corners.
     */
    private fun staircase(blocks: Int, blockM: Int = 60): Pair<List<Pair<Double, Double>>, List<Pair<Double, Double>>> {
        var lat = 1.5
        var lng = 124.8
        val points = mutableListOf(lat to lng)
        val corners = mutableListOf<Pair<Double, Double>>()
        repeat(blocks) { b ->
            repeat(blockM) {
                if (b % 2 == 0) lng += 1 / mLng else lat += 1 / mLat
                points += lat to lng
            }
            corners += lat to lng
        }
        return points to corners.dropLast(1)
    }

    /** Largest distance (m) from any original point to the simplified line. */
    private fun maxOffset(original: List<Pair<Double, Double>>, line: List<Pair<Double, Double>>): Double =
        original.maxOf { p ->
            (1 until line.size).minOf { i -> segmentDistance(p, line[i - 1], line[i]) }
        }

    private fun segmentDistance(p: Pair<Double, Double>, a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val bx = (b.second - a.second) * mLng
        val by = (b.first - a.first) * mLat
        val px = (p.second - a.second) * mLng
        val py = (p.first - a.first) * mLat
        val len2 = bx * bx + by * by
        val t = if (len2 == 0.0) 0.0 else ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
        val dx = px - t * bx
        val dy = py - t * by
        return sqrt(dx * dx + dy * dy)
    }

    @Test
    fun keepsEveryCornerOfARoute() {
        val (points, corners) = staircase(blocks = 50) // 3 km, 3 001 points
        val simplified = Geo.simplifyLine(points, maxPoints = 400)
        assertTrue("every turn is kept", simplified.containsAll(corners))
        assertTrue("straight stretches collapse (${simplified.size} points)", simplified.size <= corners.size + 2)
        assertTrue("stays on the road", maxOffset(points, simplified) <= 2.0)
        assertEquals(points.first(), simplified.first())
        assertEquals(points.last(), simplified.last())
    }

    @Test
    fun longRoutesFitTheLimitAndStayClose() {
        val (points, _) = staircase(blocks = 1_000, blockM = 20) // 20 km, more turns than points allowed
        val simplified = Geo.simplifyLine(points, maxPoints = 400)
        assertTrue("fits the limit (${simplified.size})", simplified.size <= 400)
        assertEquals(points.first(), simplified.first())
        assertEquals(points.last(), simplified.last())
        // Index-based thinning (the old way) drifts off by up to a whole block; this stays within one.
        assertTrue("close to the road (${maxOffset(points, simplified)} m)", maxOffset(points, simplified) < 20.0)
    }

    @Test
    fun shortLinesAreUnchanged() {
        val line = listOf(1.5 to 124.8, 1.5001 to 124.8001)
        assertEquals(line, Geo.simplifyLine(line, maxPoints = 400))
    }
}
