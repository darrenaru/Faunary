package com.faunary.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import com.faunary.app.domain.AnimalCategory
import com.faunary.app.util.Geo
import com.faunary.app.util.currentTimeMillis
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/*
 * The map's animations on iOS, ported from Android's FaunaMap (same timings and colours): the route
 * drawn in along the road, other explorers' routes, the heartbeat of live explorers and a shared
 * marker being deployed. Each runs on the frame clock and pushes its shapes through [NativeMapView].
 */

private const val ROUTE_COLOR = "#DF6D41"
private const val SHARED_ROUTE_COLOR = "#AAA648"
private const val LIVE_COLOR = "#AAA648"
private const val DEPLOY_COLOR = "#DF6D41"

/** A route shown more than this long ago is drawn whole (e.g. after a theme change), not drawn in. */
private const val ROUTE_DRAW_FRESH_MS = 15_000L

private const val HEARTBEAT_PERIOD_MS = 3_000L
private const val HEARTBEAT_RING_MS = 1_200L

private const val DEPLOY_DROP = 60.0
private const val DEPLOY_DROP_MS = 450f
private const val DEPLOY_PULSE_MS = 1_000f
private const val DEPLOY_POP_MS = 450f
private const val DEPLOY_FAIL_MS = 400f

/** Draw-in time grows with the route's length: 1.2 s for a short walk up to 2.5 s for long drives. */
private fun routeDrawMs(meters: Double): Long = (1_200 + meters / 5_000 * 1_300).toLong().coerceIn(1_200, 2_500)

private fun easeInOut(t: Float): Float = if (t < 0.5f) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2

private fun easeOut(t: Float): Float = 1f - (1f - t).pow(3)

private fun bounceOut(t: Float): Float = when {
    t < 0.7f -> easeOut(t / 0.7f)
    else -> 1f - 0.12f * sin(((t - 0.7f) / 0.3f) * PI).toFloat()
}

private fun lengthOf(points: List<Pair<Double, Double>>): Double =
    (1 until points.size).sumOf { Geo.distanceMeters(points[it - 1].first, points[it - 1].second, points[it].first, points[it].second) }

/** The first [fraction] of the line (by length), ending exactly on the interpolated point. */
private fun partialLine(points: List<Pair<Double, Double>>, fraction: Float): List<Pair<Double, Double>> {
    if (fraction >= 1f || points.size < 2) return points
    val cum = DoubleArray(points.size)
    for (i in 1 until points.size) {
        cum[i] = cum[i - 1] + Geo.distanceMeters(points[i - 1].first, points[i - 1].second, points[i].first, points[i].second)
    }
    val goal = cum.last() * fraction.coerceAtLeast(0f)
    val i = cum.indexOfFirst { it >= goal }.coerceAtLeast(1)
    val (aLat, aLng) = points[i - 1]
    val (bLat, bLng) = points[i]
    val seg = cum[i] - cum[i - 1]
    val k = if (seg > 0) (goal - cum[i - 1]) / seg else 0.0
    return points.subList(0, i) + ((aLat + (bLat - aLat) * k) to (aLng + (bLng - aLng) * k))
}

private fun List<Pair<Double, Double>>.flat(): List<Double> = flatMap { listOf(it.first, it.second) }

/** Glowing dot riding the tip of a line being drawn in. */
private fun tipCircle(point: Pair<Double, Double>, color: String) =
    NativeCircle(point.first, point.second, radius = 6.0, color = color, opacity = 1.0, strokeColor = "#FFFFFF", strokeWidth = 2.5, strokeOpacity = 1.0)

/**
 * The in-app route: a casing plus the orange line. A route that was just shown is drawn in along the
 * road with a glowing tip; while navigating ([fitRoute] false) the line is only updated in place.
 */
