package com.faunary.app.ui.map

import android.os.SystemClock
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.faunary.app.ui.theme.FaunaryTheme
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.easeTo
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.annotation.AnnotationConfig
import com.mapbox.maps.plugin.annotation.AnnotationSourceOptions
import com.mapbox.maps.plugin.annotation.ClusterOptions
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.OnPointAnnotationClickListener
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.attribution.attribution
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.logo.logo
import com.mapbox.maps.plugin.scalebar.scalebar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

data class MapMarker(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val photoPath: String?,
    val category: AnimalCategory,
)

/** Default camera when there is no data or GPS yet (Taman Suropati, Jakarta — as in the design). */
val DefaultCenter = GeoPoint(-6.1990, 106.8322)

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
    selectedId: Long? = null,
    onMarkerClick: (Long) -> Unit = {},
    onMapClick: () -> Unit = {},
    initialCenter: GeoPoint = DefaultCenter,
    initialZoom: Double = 14.0,
    showUserLocation: Boolean = false,
    ornamentBottomPadding: Dp = 0.dp,
    darkTheme: Boolean = isSystemInDarkTheme(),
    threeD: Boolean = false,
    onCameraSnapshot: ((GeoPoint, Double) -> Unit)? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = FaunaryTheme.colors
    val currentOnMarkerClick by rememberUpdatedState(onMarkerClick)
    val currentOnMapClick by rememberUpdatedState(onMapClick)

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
    val markerFactory = remember { MarkerFactory(density.density) }
    var manager by remember { mutableStateOf<PointAnnotationManager?>(null) }
    val annotationToSighting = remember { mutableMapOf<String, Long>() }
    var lastAnnotationClick by remember { mutableStateOf(0L) }

    DisposableEffect(mapView) {
        controller.mapView = mapView
        mapView.mapboxMap.addOnMapClickListener {
            // The annotation plugin also sees this tap; ignore it if a marker was just hit.
            if (SystemClock.uptimeMillis() - lastAnnotationClick > 300) currentOnMapClick()
            false
        }
        onDispose {
            mapView.mapboxMap.cameraState.let { cam ->
                onCameraSnapshot?.invoke(GeoPoint(cam.center.latitude(), cam.center.longitude()), cam.zoom)
            }
            controller.mapView = null
            mapView.onDestroy()
        }
    }

    LaunchedEffect(darkTheme, threeD) {
        mapView.mapboxMap.loadStyle(MapStyle.json(darkTheme, threeD)) {
            if (manager == null) {
                val canyon = 0xFFDF6D41.toInt()
                manager = mapView.annotations.createPointAnnotationManager(
                    AnnotationConfig(
                        annotationSourceOptions = AnnotationSourceOptions(
                            clusterOptions = ClusterOptions(
                                clusterRadius = 60,
                                circleRadius = 20.0,
                                textColor = 0xFFFBF8F1.toInt(),
                                textSize = 14.0,
                                colorLevels = listOf(0 to canyon),
                                clusterMaxZoom = 15,
                            ),
                        ),
                    ),
                ).apply {
                    addClickListener(OnPointAnnotationClickListener { annotation ->
                        lastAnnotationClick = SystemClock.uptimeMillis()
                        annotationToSighting[annotation.id]?.let(currentOnMarkerClick)
                        true
                    })
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

    LaunchedEffect(manager, markers, selectedId, colors.isDark) {
        val m = manager ?: return@LaunchedEffect
        val options = withContext(Dispatchers.Default) {
            markers.map { marker ->
                val selected = marker.id == selectedId
                marker.id to PointAnnotationOptions()
                    .withPoint(Point.fromLngLat(marker.longitude, marker.latitude))
                    .withIconImage(markerFactory.marker(marker.photoPath, marker.category, selected, colors.isDark))
                    .withSymbolSortKey(if (selected) 10.0 else 0.0)
            }
        }
        m.deleteAll()
        annotationToSighting.clear()
        val created = m.create(options.map { it.second })
        created.forEachIndexed { i, annotation -> annotationToSighting[annotation.id] = options[i].first }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
