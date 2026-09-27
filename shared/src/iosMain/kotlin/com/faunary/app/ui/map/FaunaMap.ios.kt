package com.faunary.app.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.UIKitView
import com.faunary.app.location.DeviceOrientation
import com.faunary.app.location.GeoPoint
import com.faunary.app.remote.Bounds
import com.faunary.app.ui.theme.FaunaryTheme
import kotlinx.coroutines.flow.StateFlow
import platform.UIKit.UIScreen
import kotlin.math.floor

/** Tilt of the 3D map, as on Android. */
private const val PITCH_3D = 58.0

/** Camera handle on iOS, backed by the Swift map view. Paddings arrive in px and go out in points. */
actual class FaunaMapController actual constructor() {
    internal var native: NativeMapView? = null
    internal var threeD = false
    private val screenScale get() = UIScreen.mainScreen.scale

    actual fun zoomBy(delta: Double) {
        val map = native ?: return
        val (lat, lng, zoom, bearing, pitch) = map.cameraState()
        map.setCamera(lat, lng, zoom + delta, bearing, pitch, 0.0, 0.0, 250.0)
    }

    actual fun flyTo(lat: Double, lng: Double, zoom: Double?, bottomPaddingPx: Double, durationMs: Long) {
        val map = native ?: return
        val state = map.cameraState()
        map.setCamera(
            lat, lng, zoom ?: state[2], state[3], if (threeD) PITCH_3D else 0.0,
            0.0, bottomPaddingPx / screenScale, durationMs.toDouble(),
        )
    }

    actual fun zoom(): Double? = native?.cameraState()?.get(2)

    actual fun fit(points: List<Pair<Double, Double>>, bottomPaddingPx: Double) {
        val map = native ?: return
        val pts = points.distinct()
        if (pts.isEmpty()) return
        if (pts.size == 1) {
            flyTo(pts[0].first, pts[0].second, 18.5, bottomPaddingPx)
            return
        }
        // Same margins as Android: roomy at the top for the header, the card height at the bottom.
        val side = 72.0
        map.fitCoordinates(
            pts.flatMap { listOf(it.first, it.second) },
            top = side * 2.5, left = side, bottom = bottomPaddingPx / screenScale + side, right = side,
            pitch = if (threeD) PITCH_3D else 0.0, maxZoom = 19.0,
        )
    }

    actual fun navigationCamera(): NavCamera? = native?.cameraState()?.let { NavCamera(it[0], it[1], it[2], it[3], it[4]) }

    actual fun setNavigationCamera(cam: NavCamera, topPaddingPx: Double) {
        native?.setCamera(cam.latitude, cam.longitude, cam.zoom, cam.bearing, cam.pitch, topPaddingPx / screenScale, 0.0, 0.0)
    }

    actual fun center(): GeoPoint? = native?.cameraState()?.let { GeoPoint(it[0], it[1]) }
}

/**
 * The Faunary map on iOS: Mapbox through the Swift bridge ([NativeMaps]), with the same style JSON,
 * markers and grouping as Android. Not yet on iOS: the route draw-in and heartbeat animations,
 * fanning out small stacks (every stack opens its list) and deploy animations.
 */