@Composable
internal fun RouteLine(
    native: NativeMapView,
    route: List<Pair<Double, Double>>?,
    routeDrawnAt: Long,
    fitRoute: Boolean,
    darkTheme: Boolean,
    onFit: (List<Pair<Double, Double>>) -> Unit,
) {
    val drawn = remember { longArrayOf(0L) }
    val casing = if (darkTheme) "#29231F" else "#FBF8F1"
    LaunchedEffect(native, route, darkTheme) {
        val pts = route
        if (pts == null || pts.size < 2) {
            native.setRoute(emptyList(), ROUTE_COLOR, casing)
            native.setCircles(LAYER_ROUTE_TIP, emptyList())
            return@LaunchedEffect
        }
        val drawIn = fitRoute && routeDrawnAt != drawn[0] && currentTimeMillis() - routeDrawnAt < ROUTE_DRAW_FRESH_MS
        drawn[0] = routeDrawnAt
        if (fitRoute) onFit(pts)
        if (!drawIn) {
            native.setRoute(pts.flat(), ROUTE_COLOR, casing)
            return@LaunchedEffect
        }
        val duration = routeDrawMs(lengthOf(pts)).toFloat()
        try {
            val start = withFrameMillis { it }
            while (true) {
                val t = ((withFrameMillis { it } - start) / duration).coerceIn(0f, 1f)
                val part = partialLine(pts, easeInOut(t))
                native.setRoute(part.flat(), ROUTE_COLOR, casing)
                native.setCircles(LAYER_ROUTE_TIP, listOf(tipCircle(part.last(), ROUTE_COLOR)))
                if (t >= 1f) break
            }
        } finally {
            native.setCircles(LAYER_ROUTE_TIP, emptyList())
        }
    }
}

/**
 * Other explorers' routes (olive) with a dot at each destination. A route that has just started is
 * drawn in along the road, the same animation its owner sees.
 */
@Composable
internal fun SharedRouteLines(native: NativeMapView, sharedRoutes: List<SharedRouteLine>, darkTheme: Boolean) {
    // Which start time of each route has been shown, so each route is drawn in only once.
    val shown = remember { mutableMapOf<String, Long>() }
    LaunchedEffect(native, sharedRoutes, darkTheme) {
        val routes = sharedRoutes.filter { it.points.size >= 2 }
        val now = currentTimeMillis()
        val fresh = routes.filter { r ->
            val isNew = shown[r.key] != r.startedAt
            shown[r.key] = r.startedAt
            isNew && r.startedAt > 0 && (now - r.startedAt) in -ROUTE_DRAW_FRESH_MS..ROUTE_DRAW_FRESH_MS
        }.map { it.key }.toSet()
        shown.keys.retainAll(routes.map { it.key }.toSet())
        val dests = routes.map {
            NativeCircle(it.destLat, it.destLng, radius = 7.0, color = SHARED_ROUTE_COLOR, opacity = 1.0,
                strokeColor = if (darkTheme) "#29231F" else "#FBF8F1", strokeWidth = 2.5, strokeOpacity = 1.0)
        }
        native.setCircles(LAYER_SHARED_DESTS, dests)
        if (fresh.isEmpty()) {
            native.setSharedRoutes(routes.map { it.points.flat() }, SHARED_ROUTE_COLOR)
            return@LaunchedEffect
        }
        val duration = routes.filter { it.key in fresh }.maxOf { routeDrawMs(lengthOf(it.points)) }.toFloat()
        val start = withFrameMillis { it }
        while (true) {
            val t = ((withFrameMillis { it } - start) / duration).coerceIn(0f, 1f)
            val lines = routes.map { if (it.key in fresh) partialLine(it.points, easeInOut(t)) else it.points }
            native.setSharedRoutes(lines.map { it.flat() }, SHARED_ROUTE_COLOR)
            if (t >= 1f) break
        }
    }
}

/** Live explorers "beat" every [HEARTBEAT_PERIOD_MS]: an olive ring radiates out from each avatar. */
@Composable
internal fun LiveHeartbeat(native: NativeMapView, liveMarkers: List<MapMarker>) {
    val current by rememberUpdatedState(liveMarkers)
    val keys = liveMarkers.map { it.key }.toSet()
    LaunchedEffect(native, keys) {
        try {
            while (isActive) {
                val start = withFrameMillis { it }
                var elapsed = 0L
                while (elapsed < HEARTBEAT_PERIOD_MS) {
                    elapsed = withFrameMillis { it } - start
                    val t = (elapsed.toFloat() / HEARTBEAT_RING_MS).coerceIn(0f, 1f)
                    // Rings follow the avatars, which move as explorers walk.
                    native.setCircles(LAYER_PULSES, current.map {
                        NativeCircle(it.latitude, it.longitude, radius = 18.0 + 30.0 * easeOut(t), color = LIVE_COLOR, opacity = 0.5 * (1f - t))
                    })
                }
            }
        } finally {
            native.setCircles(LAYER_PULSES, emptyList())
        }
    }
}

