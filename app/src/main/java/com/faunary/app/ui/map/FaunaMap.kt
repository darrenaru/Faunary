package com.faunary.app.ui.map

import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.viewannotation.annotationAnchor
import com.mapbox.maps.ViewAnnotationAnchor
import android.widget.ImageView
import android.graphics.drawable.BitmapDrawable
import android.widget.FrameLayout
import android.view.Gravity
import android.os.SystemClock
import android.view.animation.DecelerateInterpolator
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
import com.faunary.app.util.Geo
import com.faunary.app.location.GeoPoint
import com.faunary.app.remote.Bounds
import com.faunary.app.ui.theme.FaunaryTheme
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.ConstrainMode
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
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotation
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
import com.mapbox.maps.plugin.gestures.addOnMapLongClickListener
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
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Map layers, drawn bottom to top in declaration order. Live explorers sit at the bottom:
 * people usually stand where they just took a photo, and the animal photo must stay visible and tappable.
 * Shared markers (PIN) are drawn one by one; only photos are merged into stacks.
 */
enum class MarkerKind { LIVE, PIN, OWN, COMMUNITY }

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
    /** Icon of a shared marker ([MarkerKind.PIN]). */
    val pinIcon: PinIcon? = null,
)

/**
 * Someone else's route shown on the map: the line still ahead of them and where it ends. [startedAt]
 * (their clock, ms) identifies the route; one that has just started is drawn in with an animation.
 */
data class SharedRouteLine(
    val key: String,
    val points: List<Pair<Double, Double>>,
    val destLat: Double,
    val destLng: Double,
    val startedAt: Long,
)

/** Default camera when there is no data or GPS yet (Taman Suropati, Jakarta — as in the design). */
val DefaultCenter = GeoPoint(-6.1990, 106.8322)

/** Live explorers "beat" once per period: the avatar pops and an olive ring radiates out. */
private const val HEARTBEAT_PERIOD_MS = 3_000L
private const val HEARTBEAT_RING_MS = 1_200L
private const val HEARTBEAT_POP_MS = 320L
/** Duration of the glide from a live explorer's previous position to the new one. */
private const val LIVE_GLIDE_MS = 1_200L

/** Marker deployment: drop height (icon px), drop and ripple timings, ripple colour (the flag icon's). */
private const val DEPLOY_DROP = 60.0
private const val DEPLOY_DROP_MS = 450f
private const val DEPLOY_PULSE_MS = 1_000f
private const val DEPLOY_POP_MS = 450f
private const val DEPLOY_FAIL_MS = 400f
private const val DEPLOY_COLOR = "#DF6D41"

/** Other explorers' routes: the olive of their live markers. */
private const val SHARED_ROUTE_COLOR = "#AAA648"
private const val ROUTE_COLOR = "#DF6D41"

/** Only a route shown within this long (ms) is drawn in; older ones (e.g. on opening the map later) appear at once. */
private const val ROUTE_DRAW_FRESH_MS = 15_000L

/** Draw-in time grows with the route's length: 1.2 s for a short walk up to 2.5 s for long drives. */
private fun routeDrawMs(meters: Double): Long = (1_200 + meters / 5_000 * 1_300).toLong().coerceIn(1_200, 2_500)

private fun easeInOut(t: Float): Float = if (t < 0.5f) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2

/** The first [fraction] of the line (by length), ending exactly on the interpolated point. */
private fun partialLine(points: List<Point>, fraction: Float): List<Point> {
    if (fraction >= 1f) return points
    val cum = DoubleArray(points.size)
    for (i in 1 until points.size) {
        cum[i] = cum[i - 1] + Geo.distanceMeters(points[i - 1].latitude(), points[i - 1].longitude(), points[i].latitude(), points[i].longitude())
    }
    val goal = cum.last() * fraction.coerceAtLeast(0f)
    val i = cum.indexOfFirst { it >= goal }.coerceAtLeast(1)
    val a = points[i - 1]
    val b = points[i]
    val seg = cum[i] - cum[i - 1]
    val k = if (seg > 0) (goal - cum[i - 1]) / seg else 0.0
    val tip = Point.fromLngLat(a.longitude() + (b.longitude() - a.longitude()) * k, a.latitude() + (b.latitude() - a.latitude()) * k)
    return points.subList(0, i) + tip
}

