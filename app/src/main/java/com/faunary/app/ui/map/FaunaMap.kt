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
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.toCameraOptions
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.easeTo
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.annotation.AnnotationConfig
import com.mapbox.maps.plugin.annotation.AnnotationSourceOptions
import com.mapbox.maps.plugin.annotation.ClusterOptions
import com.mapbox.maps.plugin.annotation.OnClusterClickListener
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.OnPointAnnotationClickListener
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createCircleAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.attribution.attribution
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.logo.logo
import com.mapbox.maps.plugin.scalebar.scalebar
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.cos
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
    /** Local file path or https URL; null draws the category emoji instead. */
    val photo: String?,
    val category: AnimalCategory,
    val kind: MarkerKind = MarkerKind.OWN,
    /** Name shown under live-user markers. */
    val label: String? = null,
)

/** Default camera when there is no data or GPS yet (Taman Suropati, Jakarta — as in the design). */
val DefaultCenter = GeoPoint(-6.1990, 106.8322)

/** Live explorers "beat" once per period: the avatar pops and an olive ring radiates out. */
private const val HEARTBEAT_PERIOD_MS = 3_000L
private const val HEARTBEAT_RING_MS = 1_200L
private const val HEARTBEAT_POP_MS = 320L

/** Camera tilt used in 3D mode. */
const val Pitch3D = 58.0

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

    fun flyTo(lat: Double, lng: Double, zoom: Double? = null, bottomPaddingPx: Double = 0.0) {
        val map = mapView?.mapboxMap ?: return
        map.flyTo(
            CameraOptions.Builder()
                .center(Point.fromLngLat(lng, lat))
                .zoom(zoom ?: max(map.cameraState.zoom, 15.0))
                .pitch(if (threeD) Pitch3D else 0.0)
                .padding(EdgeInsets(0.0, 0.0, bottomPaddingPx, 0.0))
                .build(),
            MapAnimationOptions.mapAnimationOptions { duration(800) },
        )
    }

    fun center(): GeoPoint? = mapView?.mapboxMap?.cameraState?.center?.let { GeoPoint(it.latitude(), it.longitude()) }
}

