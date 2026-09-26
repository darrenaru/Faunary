package com.faunary.app.ui.map

import com.mapbox.geojson.Point
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/** Pins closer than this on screen overlap, so they are merged into one stack. */
const val PIN_STACK_DP = 44f

/** Stacks of up to this many photos fan out on tap; bigger ones open the list instead. */
const val SPIDER_MAX = 4

/** Distance (dp) from the spot to each fanned-out pin. */
const val SPIDER_RADIUS_DP = 62f

/** One photo pin on the map: a single find, or a stack of finds at (about) the same spot. */
data class PhotoPin(
    /** Newest first; the top photo is the one shown on the stack. */
    val members: List<MapMarker>,
) {
    val top: MapMarker get() = members.first()
    val isStack: Boolean get() = members.size > 1
    val key: String get() = if (isStack) "stack:${top.key}" else top.key
}

/** A stack fanned out around [center]: [members][i] is drawn at [positions][i], with a leg back to the spot. */
data class Spider(val pinKey: String, val center: Point, val members: List<MapMarker>, val positions: List<Point>)

/**
 * Merges pins that would overlap on screen at [zoom]. Positions are compared in Web-Mercator
 * "dp" (512-px tiles), bucketed in a grid of the stack radius, so this stays linear in the
 * number of pins. Greedy from the newest find, which then sits on top of its stack.
 */
fun groupPins(markers: List<MapMarker>, zoom: Double, radiusDp: Float = PIN_STACK_DP): List<PhotoPin> {
    if (markers.isEmpty()) return emptyList()
    val sorted = markers.sortedByDescending { it.time }
    val scale = 512.0 * 2.0.pow(zoom)
    val xs = DoubleArray(sorted.size)
    val ys = DoubleArray(sorted.size)
    val grid = HashMap<Long, MutableList<Int>>()
    fun cell(cx: Long, cy: Long) = (cx shl 32) xor (cy and 0xffffffffL)
    sorted.forEachIndexed { i, m ->
        val lat = Math.toRadians(m.latitude.coerceIn(-85.0, 85.0))
        xs[i] = (m.longitude + 180.0) / 360.0 * scale
        ys[i] = (1.0 - ln(tan(lat) + 1.0 / cos(lat)) / PI) / 2.0 * scale
        grid.getOrPut(cell(floor(xs[i] / radiusDp).toLong(), floor(ys[i] / radiusDp).toLong())) { mutableListOf() } += i
    }
    val taken = BooleanArray(sorted.size)
    val pins = ArrayList<PhotoPin>()
    for (i in sorted.indices) {
        if (taken[i]) continue
        taken[i] = true
        val group = mutableListOf(sorted[i])
        val cx = floor(xs[i] / radiusDp).toLong()
        val cy = floor(ys[i] / radiusDp).toLong()
        for (dx in -1L..1L) for (dy in -1L..1L) {
            grid[cell(cx + dx, cy + dy)]?.forEach { j ->
                if (!taken[j] && hypot(xs[j] - xs[i], ys[j] - ys[i]) <= radiusDp) {
                    taken[j] = true
                    group += sorted[j]
                }
            }
        }
        pins += PhotoPin(group.sortedByDescending { it.time })
    }
    return pins
}

/**
 * Screen offsets (dp) for fanning out [n] pins: evenly around a circle, rotated so two pins sit
 * left/right, three form a triangle and four a square, so no pin covers another.
 */
fun spiderOffsets(n: Int, radiusDp: Float = SPIDER_RADIUS_DP): List<Pair<Float, Float>> =
    (0 until n).map { i ->
        val a = Math.toRadians(-90.0 + 180.0 / n + i * 360.0 / n)
        (radiusDp * cos(a)).toFloat() to (radiusDp * sin(a)).toFloat()
    }