/**
 * Draws a route in along the road: [apply] gets a growing prefix of the line every frame, with a glowing
 * dot riding its tip (in [tips]). [points] is read every frame, so the line can change meanwhile.
 */
private suspend fun drawRouteIn(points: () -> List<Point>, color: String, tips: CircleAnnotationManager?, apply: (List<Point>) -> Unit) {
    val first = points()
    val meters = (1 until first.size).sumOf {
        Geo.distanceMeters(first[it - 1].latitude(), first[it - 1].longitude(), first[it].latitude(), first[it].longitude())
    }
    val duration = routeDrawMs(meters).toFloat()
    val tip = tips?.create(
        CircleAnnotationOptions().withPoint(first[0]).withCircleColor(color).withCircleRadius(6.0)
            .withCircleStrokeColor("#FFFFFF").withCircleStrokeWidth(2.5).withCircleBlur(0.1),
    )
    try {
        val start = withFrameMillis { it }
        while (true) {
            val t = ((withFrameMillis { it } - start) / duration).coerceIn(0f, 1f)
            val part = partialLine(points(), easeInOut(t))
            apply(if (part.size >= 2) part else listOf(part[0], part[0]))
            if (tip != null) {
                tip.point = part.last()
                tips.update(tip)
            }
            if (t >= 1f) break
        }
    } finally {
        tip?.let { runCatching { tips.delete(it) } }
    }
}

private suspend fun drawRouteIn(points: List<Point>, color: String, tips: CircleAnnotationManager?, apply: (List<Point>) -> Unit) =
    drawRouteIn({ points }, color, tips, apply)

private fun easeOut(t: Float): Float = 1f - (1f - t).pow(3)

/**
 * The size factor Mapbox applies to a (viewport-aligned) symbol at [point] on a tilted map:
 * 0.5 + 0.5 × (camera→centre distance ÷ camera→point distance), so markers nearer the camera draw
 * larger. A view annotation isn't scaled, so the deploying pin applies it itself to match the real
 * marker. Ground distance per horizontal pixel grows with the distance from the camera, which gives
 * the distance ratio.
 */
private fun perspectiveScale(mapView: MapView, point: Point): Float {
    val map = mapView.mapboxMap
    if (map.cameraState.pitch < 1.0) return 1f
    fun metersPerPixel(at: ScreenCoordinate): Double {
        val a = map.coordinateForPixel(ScreenCoordinate(at.x - 50.0, at.y))
        val b = map.coordinateForPixel(ScreenCoordinate(at.x + 50.0, at.y))
        return Geo.distanceMeters(a.latitude(), a.longitude(), b.latitude(), b.longitude()) / 100.0
    }
    val atPoint = metersPerPixel(map.pixelForCoordinate(point))
    val atCenter = metersPerPixel(map.pixelForCoordinate(map.cameraState.center))
    if (atPoint <= 0.0 || atCenter <= 0.0) return 1f
    return (0.5 + 0.5 * atCenter / atPoint).toFloat().coerceIn(0.5f, 2f)
}

/** Ease-out with one small bounce at the end (for the marker landing). */
private fun bounceOut(t: Float): Float = when {
    t < 0.7f -> easeOut(t / 0.7f)
    else -> 1f - 0.12f * sin(((t - 0.7f) / 0.3f) * Math.PI).toFloat()
}

/** Layer id of the Mapbox location puck (see LocationComponentConstants). */
private const val PUCK_LAYER = "mapbox-location-indicator-layer"

/** Camera tilt used in 3D mode. */
const val Pitch3D = 58.0

/** Zoom at/below which the globe is shown head-on, and from which full 3D tilt is allowed again. */
private const val GLOBE_FLAT_ZOOM = 2.0
private const val GLOBE_TILT_ZOOM = 5.0

