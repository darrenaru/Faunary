package com.faunary.app.ui.map

import android.os.SystemClock
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.faunary.app.domain.AnimalCategory
import com.faunary.app.location.GeoPoint
import com.faunary.app.remote.Bounds
import com.faunary.app.ui.theme.FaunaryTheme
import com.mapbox.bindgen.Value
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.ScreenCoordinate
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.LayerPosition
import com.mapbox.maps.MapView
import com.mapbox.maps.toCameraOptions
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.easeTo
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.OnPointAnnotationClickListener
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotation
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createCircleAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.attribution.attribution
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.plugin.gestures.gestures
import com.faunary.app.location.DeviceOrientation
import com.mapbox.maps.ImageHolder
import com.mapbox.maps.plugin.LocationPuck2D
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.logo.logo
import com.mapbox.maps.plugin.scalebar.scalebar
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * Map layers, drawn bottom to top in declaration order. Live explorers sit at the bottom:
 * people usually stand where they just took a photo, and the animal photo must stay visible and tappable.
 */
enum class MarkerKind { LIVE, OWN, COMMUNITY }

data class MapMarker(
    /** Unique across layers, e.g. "own:12", "com:<uuid>", "live:<uuid>". */
    val key: String,
    val latitude: Double,
    val longitude: Double,
    /** Local file path or https URL; null draws the category icon instead. */
    val photo: String?,
    val category: AnimalCategory,
    val kind: MarkerKind = MarkerKind.OWN,
    /** Name shown under live-user markers. */
    val label: String? = null,
    /** When the photo was taken; the newest photo sits on top of a stack. */
    val time: Long = 0L,
)

/** Default camera when there is no data or GPS yet (Taman Suropati, Jakarta — as in the design). */
val DefaultCenter = GeoPoint(-6.1990, 106.8322)

/** Live explorers "beat" once per period: the avatar pops and an olive ring radiates out. */
private const val HEARTBEAT_PERIOD_MS = 3_000L
private const val HEARTBEAT_RING_MS = 1_200L
private const val HEARTBEAT_POP_MS = 320L
/** Duration of the glide from a live explorer's previous position to the new one. */
private const val LIVE_GLIDE_MS = 1_200L

/** Layer id of the Mapbox location puck (see LocationComponentConstants). */
private const val PUCK_LAYER = "mapbox-location-indicator-layer"

/** Camera tilt used in 3D mode. */
const val Pitch3D = 58.0

/** Zoom at/below which the globe is shown head-on, and from which full 3D tilt is allowed again. */
private const val GLOBE_FLAT_ZOOM = 2.0
private const val GLOBE_TILT_ZOOM = 5.0

/** Camera tilt while following the user during navigation (when the phone's tilt isn't used). */
const val NavigationPitch = 60.0

data class NavCamera(val latitude: Double, val longitude: Double, val zoom: Double, val bearing: Double, val pitch: Double)

/** Imperative handle for camera moves triggered from Compose (zoom buttons, "my location", etc.). */
class FaunaMapController {
    internal var mapView: MapView? = null
    internal var threeD = false

    fun zoomBy(delta: Double) {
        val map = mapView?.mapboxMap ?: return
        map.easeTo(
            CameraOptions.Builder().zoom(map.cameraState.zoom + delta).build(),
            MapAnimationOptions.mapAnimationOptions { duration(250) },
        )
    }

    fun flyTo(lat: Double, lng: Double, zoom: Double? = null, bottomPaddingPx: Double = 0.0, durationMs: Long = 800) {
        val map = mapView?.mapboxMap ?: return
        map.flyTo(
            CameraOptions.Builder()
                .center(Point.fromLngLat(lng, lat))
                .zoom(zoom ?: max(map.cameraState.zoom, 15.0))
                .pitch(if (threeD) Pitch3D else 0.0)
                .padding(EdgeInsets(0.0, 0.0, bottomPaddingPx, 0.0))
                .build(),
            MapAnimationOptions.mapAnimationOptions { duration(durationMs) },
        )
    }

    fun zoom(): Double? = mapView?.mapboxMap?.cameraState?.zoom