@Composable
actual fun FaunaMap(
    markers: List<MapMarker>,
    modifier: Modifier,
    controller: FaunaMapController,
    selectedKey: String?,
    onMarkerClick: (String) -> Unit,
    onMapClick: () -> Unit,
    initialCenter: GeoPoint,
    initialZoom: Double,
    showUserLocation: Boolean,
    ornamentBottomPadding: Dp,
    darkTheme: Boolean,
    threeD: Boolean,
    onCameraSnapshot: ((GeoPoint, Double) -> Unit)?,
    onCameraIdle: ((Bounds) -> Unit)?,
    onStackClick: ((List<String>, Double, Double) -> Unit)?,
    route: List<Pair<Double, Double>>?,
    routeTopPadding: Dp,
    routeBottomPadding: Dp,
    fitRoute: Boolean,
    routeDrawnAt: Long,
    sharedRoutes: List<SharedRouteLine>,
    heading: StateFlow<DeviceOrientation?>?,
    onUserPan: (() -> Unit)?,
    onMapLongClick: ((Double, Double) -> Unit)?,
    deploys: List<PinDeploy>,
) {
    val factory = NativeMaps.factory
    if (factory == null) {
        // The Swift side didn't register a map (e.g. a build without Mapbox): keep the screen usable.
        Box(modifier.background(FaunaryTheme.colors.surfaceMuted))
        return
    }
    val native = remember(factory) { factory.createMapView() }
    val art = remember { IosMarkerArt() }
    controller.native = native
    controller.threeD = threeD

    var zoom by remember { mutableDoubleStateOf(initialZoom) }
    // Stacks are regrouped per half zoom level, not on every camera frame.
    val zoomStep = floor(zoom * 2) / 2
    val groups = remember(markers, zoomStep) {
        val photos = markers.filter { it.kind == MarkerKind.OWN || it.kind == MarkerKind.COMMUNITY }
        groupPins(photos, zoomStep)
    }

    val currentMarkerClick by rememberUpdatedState(onMarkerClick)
    val currentMapClick by rememberUpdatedState(onMapClick)
    val currentLongClick by rememberUpdatedState(onMapLongClick)
    val currentSnapshot by rememberUpdatedState(onCameraSnapshot)
    val currentIdle by rememberUpdatedState(onCameraIdle)
    val currentStackClick by rememberUpdatedState(onStackClick)
    val currentUserPan by rememberUpdatedState(onUserPan)
    val currentGroups by rememberUpdatedState(groups)

    DisposableEffect(native) {
        native.listener = object : NativeMapListener {
            override fun onMarkerTap(key: String) {
                val stack = currentGroups.firstOrNull { it.isStack && it.key == key }
                if (stack != null) {
                    val click = currentStackClick
                    if (click != null) click(stack.members.map { it.key }, stack.top.latitude, stack.top.longitude)
                    else currentMarkerClick(stack.top.key)
                } else {
                    currentMarkerClick(key)
                }
            }

            override fun onMapTap() = currentMapClick()

            override fun onMapLongPress(latitude: Double, longitude: Double) {
                currentLongClick?.invoke(latitude, longitude)
            }

            override fun onCameraChanged(latitude: Double, longitude: Double, zoom: Double) {
                currentSnapshot?.invoke(GeoPoint(latitude, longitude), zoom)
            }

            override fun onCameraIdle(south: Double, west: Double, north: Double, east: Double) {
                zoom = native.cameraState()[2]
                currentIdle?.invoke(Bounds(south, west, north, east))
            }

            override fun onUserPan() {
                currentUserPan?.invoke()
            }
        }
        native.setCamera(initialCenter.latitude, initialCenter.longitude, initialZoom, 0.0, if (threeD) PITCH_3D else 0.0, 0.0, 0.0, 0.0)
        onDispose {
            native.listener = null
            if (controller.native === native) controller.native = null
        }
    }

    LaunchedEffect(native, darkTheme, threeD) { native.setStyleJson(MapStyle.json(darkTheme, threeD)) }
    LaunchedEffect(native, showUserLocation) { native.setUserLocationVisible(showUserLocation) }
    LaunchedEffect(native, ornamentBottomPadding) { native.setOrnamentBottomMargin(ornamentBottomPadding.value.toDouble()) }

    LaunchedEffect(native, groups, markers, selectedKey, darkTheme) {
        val others = markers.filter { it.kind != MarkerKind.OWN && it.kind != MarkerKind.COMMUNITY }
        val list = buildList {
            for (m in others) {
                val selected = m.key == selectedKey
                val img = art.marker(m, selected, darkTheme)
                add(NativeMarker(m.key, m.latitude, m.longitude, img.image, img.id, anchorBottom = m.kind != MarkerKind.LIVE, sortKey = layer(m.kind, selected)))
            }
            for (pin in groups) {
                val selected = pin.members.any { it.key == selectedKey }
                val img = art.marker(pin.top, selected, darkTheme, pin.members.size)
                add(NativeMarker(pin.key, pin.top.latitude, pin.top.longitude, img.image, img.id, anchorBottom = true, sortKey = layer(pin.top.kind, selected)))
            }
        }
        native.setMarkers(list)
    }

    val routeColor = "#DF6D41"
    val routeBottom = routeBottomPadding
    LaunchedEffect(native, route) {
        native.setRoute(route.orEmpty().flatMap { listOf(it.first, it.second) }, routeColor)
        if (route != null && fitRoute) controller.fit(route, routeBottom.value * UIScreen.mainScreen.scale)
    }
    LaunchedEffect(native, sharedRoutes) {
        native.setSharedRoutes(sharedRoutes.map { line -> line.points.flatMap { listOf(it.first, it.second) } }, "#7395BF")
    }

    UIKitView(factory = { native.view }, modifier = modifier)
}

/** Draw order, bottom to top, as on Android: live explorers, shared markers, photos, the searched place; selection on top. */
private fun layer(kind: MarkerKind, selected: Boolean): Double = kind.ordinal.toDouble() + if (selected) 10.0 else 0.0

private operator fun List<Double>.component5() = this[4]
