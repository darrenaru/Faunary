package com.faunary.app.location

import com.faunary.app.util.Geo
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Where the user is along a [Route] during navigation. */
data class RouteProgress(
    /** Position snapped onto the route line. */
    val snapped: Pair<Double, Double>,
    /** Distance from the GPS fix to the route line; large values mean the user left the route. */
    val offRouteMeters: Double,
    /** Index of the route segment (points[i] → points[i+1]) the user is on. */
    val segmentIndex: Int,
    val remainingMeters: Double,
    val remainingSeconds: Double,
    /** Next manoeuvre to announce, or null once only the arrival is left. */
    val nextStep: RouteStep?,
    val nextStepIndex: Int,
    val distanceToNextStep: Double,
    /** Direction of travel along the route, degrees clockwise from north. */
    val bearing: Double,
)

/** Follows GPS fixes along one route. Create a new tracker whenever the route changes. */
class RouteTracker(val route: Route) {
    private val pts = route.points
    /** Distance along the route at each point. */
    private val cum = DoubleArray(pts.size).also { c ->
        for (i in 1 until pts.size) c[i] = c[i - 1] + Geo.distanceMeters(pts[i - 1].first, pts[i - 1].second, pts[i].first, pts[i].second)
    }
    private val total = cum.lastOrNull() ?: 0.0

    /** Distance along the route of each step's manoeuvre point, searched forwards so loops don't confuse it. */
    private val stepAlong: List<Double> = run {
        var from = 0
        route.steps.map { st ->
            var best = from
            var bestD = Double.MAX_VALUE
            for (i in from until pts.size) {
                val d = Geo.distanceMeters(st.latitude, st.longitude, pts[i].first, pts[i].second)
                if (d < bestD) { bestD = d; best = i }
            }
            from = best
            cum.getOrElse(best) { 0.0 }
        }
    }

    private var hint = 0

    fun update(lat: Double, lng: Double): RouteProgress {
        if (pts.size < 2) {
            val d = if (pts.isEmpty()) 0.0 else Geo.distanceMeters(lat, lng, pts[0].first, pts[0].second)
            return RouteProgress(lat to lng, 0.0, 0, d, route.durationSeconds, null, route.steps.size, d, 0.0)
        }
        // Local flat projection around the fix (metres); plenty accurate at street scale.
        val mLat = 110_540.0
        val mLng = 111_320.0 * cos(toRadians(lat))
        var bestI = hint
        var bestT = 0.0
        var bestD = Double.MAX_VALUE
        // Don't jump back more than a few segments: avoids snapping to the opposite side of an out-and-back.
        for (i in (hint - 3).coerceAtLeast(0) until pts.size - 1) {
            val ax = (pts[i].second - lng) * mLng
            val ay = (pts[i].first - lat) * mLat
            val dx = (pts[i + 1].second - pts[i].second) * mLng
            val dy = (pts[i + 1].first - pts[i].first) * mLat
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
            val cx = ax + t * dx
            val cy = ay + t * dy
            val d = sqrt(cx * cx + cy * cy)
            if (d < bestD) { bestD = d; bestI = i; bestT = t }
        }
        hint = bestI
        val a = pts[bestI]
        val b = pts[bestI + 1]
        val snapped = (a.first + (b.first - a.first) * bestT) to (a.second + (b.second - a.second) * bestT)
        val traveled = cum[bestI] + (cum[bestI + 1] - cum[bestI]) * bestT
        val remaining = (total - traveled).coerceAtLeast(0.0)

        // Next manoeuvre strictly ahead of us (a couple of metres of slack so we move on once past it).
        val next = stepAlong.indexOfFirst { it > traveled + 3.0 }.let { if (it == -1) route.steps.size else it }
        val nextStep = route.steps.getOrNull(next)?.takeIf { it.type != "arrive" }
        val toNext = if (nextStep != null) stepAlong[next] - traveled else remaining

        return RouteProgress(
            snapped = snapped,
            offRouteMeters = bestD,
            segmentIndex = bestI,
            remainingMeters = remaining,
            remainingSeconds = if (total > 0) route.durationSeconds * remaining / total else 0.0,
            nextStep = nextStep,
            nextStepIndex = next,
            distanceToNextStep = toNext.coerceAtLeast(0.0),
            bearing = headingFrom(bestI, snapped),
        )
    }

    /** Remaining part of the route line, starting at the snapped position. */
    fun remainingPoints(p: RouteProgress): List<Pair<Double, Double>> =
        listOf(p.snapped) + pts.subList(p.segmentIndex + 1, pts.size)

    /** Bearing towards a point ~15 m ahead on the route, so tiny zig-zags don't spin the map. */
    private fun headingFrom(segment: Int, from: Pair<Double, Double>): Double {
        var j = segment + 1
        while (j < pts.size - 1 && Geo.distanceMeters(from.first, from.second, pts[j].first, pts[j].second) < 15.0) j++
        return bearing(from.first, from.second, pts[j].first, pts[j].second)
    }

    private fun bearing(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val p1 = toRadians(lat1)
        val p2 = toRadians(lat2)
        val dl = toRadians(lng2 - lng1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (toDegrees(atan2(y, x)) + 360.0) % 360.0
    }
}

private fun toRadians(degrees: Double) = degrees * PI / 180.0

private fun toDegrees(radians: Double) = radians * 180.0 / PI