/** Below this zoom the map is (turning into) a globe and drags are handled by [GlobeDrag]. */
private const val GLOBE_DRAG_ZOOM = 6.0

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
    /** When the current route was shown (ms): a new value draws the line in along the road. */
    routeDrawnAt: Long = 0L,
    /** Other explorers' routes while they navigate (their line ahead and destination). */
    sharedRoutes: List<SharedRouteLine> = emptyList(),
    /** Live phone orientation; the location arrow points where the phone points. */
    heading: StateFlow<DeviceOrientation?>? = null,
    /** The user started dragging the map (used to pause the navigation camera). */
    onUserPan: (() -> Unit)? = null,
    /** Long press on the map (not on a marker): where to create a shared marker. Null = disabled. */
    onMapLongClick: ((Double, Double) -> Unit)? = null,
    /** A marker being deployed: animated at its spot until it's done (or failed). */
    deploy: PinDeploy? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = FaunaryTheme.colors
    val currentOnMarkerClick by rememberUpdatedState(onMarkerClick)
    val currentOnMapClick by rememberUpdatedState(onMapClick)
    val currentOnMapLongClick by rememberUpdatedState(onMapLongClick)
    val currentOnCameraIdle by rememberUpdatedState(onCameraIdle)
    val currentOnStackClick by rememberUpdatedState(onStackClick)
    val currentOnUserPan by rememberUpdatedState(onUserPan)

    val mapView = remember {
        MapView(
            context,
            MapInitOptions(
                context,
                // The default (HEIGHT_ONLY) stops the globe turning north/south past a zoom-dependent
                // latitude (about ±26° when zoomed out), so vertical drags got stuck.
                mapOptions = MapInitOptions.getDefaultMapOptions(context).toBuilder().constrainMode(ConstrainMode.NONE).build(),
                // No default style: ours is loaded right after (below). The default is Mapbox Standard,
                // which would also clash with the 3D style that imports Standard as its basemap.
                styleUri = null,
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
    var sharedRouteManager by remember { mutableStateOf<PolylineAnnotationManager?>(null) }
    var routeTipManager by remember { mutableStateOf<CircleAnnotationManager?>(null) }
    var sharedDestManager by remember { mutableStateOf<CircleAnnotationManager?>(null) }
    val annotationToKey = remember { mutableMapOf<String, String>() }
    val annotationToStack = remember { mutableMapOf<String, PhotoPin>() }
    var lastAnnotationClick by remember { mutableStateOf(0L) }
    var legManager by remember { mutableStateOf<PolylineAnnotationManager?>(null) }
    var deployRingManager by remember { mutableStateOf<CircleAnnotationManager?>(null) }
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
        mapView.mapboxMap.addOnMapLongClickListener { point ->
            val handler = currentOnMapLongClick ?: return@addOnMapLongClickListener false
            spider = null
            handler(point.latitude(), point.longitude())
            true
        }
        mapView.mapboxMap.addOnMapClickListener {
            // The annotation plugin also sees this tap; ignore it if a marker was just hit.
            if (SystemClock.uptimeMillis() - lastAnnotationClick > 300) {
                spider = null
                currentOnMapClick()
            }
            false
        }
        val globeDrag = GlobeDrag(mapView)
        mapView.gestures.addOnMoveListener(object : OnMoveListener {
            override fun onMoveBegin(detector: MoveGestureDetector) {
                globeDrag.begin()
                currentOnUserPan?.invoke()
            }
            override fun onMove(detector: MoveGestureDetector) = globeDrag.move(detector)
            override fun onMoveEnd(detector: MoveGestureDetector) = globeDrag.end()
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
                // Deploy ripples of a new shared marker, also under the markers.
                deployRingManager = mapView.annotations.createCircleAnnotationManager()
                // Other explorers' routes, under the user's own route.
                sharedRouteManager = mapView.annotations.createPolylineAnnotationManager()
                sharedDestManager = mapView.annotations.createCircleAnnotationManager()
                // Route line sits above the rings but below every marker.
                routeManager = mapView.annotations.createPolylineAnnotationManager()
                // Glowing tip of a route line being drawn in.
                routeTipManager = mapView.annotations.createCircleAnnotationManager()
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
    val photoMarkers = markers.filter { it.kind == MarkerKind.OWN || it.kind == MarkerKind.COMMUNITY }
    val pinMarkers = byKind[MarkerKind.PIN].orEmpty()
    val pins = remember(photoMarkers, stackZoom) { groupPins(photoMarkers, stackZoom) }
    val activeSpider = spider?.takeIf { s -> pins.any { it.key == s.pinKey } }
    val draws = remember(pins, pinMarkers, activeSpider, selectedKey) {
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
            pinMarkers.forEach { m ->
                add(PinDraw(m, Point.fromLngLat(m.longitude, m.latitude), 1, m.key == selectedKey, null))
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
    var drawnRoute by remember { mutableStateOf(0L) }
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
        // A route just shown is drawn in along the road (not again on a theme/style reload).
        val drawIn = fitRoute && routeDrawnAt != drawnRoute && System.currentTimeMillis() - routeDrawnAt < ROUTE_DRAW_FRESH_MS
        drawnRoute = routeDrawnAt
        val start = if (drawIn) listOf(pts[0], pts[0]) else pts
        routeLines += m.create(
            listOf(
                PolylineAnnotationOptions().withPoints(start).withLineColor(if (colors.isDark) "#29231F" else "#FBF8F1")
                    .withLineWidth(9.0).withLineJoin(LineJoin.ROUND),
                PolylineAnnotationOptions().withPoints(start).withLineColor("#DF6D41")
                    .withLineWidth(5.0).withLineJoin(LineJoin.ROUND),
            ),
        )
        if (drawIn) {
            val tips = routeTipManager
            launch {
                drawRouteIn(pts, ROUTE_COLOR, tips) { part ->
                    routeLines.forEach { it.points = part }
                    m.update(routeLines)
                }
            }
        }
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

    // Other explorers' routes: an olive line to an olive destination dot, updated in place by key. A route
    // that has just started is drawn in along the road, the same animation its owner sees; that runs in
    // its own job so the updates arriving meanwhile only move its target instead of restarting it.
    val sharedLines = remember { mutableMapOf<String, PolylineAnnotation>() }
    val sharedDests = remember { mutableMapOf<String, CircleAnnotation>() }
    val sharedTargets = remember { mutableMapOf<String, List<Point>>() }
    val sharedDrawJobs = remember { mutableMapOf<String, Job>() }
    val sharedDrawn = remember { mutableMapOf<String, Long>() }
    val sharedScope = rememberCoroutineScope()
    LaunchedEffect(sharedRouteManager, sharedDestManager, sharedRoutes, colors.isDark) {
        val lines = sharedRouteManager ?: return@LaunchedEffect
        val dests = sharedDestManager ?: return@LaunchedEffect
        val wanted = sharedRoutes.filter { it.points.size >= 2 }.associateBy { it.key }
        (sharedLines.keys - wanted.keys).forEach { k ->
            sharedDrawJobs.remove(k)?.cancel()
            sharedTargets.remove(k)
            sharedDrawn.remove(k)
            sharedLines.remove(k)?.let(lines::delete)
        }
        (sharedDests.keys - wanted.keys).forEach { k -> sharedDests.remove(k)?.let(dests::delete) }
        wanted.forEach { (k, r) ->
            val pts = r.points.map { (lat, lng) -> Point.fromLngLat(lng, lat) }
            val dest = Point.fromLngLat(r.destLng, r.destLat)
            sharedTargets[k] = pts
            val isNewRoute = sharedDrawn[k] != r.startedAt
            sharedDrawn[k] = r.startedAt
            val age = System.currentTimeMillis() - r.startedAt
            val drawIn = isNewRoute && r.startedAt > 0 && age in -ROUTE_DRAW_FRESH_MS..ROUTE_DRAW_FRESH_MS
            val line = sharedLines[k]
            when {
                line == null -> sharedLines[k] = lines.create(
                    PolylineAnnotationOptions().withPoints(if (drawIn) listOf(pts[0], pts[0]) else pts).withLineColor(SHARED_ROUTE_COLOR)
                        .withLineWidth(4.0).withLineOpacity(0.85).withLineJoin(LineJoin.ROUND),
                )
                sharedDrawJobs[k]?.isActive != true && !drawIn -> {
                    line.points = pts
                    lines.update(line)
                }
            }
            if (drawIn) {
                sharedDrawJobs.remove(k)?.cancel()
                val target = sharedLines.getValue(k)
                sharedDrawJobs[k] = sharedScope.launch {
                    drawRouteIn({ sharedTargets[k] ?: pts }, SHARED_ROUTE_COLOR, dests) { part ->
                        target.points = part
                        lines.update(target)
                    }
                    // Settle on the latest line.
                    sharedTargets[k]?.let {
                        target.points = it
                        lines.update(target)
                    }
                }
            }
            val dot = sharedDests[k]
            if (dot != null) {
                dot.point = dest
                dests.update(dot)
            } else {
                sharedDests[k] = dests.create(
                    CircleAnnotationOptions().withPoint(dest).withCircleColor(SHARED_ROUTE_COLOR).withCircleRadius(7.0)
                        .withCircleStrokeColor(if (colors.isDark) "#29231F" else "#FBF8F1").withCircleStrokeWidth(2.5),
                )
            }
        }
    }
    // Style reloads recreate the managers; drop stale handles so the routes are drawn again.
    LaunchedEffect(sharedRouteManager, sharedDestManager) {
        sharedDrawJobs.values.forEach { it.cancel() }
        sharedDrawJobs.clear()
        sharedLines.clear()
        sharedDests.clear()
        sharedTargets.clear()
        sharedDrawn.clear()
    }

    // Deploying a shared marker: it drops onto the spot, ripples while the server saves it, then pops
    // when it's live (or fades if it failed). The pin is a view annotation (a plain view pinned to the
    // spot): animating a symbol means re-uploading map data every frame, which cross-fades the old and
    // new symbol and ghosts a second pin. The ripple is a circle layer, which doesn't fade.
    val deployNow by rememberUpdatedState(deploy)
    LaunchedEffect(deployRingManager, deploy?.latitude, deploy?.longitude, colors.isDark) {
        val rings = deployRingManager ?: return@LaunchedEffect
        rings.deleteAll()
        val target = deploy ?: return@LaunchedEffect
        val point = Point.fromLngLat(target.longitude, target.latitude)
        val bitmap = withContext(Dispatchers.IO) {
            markerFactory.marker(
                MapMarker("deploy", target.latitude, target.longitude, null, AnimalCategory.OTHER, MarkerKind.PIN, pinIcon = PinIcon.FLAG),
                selected = false, dark = colors.isDark,
            )
        }
        val dropPx = (DEPLOY_DROP * density.density).toFloat()
        // Same on-screen size as the marker symbol: the map scales images from the bitmap's density to
        // the current display density (they differ when the phone's display size is changed), and so
        // does a BitmapDrawable — raw bitmap pixels would draw the pin smaller than the real marker.
        val drawable = BitmapDrawable(context.resources, bitmap)
        val pinW = drawable.intrinsicWidth
        val pinH = drawable.intrinsicHeight
        // The manager positions the container; the pin inside is what moves. Room above for the drop
        // and around for the pop, with the pin's tip at the container's bottom centre (= the spot).
        val pinView = ImageView(context).apply {
            setImageDrawable(drawable)
            layoutParams = FrameLayout.LayoutParams(pinW, pinH, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            pivotX = pinW / 2f
            pivotY = pinH.toFloat()
            alpha = 0f
            translationY = -dropPx
        }
        val container = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            layoutParams = FrameLayout.LayoutParams((pinW * 1.6f).toInt(), (pinH * 1.4f + dropPx).toInt())
            addView(pinView)
        }
        mapView.viewAnnotationManager.addViewAnnotation(
            container,
            viewAnnotationOptions {
                geometry(point)
                annotationAnchor { anchor(ViewAnnotationAnchor.BOTTOM) }
                allowOverlap(true)
                allowOverlapWithPuck(true)
            },
        )
        val ring = rings.create(
            // An outlined ripple (barely filled), so it reads as an effect, never as a second marker.
            CircleAnnotationOptions().withPoint(point).withCircleColor(DEPLOY_COLOR)
                .withCircleStrokeColor(DEPLOY_COLOR).withCircleStrokeWidth(2.0)
                .withCircleRadius(0.0).withCircleOpacity(0.0).withCircleStrokeOpacity(0.0),
        )
        try {
            val start = withFrameMillis { it }
            var phaseStart = start
            var phase = PinDeploy.Phase.DEPLOYING
            while (isActive) {
                val now = withFrameMillis { it }
                val current = deployNow?.phase ?: break
                // Match the symbol's perspective scaling on a tilted map (see perspectiveScale).
                val base = perspectiveScale(mapView, point)
                if (current != phase) {
                    phase = current
                    phaseStart = now
                }
                val t = (now - start).toFloat()
                val p = (now - phaseStart).toFloat()
                // Drop with a small bounce, fading in.
                val drop = (t / DEPLOY_DROP_MS).coerceIn(0f, 1f)
                val dropOffset = dropPx * (1f - bounceOut(drop))
                var ringChanged = true
                when (phase) {
                    PinDeploy.Phase.DEPLOYING -> {
                        // Waiting for the server: ripples from the base and a gentle bob.
                        val pulsing = t > DEPLOY_DROP_MS
                        val cycle = ((t - DEPLOY_DROP_MS).coerceAtLeast(0f) % DEPLOY_PULSE_MS) / DEPLOY_PULSE_MS
                        // Fades in as it grows from the base, then out: no solid dot at the start.
                        val fade = if (pulsing) (1f - cycle) * (cycle * 6f).coerceAtMost(1f) else 0f
                        ring.circleRadius = 14.0 + 26.0 * easeOut(cycle)
                        ring.circleOpacity = 0.12 * fade
                        ring.circleStrokeOpacity = 0.75 * fade
                        val bob = if (pulsing) 3f * density.density * sin(cycle * 2 * Math.PI).toFloat() else 0f
                        pinView.translationY = -dropOffset - bob
                        pinView.alpha = (t / 150f).coerceIn(0f, 1f)
                        pinView.scaleX = base
                        pinView.scaleY = base
                    }
                    PinDeploy.Phase.DEPLOYED -> {
                        // Live: one strong ripple and a pop.
                        val k = (p / DEPLOY_POP_MS).coerceIn(0f, 1f)
                        ring.circleRadius = 14.0 + 46.0 * easeOut(k)
                        ring.circleOpacity = 0.18 * (1f - k)
                        ring.circleStrokeOpacity = 0.9 * (1f - k)
                        val pop = base * (1f + 0.28f * sin(k * Math.PI).toFloat())
                        pinView.translationY = -dropOffset
                        pinView.alpha = 1f
                        pinView.scaleX = pop
                        pinView.scaleY = pop
                    }
                    PinDeploy.Phase.SETTLING -> {
                        // At rest, exactly over the real marker now drawn underneath.
                        ringChanged = ring.circleStrokeOpacity != 0.0
                        ring.circleOpacity = 0.0
                        ring.circleStrokeOpacity = 0.0
                        pinView.translationY = 0f
                        pinView.alpha = 1f
                        pinView.scaleX = base
                        pinView.scaleY = base
                    }
                    PinDeploy.Phase.FAILED -> {
                        val k = (p / DEPLOY_FAIL_MS).coerceIn(0f, 1f)
                        ringChanged = ring.circleStrokeOpacity != 0.0
                        ring.circleOpacity = 0.0
                        ring.circleStrokeOpacity = 0.0
                        pinView.alpha = 1f - k
                        pinView.scaleX = base * (1f - 0.3f * k)
                        pinView.scaleY = base * (1f - 0.3f * k)
                    }
                }
                if (ringChanged) rings.update(ring)
            }
        } finally {
            mapView.viewAnnotationManager.removeViewAnnotation(container)
            runCatching { rings.deleteAll() }
        }
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

/**
 * Free drag for the globe. Mapbox's own globe pan only followed one diagonal, so at globe zooms the
 * finger's movement is turned into a camera move here: the globe turns with the finger in every
 * direction, and keeps spinning briefly (decelerating) after a flick.
 */
private class GlobeDrag(private val mapView: MapView) {
    private class Sample(val time: Long, val x: Float, val y: Float)

    private var active = false
    private val samples = ArrayDeque<Sample>()
    private var pointers = 0
    private var lastX = Float.NaN
    private var lastY = Float.NaN

    fun begin() {
        active = mapView.mapboxMap.cameraState.zoom < GLOBE_DRAG_ZOOM
        // Mapbox's fling uses the same globe pan, so it's replaced by ours while dragging the globe.
        if (mapView.gestures.scrollDecelerationEnabled == active) mapView.gestures.scrollDecelerationEnabled = !active
        samples.clear()
        lastX = Float.NaN
    }

    /** True when the move was applied here, so Mapbox skips its own. */
    fun move(detector: MoveGestureDetector): Boolean {
        if (!active) return false
        val focal = detector.focalPoint
        // Start over when a finger is added or lifted: the focal point jumps to the new fingers' centre.
        if (lastX.isNaN() || detector.pointersCount != pointers) {
            pointers = detector.pointersCount
            lastX = focal.x
            lastY = focal.y
            samples.clear()
            return true
        }
        val dx = (focal.x - lastX).toDouble()
        val dy = (focal.y - lastY).toDouble()
        lastX = focal.x
        lastY = focal.y
        val now = SystemClock.uptimeMillis()
        samples.addLast(Sample(now, focal.x, focal.y))
        while (now - samples.first().time > VELOCITY_WINDOW_MS) samples.removeFirst()
        if (dx == 0.0 && dy == 0.0) return true
        val map = mapView.mapboxMap
        val cam = map.cameraState
        map.setCamera(CameraOptions.Builder().center(shift(cam.center, dx, dy, cam.bearing, arcPerPixel())).build())
        return true
    }

    fun end() {
        if (!active) return
        active = false
        // Finger speed over the last moments (none if it paused before lifting).
        val now = SystemClock.uptimeMillis()
        while (samples.isNotEmpty() && now - samples.first().time > VELOCITY_WINDOW_MS) samples.removeFirst()
        if (samples.size < 2) return
        val first = samples.first()
        val last = samples.last()
        val span = max(16L, last.time - first.time).toDouble()
        val vx = (last.x - first.x) / span // px per ms
        val vy = (last.y - first.y) / span
        val speed = sqrt(vx * vx + vy * vy)
        if (speed < MIN_FLING_SPEED) return
        // The coast starts at the finger's speed (the interpolator's initial slope is 3) and eases out;
        // capped so a hard flick doesn't spin the world.
        val coast = min(FLING_DURATION_MS / 3.0, MAX_FLING_PX / speed)
        val cam = mapView.mapboxMap.cameraState
        mapView.mapboxMap.easeTo(
            CameraOptions.Builder().center(shift(cam.center, vx * coast, vy * coast, cam.bearing, arcPerPixel())).build(),
            MapAnimationOptions.mapAnimationOptions {
                duration(FLING_DURATION_MS)
                interpolator(DecelerateInterpolator(1.5f))
            },
        )
    }

    /**
     * Degrees of arc on the globe per screen pixel at the camera centre, measured from the map itself
     * (so it's right for any zoom, screen density and globe/flat blend).
     */
    private fun arcPerPixel(): Double {
        val map = mapView.mapboxMap
        val center = map.cameraState.center
        val c = map.pixelForCoordinate(center)
        val probe = map.coordinateForPixel(ScreenCoordinate(c.x, c.y + PROBE_PX))
        val arc = arcDegrees(center, probe) / PROBE_PX
        return if (arc.isFinite() && arc > 0.0) arc else 0.0
    }

    private companion object {
        const val VELOCITY_WINDOW_MS = 80L
        const val MIN_FLING_SPEED = 0.3 // px per ms
        const val MAX_FLING_PX = 1_500.0
        const val FLING_DURATION_MS = 700L
        const val PROBE_PX = 40.0

        /** Great-circle distance between two points, in degrees. */
        fun arcDegrees(a: Point, b: Point): Double {
            val lat1 = Math.toRadians(a.latitude())
            val lat2 = Math.toRadians(b.latitude())
            val dLat = lat2 - lat1
            val dLng = Math.toRadians(b.longitude() - a.longitude())
            val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLng / 2).pow(2)
            return Math.toDegrees(2 * kotlin.math.asin(sqrt(h.coerceIn(0.0, 1.0))))
        }

        /**
         * [center] after the finger moved ([dx], [dy]) screen pixels, so the globe surface follows the
         * finger 1:1. Both the globe and the flat map are locally true to shape at the centre, so a pixel
         * is [arcPerPx] degrees of latitude, and that divided by cos(latitude) degrees of longitude.
         */
        fun shift(center: Point, dx: Double, dy: Double, bearing: Double, arcPerPx: Double): Point {
            val b = Math.toRadians(bearing)
            // Finger movement in map directions (screen is rotated by the bearing).
            val east = dx * cos(b) - dy * sin(b)
            val north = -(dx * sin(b) + dy * cos(b))
            val lat = center.latitude()
            val cosLat = cos(Math.toRadians(lat)).coerceAtLeast(0.1)
            // Dragging moves the globe with the finger, so the camera centre moves the opposite way.
            val newLat = (lat - north * arcPerPx).coerceIn(-85.0, 85.0)
            val newLng = ((center.longitude() - east * arcPerPx / cosLat + 540.0) % 360.0) - 180.0
            return Point.fromLngLat(newLng, newLat)
        }
    }
}

/** One photo annotation to draw: a single find, a stack's top photo ([stack] set) or a fanned-out member. */
private data class PinDraw(
    val marker: MapMarker,
    val point: Point,
    val count: Int,
    val selected: Boolean,
    val stack: PhotoPin?,
)