@Composable
fun rememberFaunaMapController() = remember { FaunaMapController() }

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
    /** Cluster tapped: kind, centre, and the ground radius (m) the cluster covers at this zoom. */
    onClusterClick: ((MarkerKind, Double, Double, Double) -> Unit)? = null,
    /** In-app route to draw, as (lat, lng) points; the camera fits it once when it changes. */
    route: List<Pair<Double, Double>>? = null,
    routeTopPadding: Dp = 0.dp,
    routeBottomPadding: Dp = 0.dp,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = FaunaryTheme.colors
    val currentOnMarkerClick by rememberUpdatedState(onMarkerClick)
    val currentOnMapClick by rememberUpdatedState(onMapClick)
    val currentOnCameraIdle by rememberUpdatedState(onCameraIdle)
    val currentOnClusterClick by rememberUpdatedState(onClusterClick)

    val mapView = remember {
        MapView(
            context,
            MapInitOptions(
                context,
                cameraOptions = CameraOptions.Builder()
                    .center(Point.fromLngLat(initialCenter.longitude, initialCenter.latitude))
                    .zoom(initialZoom)
                    .pitch(if (threeD) Pitch3D else 0.0)
                    .build(),
            ),
        ).apply {
            scalebar.enabled = false
            compass.enabled = false
        }
    }
    val markerFactory = remember { MarkerFactory(context, density.density) }
    var managers by remember { mutableStateOf<Map<MarkerKind, PointAnnotationManager>>(emptyMap()) }
    var pulseManager by remember { mutableStateOf<CircleAnnotationManager?>(null) }
    var routeManager by remember { mutableStateOf<PolylineAnnotationManager?>(null) }
    val annotationToKey = remember { mutableMapOf<String, String>() }
    var lastAnnotationClick by remember { mutableStateOf(0L) }

    DisposableEffect(mapView) {
        controller.mapView = mapView
        mapView.mapboxMap.addOnMapClickListener {
            // The annotation plugin also sees this tap; ignore it if a marker was just hit.
            if (SystemClock.uptimeMillis() - lastAnnotationClick > 300) currentOnMapClick()
            false
        }
        val idle = mapView.mapboxMap.subscribeMapIdle {
            val map = mapView.mapboxMap
            val b = map.coordinateBoundsForCamera(map.cameraState.toCameraOptions())
            currentOnCameraIdle?.invoke(Bounds(b.south(), b.west(), b.north(), b.east()))
        }
        onDispose {
            idle.cancel()
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
                managers = MarkerKind.entries.associateWith { kind ->
                    // Own finds cluster in Canyon, community finds in Info blue; live users never cluster.
                    val clusterColor = when (kind) {
                        MarkerKind.OWN -> 0xFFDF6D41.toInt()
                        MarkerKind.COMMUNITY -> 0xFF7395BF.toInt()
                        MarkerKind.LIVE -> null
                    }
                    mapView.annotations.createPointAnnotationManager(
                        AnnotationConfig(
                            annotationSourceOptions = clusterColor?.let {
                                AnnotationSourceOptions(
                                    clusterOptions = ClusterOptions(
                                        clusterRadius = 60,
                                        circleRadius = 20.0,
                                        textColor = 0xFFFBF8F1.toInt(),
                                        textSize = 14.0,
                                        colorLevels = listOf(0 to it),
                                        clusterMaxZoom = 15,
                                    ),
                                )
                            },
                        ),
                    ).apply {
                        addClickListener(OnPointAnnotationClickListener { annotation ->
                            lastAnnotationClick = SystemClock.uptimeMillis()
                            annotationToKey[annotation.id]?.let(currentOnMarkerClick)
                            true
                        })
                        if (clusterColor != null) addClusterClickListener(OnClusterClickListener { cluster ->
                            lastAnnotationClick = SystemClock.uptimeMillis()
                            (cluster.originalFeature.geometry() as? Point)?.let { p ->
                                // Web-mercator metres per (dp) pixel; the cluster radius is 60 px.
                                val zoom = mapView.mapboxMap.cameraState.zoom
                                val metersPerPx = 156_543.03392 * cos(Math.toRadians(p.latitude())) / 2.0.pow(zoom)
                                currentOnClusterClick?.invoke(kind, p.latitude(), p.longitude(), metersPerPx * 60 * 1.5)
                            }
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
            locationPuck = createDefault2DPuck(withBearing = false)
            pulsingEnabled = true
            pulsingColor = 0xFFDF6D41.toInt()
        }
    }

    LaunchedEffect(ornamentBottomPadding) {
        val px = with(density) { ornamentBottomPadding.toPx() }
        mapView.logo.updateSettings { marginBottom = px + 8f }
        mapView.attribution.updateSettings { marginBottom = px + 8f }
    }

    // One effect per layer so a live-position tick doesn't redraw every photo marker.
    val byKind = markers.groupBy { it.kind }
    MarkerKind.entries.forEach { kind ->
        val layer = byKind[kind].orEmpty()
        val layerSelected = selectedKey?.takeIf { k -> layer.any { it.key == k } }
        key(kind) {
            LaunchedEffect(managers, layer, layerSelected, colors.isDark) {
                val m = managers[kind] ?: return@LaunchedEffect
                val options = withContext(Dispatchers.IO) {
                    layer.map { marker ->
                        val selected = marker.key == layerSelected
                        marker.key to PointAnnotationOptions()
                            .withPoint(Point.fromLngLat(marker.longitude, marker.latitude))
                            .withIconImage(markerFactory.marker(marker, selected, colors.isDark))
                            .withSymbolSortKey(if (selected) 10.0 else 0.0)
                    }
                }
                m.annotations.forEach { annotationToKey.remove(it.id) }
                m.deleteAll()
                val created = m.create(options.map { it.second })
                created.forEachIndexed { i, annotation -> annotationToKey[annotation.id] = options[i].first }
            }
        }
    }

    // In-app route: soft light casing + Canyon line, then fit the whole route on screen.
    LaunchedEffect(routeManager, route, colors.isDark) {
        val m = routeManager ?: return@LaunchedEffect
        m.deleteAll()
        val pts = route?.map { (lat, lng) -> Point.fromLngLat(lng, lat) }?.takeIf { it.size >= 2 } ?: return@LaunchedEffect
        m.create(
            listOf(
                PolylineAnnotationOptions().withPoints(pts).withLineColor(if (colors.isDark) "#29231F" else "#FBF8F1")
                    .withLineWidth(9.0).withLineJoin(LineJoin.ROUND),
                PolylineAnnotationOptions().withPoints(pts).withLineColor("#DF6D41")
                    .withLineWidth(5.0).withLineJoin(LineJoin.ROUND),
            ),
        )
        val map = mapView.mapboxMap
        val side = with(density) { 48.dp.toPx().toDouble() }
        val padding = EdgeInsets(
            with(density) { routeTopPadding.toPx().toDouble() }, side,
            with(density) { routeBottomPadding.toPx().toDouble() }, side,
        )
        val camera = map.cameraForCoordinates(pts, CameraOptions.Builder().pitch(if (threeD) 45.0 else 0.0).build(), padding, 17.0, null)
        map.easeTo(camera, MapAnimationOptions.mapAnimationOptions { duration(900) })
    }

    // Heartbeat for live explorers, every HEARTBEAT_PERIOD_MS. Restarts when someone moves or joins.
    val livePoints = markers.filter { it.kind == MarkerKind.LIVE }.map { Triple(it.key, it.latitude, it.longitude) }
    LaunchedEffect(pulseManager, managers, livePoints) {
        val rings = pulseManager ?: return@LaunchedEffect
        val liveLayer = managers[MarkerKind.LIVE] ?: return@LaunchedEffect
        rings.deleteAll()
        if (livePoints.isEmpty()) return@LaunchedEffect
        val circles = rings.create(livePoints.map { (_, lat, lng) ->
            CircleAnnotationOptions()
                .withPoint(Point.fromLngLat(lng, lat))
                .withCircleColor("#AAA648")
                .withCircleRadius(0.0)
                .withCircleOpacity(0.0)
        })
        try {
            while (isActive) {
                val start = withFrameMillis { it }
                var elapsed = 0L
                while (elapsed < HEARTBEAT_RING_MS) {
                    elapsed = withFrameMillis { it } - start
                    val t = (elapsed.toFloat() / HEARTBEAT_RING_MS).coerceIn(0f, 1f)
                    val eased = 1f - (1f - t).pow(3)
                    circles.forEach {
                        it.circleRadius = 18.0 + 30.0 * eased
                        it.circleOpacity = 0.5 * (1f - t)
                    }
                    rings.update(circles)
                    // Avatar "pop": quick swell and settle at the start of each beat.
                    val pop = (elapsed.toFloat() / HEARTBEAT_POP_MS).coerceIn(0f, 1f)
                    val scale = 1.0 + 0.14 * sin(pop * Math.PI)
                    val avatars = liveLayer.annotations
                    avatars.forEach { it.iconSize = scale }
                    liveLayer.update(avatars)
                }
                delay(HEARTBEAT_PERIOD_MS - HEARTBEAT_RING_MS)
            }
        } finally {
            circles.forEach { it.circleOpacity = 0.0 }
            runCatching { rings.update(circles) }
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