    /** Fits all [points] on screen (a single spot just zooms in close). */
    fun fit(points: List<Pair<Double, Double>>, bottomPaddingPx: Double = 0.0) {
        val map = mapView?.mapboxMap ?: return
        val pts = points.map { (lat, lng) -> Point.fromLngLat(lng, lat) }.distinct()
        if (pts.isEmpty()) return
        if (pts.size == 1) {
            flyTo(pts[0].latitude(), pts[0].longitude(), 18.5, bottomPaddingPx)
            return
        }
        val side = 72.0 * (mapView?.resources?.displayMetrics?.density ?: 3f)
        val camera = map.cameraForCoordinates(
            pts, CameraOptions.Builder().pitch(if (threeD) Pitch3D else 0.0).build(),
            EdgeInsets(side * 2.5, side, bottomPaddingPx + side, side), 19.0, null,
        )
        map.flyTo(camera, MapAnimationOptions.mapAnimationOptions { duration(800) })
    }

    /** Current navigation-relevant camera: (center, zoom, bearing, pitch). */
    fun navigationCamera(): NavCamera? = mapView?.mapboxMap?.cameraState?.let {
        NavCamera(it.center.latitude(), it.center.longitude(), it.zoom, it.bearing, it.pitch)
    }

    /**
     * Navigation camera, applied immediately (called every frame): centred on the user, rotated and
     * tilted as given, with the user low on screen.
     */
    fun setNavigationCamera(cam: NavCamera, topPaddingPx: Double) {
        val map = mapView?.mapboxMap ?: return
        map.setCamera(
            CameraOptions.Builder()
                .center(Point.fromLngLat(cam.longitude, cam.latitude))
                .zoom(cam.zoom)
                .bearing(cam.bearing)
                .pitch(cam.pitch)
                .padding(EdgeInsets(topPaddingPx, 0.0, 0.0, 0.0))
                .build(),
        )
    }

    fun center(): GeoPoint? = mapView?.mapboxMap?.cameraState?.center?.let { GeoPoint(it.latitude(), it.longitude()) }
}

@Composable
fun rememberFaunaMapController() = remember { FaunaMapController() }

