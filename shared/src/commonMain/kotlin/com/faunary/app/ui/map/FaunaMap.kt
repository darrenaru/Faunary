package com.faunary.app.ui.map

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.faunary.app.location.DeviceOrientation
import com.faunary.app.location.GeoPoint
import com.faunary.app.remote.Bounds
import kotlinx.coroutines.flow.StateFlow

/** Camera tilt while following the user during navigation (when the phone's tilt isn't used). */
const val NavigationPitch = 60.0

data class NavCamera(val latitude: Double, val longitude: Double, val zoom: Double, val bearing: Double, val pitch: Double)

/** Imperative handle for camera moves triggered from Compose (zoom buttons, "my location", etc.). */
expect class FaunaMapController() {
    fun zoomBy(delta: Double)

    fun flyTo(lat: Double, lng: Double, zoom: Double? = null, bottomPaddingPx: Double = 0.0, durationMs: Long = 800)

    fun zoom(): Double?

    /** Fits all [points] on screen (a single spot just zooms in close). */
    fun fit(points: List<Pair<Double, Double>>, bottomPaddingPx: Double = 0.0)

    /** Current navigation-relevant camera: (center, zoom, bearing, pitch). */
    fun navigationCamera(): NavCamera?

    /**
     * Navigation camera, applied immediately (called every frame): centred on the user, rotated and
     * tilted as given, with the user low on screen.
     */
    fun setNavigationCamera(cam: NavCamera, topPaddingPx: Double)

    fun center(): GeoPoint?
}

@Composable
fun rememberFaunaMapController() = remember { FaunaMapController() }

/** The Faunary map (Mapbox on both platforms): photo pins, live explorers, shared markers and routes. */
@Composable
expect fun FaunaMap(
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
    /** False while navigating: the route line is updated in place and the camera is left to [FaunaMapController.setNavigationCamera]. */
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
    /** Markers being deployed (here and by other explorers): each animated at its spot until done (or failed). */
    deploys: List<PinDeploy> = emptyList(),
)
