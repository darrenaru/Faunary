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

    /**
     * Fewer points for the same line: keeps every point needed to stay within [toleranceM] metres of the
     * original (Ramer–Douglas–Peucker), so bends and corners survive while straight stretches collapse.
     * When that is still more than [maxPoints], the tolerance grows until it fits. Ends are always kept.
     */
    fun simplifyLine(points: List<Pair<Double, Double>>, maxPoints: Int, toleranceM: Double = 2.0): List<Pair<Double, Double>> {
        if (points.size <= 2) return points
        var tolerance = toleranceM
        var kept = douglasPeucker(points, tolerance)
        while (kept.size > maxPoints) {
            tolerance *= 1.5
            kept = douglasPeucker(points, tolerance)
        }
        return kept
    }

    private fun douglasPeucker(points: List<Pair<Double, Double>>, toleranceM: Double): List<Pair<Double, Double>> {
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.size - 1] = true
        // Iterative, so very long routes can't overflow the stack.
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(0 to points.size - 1)
        while (stack.isNotEmpty()) {
            val (from, to) = stack.removeLast()
            var farthest = -1
            var farthestM = toleranceM
            for (i in from + 1 until to) {
                val d = offsetMeters(points[i], points[from], points[to])
                if (d > farthestM) {
                    farthestM = d
                    farthest = i
                }
            }
            if (farthest != -1) {
                keep[farthest] = true
                stack.addLast(from to farthest)
                stack.addLast(farthest to to)
            }
        }
        return points.filterIndexed { i, _ -> keep[i] }
    }

    /** Distance in metres from [p] to the segment [a]–[b], on a local flat projection (fine at street scale). */
    private fun offsetMeters(p: Pair<Double, Double>, a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val mLat = 110_540.0
        val mLng = 111_320.0 * cos(toRadians(a.first))
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

    private fun toRadians(degrees: Double) = degrees * PI / 180.0
}