/**
 * Deploying a shared marker: it drops onto the spot, ripples while the server saves it, then pops when
 * it's live (or fades if it failed). The pin is a view on the map (animating a symbol would cross-fade
 * it every frame), the ripple a circle. Several deployments can run at once.
 */
@Composable
internal fun DeployAnimations(native: NativeMapView, art: IosMarkerArt, deploys: List<PinDeploy>, darkTheme: Boolean) {
    val current by rememberUpdatedState(deploys)
    // Rings of every running deployment, pushed together (they share one layer).
    val rings = remember { mutableMapOf<String, NativeCircle>() }
    for (deploy in deploys) androidx.compose.runtime.key(deploy.id) {
        LaunchedEffect(native, darkTheme) {
            val id = deploy.id
            val lat = deploy.latitude
            val lng = deploy.longitude
            val pin = art.marker(
                MapMarker("deploy", lat, lng, null, AnimalCategory.OTHER, MarkerKind.PIN, pinIcon = PinIcon.FLAG),
                selected = false, dark = darkTheme,
            )
            native.showDeployPin(id, lat, lng, pin.image)
            fun ring(radius: Double, fill: Double, stroke: Double) {
                rings[id] = NativeCircle(lat, lng, radius, DEPLOY_COLOR, fill, DEPLOY_COLOR, 2.0, stroke)
                native.setCircles(LAYER_DEPLOYS, rings.values.toList())
            }
            try {
                val start = withFrameMillis { it }
                var phaseStart = start
                var phase = PinDeploy.Phase.DEPLOYING
                while (isActive) {
                    val now = withFrameMillis { it }
                    val latest = current.firstOrNull { it.id == id }?.phase ?: break
                    if (latest != phase) {
                        phase = latest
                        phaseStart = now
                    }
                    val t = (now - start).toFloat()
                    val p = (now - phaseStart).toFloat()
                    // Drop with a small bounce, fading in.
                    val dropOffset = DEPLOY_DROP * (1f - bounceOut((t / DEPLOY_DROP_MS).coerceIn(0f, 1f)))
                    when (phase) {
                        PinDeploy.Phase.DEPLOYING -> {
                            // Waiting for the server: ripples from the base and a gentle bob.
                            val pulsing = t > DEPLOY_DROP_MS
                            val cycle = ((t - DEPLOY_DROP_MS).coerceAtLeast(0f) % DEPLOY_PULSE_MS) / DEPLOY_PULSE_MS
                            // Fades in as it grows from the base, then out: no solid dot at the start.
                            val fade = if (pulsing) (1f - cycle) * (cycle * 6f).coerceAtMost(1f) else 0f
                            ring(14.0 + 26.0 * easeOut(cycle), 0.12 * fade, 0.75 * fade)
                            val bob = if (pulsing) 3.0 * sin(cycle * 2 * PI) else 0.0
                            native.updateDeployPin(id, -dropOffset - bob, 1.0, (t / 150f).coerceIn(0f, 1f).toDouble())
                        }
                        PinDeploy.Phase.DEPLOYED -> {
                            // Live: one strong ripple and a pop.
                            val k = (p / DEPLOY_POP_MS).coerceIn(0f, 1f)
                            ring(14.0 + 46.0 * easeOut(k), 0.18 * (1f - k), 0.9 * (1f - k))
                            native.updateDeployPin(id, -dropOffset, 1.0 + 0.28 * sin(k * PI), 1.0)
                        }
                        PinDeploy.Phase.SETTLING -> {
                            // At rest, exactly over the real marker now drawn underneath.
                            ring(0.0, 0.0, 0.0)
                            native.updateDeployPin(id, 0.0, 1.0, 1.0)
                        }
                        PinDeploy.Phase.FAILED -> {
                            val k = (p / DEPLOY_FAIL_MS).coerceIn(0f, 1f)
                            ring(0.0, 0.0, 0.0)
                            native.updateDeployPin(id, 0.0, 1.0 - 0.3 * k, 1.0 - k.toDouble())
                        }
                    }
                }
            } finally {
                native.removeDeployPin(id)
                rings.remove(id)
                native.setCircles(LAYER_DEPLOYS, rings.values.toList())
            }
        }
    }
}