@OptIn(FlowPreview::class) // debounce() for regrouping stacks once the zoom settles
@Composable
fun FaunaMap(
    markers: List<MapMarker>,
    modifier: Modifier = Modifier,
    controller: FaunaMapController = rememberFaunaMapController(),
    selectedKey: String? = null,
    onMarkerClick: (String) -> Unit = {},
    onMapClick: () -> Unit = {},
    initialCenter: GeoPoint = DefaultCenter,
    initialZoom: Double = 14.0,
    showUserLocation: Boolean = false,
    ornamentBottomPadding: Dp = 0.dp,
    darkTheme: Boolean = isSystemInDarkTheme(),
    threeD: Boolean = false,
    onCameraSnapshot: ((GeoPoint, Double) -> Unit)? = null,
    onCameraIdle: ((Bounds) -> Unit)? = null,
    /** A stack of more than [SPIDER_MAX] photos was tapped: member keys (newest first) and its spot. */
    onStackClick: ((List<String>, Double, Double) -> Unit)? = null,
    /** In-app route to draw, as (lat, lng) points; the camera fits it once when it changes. */
    route: List<Pair<Double, Double>>? = null,
    routeTopPadding: Dp = 0.dp,
    routeBottomPadding: Dp = 0.dp,
    /** False while navigating: the route line is updated in place and the camera is left to [FaunaMapController.follow]. */
    fitRoute: Boolean = true,
    /** Live phone orientation; the location arrow points where the phone points. */
    heading: StateFlow<DeviceOrientation?>? = null,
    /** The user started dragging the map (used to pause the navigation camera). */
    onUserPan: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = FaunaryTheme.colors
    val currentOnMarkerClick by rememberUpdatedState(onMarkerClick)
    val currentOnMapClick by rememberUpdatedState(onMapClick)
    val currentOnCameraIdle by rememberUpdatedState(onCameraIdle)
    val currentOnStackClick by rememberUpdatedState(onStackClick)
    val currentOnUserPan by rememberUpdatedState(onUserPan)

    val mapView = remember {
        MapView(
            context,
            MapInitOptions(
                context,
                cameraOptions = CameraOptions.Builder()
                    .center(Point.fromLngLat(initialCenter.longitude, initialCenter.latitude))
                    .zoom(initialZoom)
                    .pitch(if (threeD && initialZoom >= GLOBE_TILT_ZOOM) Pitch3D else 0.0)
                    .build(),
            ),
        ).apply {
            scalebar.enabled = false
            compass.enabled = false
        }
    }
    val markerFactory = remember { MarkerFactory(context, density.density) }
    val locationProvider = remember { HeadingLocationProvider(context) }
    val arrow = remember { ImageHolder.from(locationArrowBitmap(density.density)) }
    var managers by remember { mutableStateOf<Map<MarkerKind, PointAnnotationManager>>(emptyMap()) }
    var pulseManager by remember { mutableStateOf<CircleAnnotationManager?>(null) }
    var routeManager by remember { mutableStateOf<PolylineAnnotationManager?>(null) }
    val annotationToKey = remember { mutableMapOf<String, String>() }
    val annotationToStack = remember { mutableMapOf<String, PhotoPin>() }
    var lastAnnotationClick by remember { mutableStateOf(0L) }
    var legManager by remember { mutableStateOf<PolylineAnnotationManager?>(null) }
    // Stacking depends on zoom; regrouped whenever the map settles at a new quarter-zoom level.
    var stackZoom by remember { mutableStateOf(floor(initialZoom * 4) / 4) }
    var spider by remember { mutableStateOf<Spider?>(null) }
    val liveZoom = remember { MutableStateFlow(initialZoom) }
    LaunchedEffect(Unit) {
        // Regroup once the zoom settles (not on every animation frame), in quarter-zoom steps.
        liveZoom.debounce(200).collect { z ->
            val bucket = floor(z * 4) / 4
            if (bucket != stackZoom) {
                stackZoom = bucket
                spider = null // the fan-out was laid out for the old zoom
            }
        }
    }

    fun openStack(pin: PhotoPin) {
        if (pin.members.size > SPIDER_MAX) {
            spider = null
            currentOnStackClick?.invoke(pin.members.map { it.key }, pin.top.latitude, pin.top.longitude)
            return
        }
        // Fan out around the spot, in screen space, converted back to map coordinates.
        val map = mapView.mapboxMap
        val center = Point.fromLngLat(pin.top.longitude, pin.top.latitude)
        val c = map.pixelForCoordinate(center)
        val positions = spiderOffsets(pin.members.size).map { (dx, dy) ->
            map.coordinateForPixel(ScreenCoordinate(c.x + dx * density.density, c.y + dy * density.density))
        }
        currentOnMapClick() // closes any open card or list
        spider = Spider(pin.key, center, pin.members, positions)
    }

    DisposableEffect(mapView) {
        controller.mapView = mapView
        mapView.location.setLocationProvider(locationProvider)
        mapView.mapboxMap.addOnMapClickListener {
            // The annotation plugin also sees this tap; ignore it if a marker was just hit.
            if (SystemClock.uptimeMillis() - lastAnnotationClick > 300) {
                spider = null
                currentOnMapClick()
            }
            false
        }
        mapView.gestures.addOnMoveListener(object : OnMoveListener {
            override fun onMoveBegin(detector: MoveGestureDetector) { currentOnUserPan?.invoke() }
            override fun onMove(detector: MoveGestureDetector) = false
            override fun onMoveEnd(detector: MoveGestureDetector) {}
        })
        // Map "idle" can stay away for long (the pulsing location puck keeps rendering), so stacking
        // follows the raw zoom, settled below.
        val cameraSub = mapView.mapboxMap.subscribeCameraChanged { liveZoom.value = mapView.mapboxMap.cameraState.zoom }
        val idle = mapView.mapboxMap.subscribeMapIdle {
            val map = mapView.mapboxMap
            val b = map.coordinateBoundsForCamera(map.cameraState.toCameraOptions())
            currentOnCameraIdle?.invoke(Bounds(b.south(), b.west(), b.north(), b.east()))
        }
        onDispose {
            idle.cancel()
            cameraSub.cancel()
            mapView.mapboxMap.cameraState.let { cam ->
                onCameraSnapshot?.invoke(GeoPoint(cam.center.latitude(), cam.center.longitude()), cam.zoom)
            }
            controller.mapView = null
            mapView.onDestroy()
        }
    }

    LaunchedEffect(darkTheme, threeD) {
        mapView.mapboxMap.loadStyle(MapStyle.json(darkTheme, threeD)) {
            if (managers.isEmpty()) {
                // Created first so the heartbeat rings render underneath every marker.
                pulseManager = mapView.annotations.createCircleAnnotationManager()
                // Route line sits above the rings but below every marker.
                routeManager = mapView.annotations.createPolylineAnnotationManager()
                // Legs of a fanned-out stack, under the pins.
                legManager = mapView.annotations.createPolylineAnnotationManager()
                // Nearby photos are merged into stacks by groupPins() instead of Mapbox clustering,
                // so they never pile up at high zoom and own + community finds stack together.
                managers = MarkerKind.entries.associateWith {
                    mapView.annotations.createPointAnnotationManager().apply {
                        addClickListener(OnPointAnnotationClickListener { annotation ->
                            lastAnnotationClick = SystemClock.uptimeMillis()
                            val stack = annotationToStack[annotation.id]
                            if (stack != null) openStack(stack)
                            else annotationToKey[annotation.id]?.let(currentOnMarkerClick)
                            true
                        })
                    }
                }
            }
        }
    }

    // Tilt the camera in 3D; flatten and face north again in 2D. The first run has no animation
    // because the initial camera options already carry the right pitch.
    var firstTilt by remember { mutableStateOf(true) }
    LaunchedEffect(threeD) {
        controller.threeD = threeD
        mapView.gestures.pitchEnabled = threeD
        mapView.gestures.rotateEnabled = threeD
        mapView.compass.updateSettings {
            enabled = threeD
            marginTop = with(density) { 180.dp.toPx() }
        }
        if (firstTilt) {
            firstTilt = false
            return@LaunchedEffect
        }
        // Navigation drives the camera every frame itself; an ease here would fight it.
        if (!fitRoute && threeD) return@LaunchedEffect
        val map = mapView.mapboxMap
        val camera = if (threeD) {
            // Buildings only extrude from z14, so nudge the camera in if it's too far out.
            CameraOptions.Builder().pitch(Pitch3D).zoom(max(map.cameraState.zoom, 15.5)).build()
        } else {
            CameraOptions.Builder().pitch(0.0).bearing(0.0).build()
        }
        map.easeTo(camera, MapAnimationOptions.mapAnimationOptions { duration(700) })
    }

    LaunchedEffect(showUserLocation) {
        mapView.location.updateSettings {
            enabled = showUserLocation
            // Arrow instead of a dot; it is the bearing image, so it rotates with the heading.
            locationPuck = LocationPuck2D(bearingImage = arrow)
            puckBearingEnabled = true
            pulsingEnabled = true
            pulsingColor = 0xFFDF6D41.toInt()
            // Soft accuracy halo makes "you are here" readable even next to photo markers.
            showAccuracyRing = true
            accuracyRingColor = 0x334A90E2.toInt()
            accuracyRingBorderColor = 0x804A90E2.toInt()
        }
    }

    // "You are here" must never hide under animal photos: annotation layers are added after the
    // location layer (and re-added on style reloads), so keep moving the puck to the very top.
    LaunchedEffect(showUserLocation, managers, routeManager, pulseManager, markers, darkTheme, threeD) {
        if (!showUserLocation) return@LaunchedEffect
        repeat(4) {
            val style = mapView.mapboxMap.style
            if (style != null && style.styleLayerExists(PUCK_LAYER)) {
                val top = style.styleLayers.lastOrNull()?.id
                if (top != PUCK_LAYER) style.moveStyleLayer(PUCK_LAYER, LayerPosition(null, null, null))
            }
            delay(500) // the puck layer can be (re)created shortly after the style/annotations
        }
    }

    // Turn the arrow with the phone, lightly smoothed so sensor jitter doesn't make it shiver.
    LaunchedEffect(heading) {
        var smoothed: Double? = null
        heading?.filterNotNull()?.collect { o ->
            val prev = smoothed
            val next = if (prev == null) o.heading else (prev + (((o.heading - prev + 540.0) % 360.0) - 180.0) * 0.35 + 360.0) % 360.0
            smoothed = next
            locationProvider.updateHeading(next)
        }
    }

    // Globe view: far out, a tilted camera pushes the globe off the bottom of the screen, so the
    // allowed tilt shrinks with zoom (flat at zoom ≤ 2, full 3D tilt again from zoom 5).
    // Navigation sets its own camera every frame and is left alone.
    LaunchedEffect(fitRoute) {
        if (!fitRoute) return@LaunchedEffect
        val map = mapView.mapboxMap
        val sub = map.subscribeCameraChanged {
            val cam = map.cameraState
            val maxPitch = Pitch3D * ((cam.zoom - GLOBE_FLAT_ZOOM) / (GLOBE_TILT_ZOOM - GLOBE_FLAT_ZOOM)).coerceIn(0.0, 1.0)
            if (cam.pitch > maxPitch + 0.5) map.setCamera(CameraOptions.Builder().pitch(maxPitch).build())
        }
        try {
            awaitCancellation()
        } finally {
            sub.cancel()
        }
    }

    LaunchedEffect(ornamentBottomPadding) {
        val px = with(density) { ornamentBottomPadding.toPx() }
        mapView.logo.updateSettings { marginBottom = px + 8f }
        mapView.attribution.updateSettings { marginBottom = px + 8f }
    }

    // Photo pins: grouped into stacks for the current zoom; an open fan-out replaces its stack
    // with the individual pins. One effect per layer so a live-position tick doesn't redraw them.
    // Live explorers are handled separately below so they can glide instead of being recreated.
    val byKind = markers.groupBy { it.kind }
    val photoMarkers = markers.filter { it.kind != MarkerKind.LIVE }
    val pins = remember(photoMarkers, stackZoom) { groupPins(photoMarkers, stackZoom) }
    val activeSpider = spider?.takeIf { s -> pins.any { it.key == s.pinKey } }
    val draws = remember(pins, activeSpider, selectedKey) {
        buildList {
            for (pin in pins) {
                if (pin.key == activeSpider?.pinKey) {
                    activeSpider.members.forEachIndexed { i, m ->
                        add(PinDraw(m, activeSpider.positions[i], 1, m.key == selectedKey, null))
                    }
                } else {
                    val selected = pin.members.any { it.key == selectedKey }
                    add(PinDraw(pin.top, Point.fromLngLat(pin.top.longitude, pin.top.latitude), pin.members.size, selected, pin.takeIf { it.isStack }))
                }
            }
        }.groupBy { it.marker.kind }
    }
    MarkerKind.entries.filter { it != MarkerKind.LIVE }.forEach { kind ->
        val layer = draws[kind].orEmpty()
        key(kind) {
            LaunchedEffect(managers, layer, colors.isDark) {
                val m = managers[kind] ?: return@LaunchedEffect
                val options = withContext(Dispatchers.IO) {
                    layer.map { d ->
                        PointAnnotationOptions()
                            .withPoint(d.point)
                            .withIconImage(markerFactory.marker(d.marker, d.selected, colors.isDark, d.count))
                            // Photo pins stand on their spot: the tail tip is the bitmap's bottom centre.
                            .withIconAnchor(IconAnchor.BOTTOM)
                            .withSymbolSortKey(if (d.selected) 10.0 else if (d.stack != null) 5.0 else 0.0)
                    }
                }
                m.annotations.forEach {
                    annotationToKey.remove(it.id)
                    annotationToStack.remove(it.id)
                }
                m.deleteAll()
                val created = m.create(options)
                created.forEachIndexed { i, annotation ->
                    val d = layer[i]
                    if (d.stack != null) annotationToStack[annotation.id] = d.stack
                    else annotationToKey[annotation.id] = d.marker.key
                }
            }
        }
    }

    // Legs from the spot to each fanned-out pin.
    LaunchedEffect(legManager, activeSpider, colors.isDark) {
        val m = legManager ?: return@LaunchedEffect
        m.deleteAll()
        val s = activeSpider ?: return@LaunchedEffect
        m.create(s.positions.map { p ->
            PolylineAnnotationOptions().withPoints(listOf(s.center, p))
                .withLineColor(if (colors.isDark) "#F5EDE0" else "#7A4E28")
                .withLineOpacity(0.55).withLineWidth(1.6)
        })
    }

    // Live explorers: keep one annotation per person and animate it to each new position.
    val liveAnnotations = remember { mutableMapOf<String, PointAnnotation>() }
    val liveMarkers = byKind[MarkerKind.LIVE].orEmpty()
    val liveSelected = selectedKey?.takeIf { k -> liveMarkers.any { it.key == k } }
    LaunchedEffect(managers, liveMarkers, liveSelected, colors.isDark) {
        val m = managers[MarkerKind.LIVE] ?: return@LaunchedEffect
        val wanted = liveMarkers.associateBy { it.key }
        // People who left
        (liveAnnotations.keys - wanted.keys).forEach { k ->
            liveAnnotations.remove(k)?.let { m.delete(it); annotationToKey.remove(it.id) }
        }
        val glides = mutableListOf<Triple<PointAnnotation, Point, Point>>()
        for (marker in liveMarkers) {
            val target = Point.fromLngLat(marker.longitude, marker.latitude)
            val selected = marker.key == liveSelected
            val icon = withContext(Dispatchers.IO) { markerFactory.marker(marker, selected, colors.isDark) }
            val existing = liveAnnotations[marker.key]
            if (existing == null) {
                val created = m.create(
                    PointAnnotationOptions().withPoint(target).withIconImage(icon)
                        .withSymbolSortKey(if (selected) 10.0 else 0.0),
                )
                liveAnnotations[marker.key] = created
                annotationToKey[created.id] = marker.key
            } else {
                existing.iconImageBitmap = icon
                existing.symbolSortKey = if (selected) 10.0 else 0.0
                val from = existing.point
                if (from.latitude() != target.latitude() || from.longitude() != target.longitude()) {
                    glides += Triple(existing, from, target)
                } else {
                    m.update(existing)
                }
            }
        }
        if (glides.isEmpty()) return@LaunchedEffect
        // Ease each moved marker from where it is to its new position. A newer update cancels this
        // effect mid-way; the next run starts from the marker's current (partly moved) position.
        val start = withFrameMillis { it }
        var t = 0f
        while (t < 1f) {
            t = ((withFrameMillis { it } - start).toFloat() / LIVE_GLIDE_MS).coerceIn(0f, 1f)
            val e = if (t < 0.5f) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2 // ease-in-out cubic
            glides.forEach { (a, from, to) ->
                a.point = Point.fromLngLat(
                    from.longitude() + (to.longitude() - from.longitude()) * e,
                    from.latitude() + (to.latitude() - from.latitude()) * e,
                )
            }
            m.update(glides.map { it.first })
        }
    }
    // Style reloads (dark mode, 3D) recreate managers; drop stale handles so markers are recreated.
    LaunchedEffect(managers) { liveAnnotations.clear() }

    // In-app route: soft light casing + Canyon line, then fit the whole route on screen.
    // While navigating the line shrinks every fix, so the existing lines are moved instead of recreated.
    val routeLines = remember { mutableListOf<PolylineAnnotation>() }
    var routeLinesDark by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(routeManager, route, colors.isDark) {
        val m = routeManager ?: return@LaunchedEffect
        val pts = route?.map { (lat, lng) -> Point.fromLngLat(lng, lat) }?.takeIf { it.size >= 2 }
        if (pts == null) {
            m.deleteAll()
            routeLines.clear()
            return@LaunchedEffect
        }
        if (!fitRoute && routeLines.size == 2 && routeLinesDark == colors.isDark && m.annotations.containsAll(routeLines)) {
            routeLines.forEach { it.points = pts }
            m.update(routeLines)
            return@LaunchedEffect
        }
        m.deleteAll()
        routeLines.clear()
        routeLinesDark = colors.isDark
        routeLines += m.create(
            listOf(
                PolylineAnnotationOptions().withPoints(pts).withLineColor(if (colors.isDark) "#29231F" else "#FBF8F1")
                    .withLineWidth(9.0).withLineJoin(LineJoin.ROUND),
                PolylineAnnotationOptions().withPoints(pts).withLineColor("#DF6D41")
                    .withLineWidth(5.0).withLineJoin(LineJoin.ROUND),
            ),
        )
        if (!fitRoute) return@LaunchedEffect
        val map = mapView.mapboxMap
        val side = with(density) { 48.dp.toPx().toDouble() }
        val padding = EdgeInsets(
            with(density) { routeTopPadding.toPx().toDouble() }, side,
            with(density) { routeBottomPadding.toPx().toDouble() }, side,
        )
        val camera = map.cameraForCoordinates(pts, CameraOptions.Builder().pitch(if (threeD) 45.0 else 0.0).bearing(0.0).build(), padding, 17.0, null)
        map.easeTo(camera, MapAnimationOptions.mapAnimationOptions { duration(900) })
    }

    // Heartbeat for live explorers, every HEARTBEAT_PERIOD_MS. Rings follow the avatars every frame,
    // so they keep pulsing around someone while they glide; restarts only when people join/leave.
    val liveKeys = liveMarkers.map { it.key }.toSet()
    LaunchedEffect(pulseManager, managers, liveKeys) {
        val rings = pulseManager ?: return@LaunchedEffect
        val liveLayer = managers[MarkerKind.LIVE] ?: return@LaunchedEffect
        rings.deleteAll()
        if (liveKeys.isEmpty()) return@LaunchedEffect
        // Wait until the glide effect has created this round's annotations.
        while (isActive && !liveKeys.all { it in liveAnnotations }) withFrameMillis { }
        val order = liveKeys.toList()
        val circles = rings.create(order.map { k ->
            CircleAnnotationOptions()
                .withPoint(liveAnnotations.getValue(k).point)
                .withCircleColor("#AAA648")
                .withCircleRadius(0.0)
                .withCircleOpacity(0.0)
        })
        fun followAvatars() = order.forEachIndexed { i, k -> liveAnnotations[k]?.let { circles[i].point = it.point } }
        try {
            while (isActive) {
                val start = withFrameMillis { it }
                var elapsed = 0L
                while (elapsed < HEARTBEAT_PERIOD_MS) {
                    elapsed = withFrameMillis { it } - start
                    val t = (elapsed.toFloat() / HEARTBEAT_RING_MS).coerceIn(0f, 1f)
                    val eased = 1f - (1f - t).pow(3)
                    circles.forEach {
                        it.circleRadius = 18.0 + 30.0 * eased
                        it.circleOpacity = 0.5 * (1f - t)
                    }
                    followAvatars()
                    rings.update(circles)
                    if (elapsed <= HEARTBEAT_POP_MS + 50) {
                        // Avatar "pop": quick swell and settle at the start of each beat.
                        val pop = (elapsed.toFloat() / HEARTBEAT_POP_MS).coerceIn(0f, 1f)
                        val scale = 1.0 + 0.14 * sin(pop * Math.PI)
                        val avatars = liveLayer.annotations
                        avatars.forEach { it.iconSize = scale }
                        liveLayer.update(avatars)
                    }
                }
            }
        } finally {
            circles.forEach { it.circleOpacity = 0.0 }
            runCatching { rings.update(circles) }
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/** One photo annotation to draw: a single find, a stack's top photo ([stack] set) or a fanned-out member. */
private data class PinDraw(
    val marker: MapMarker,
    val point: Point,
    val count: Int,
    val selected: Boolean,
    val stack: PhotoPin?,
)
