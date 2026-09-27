package com.faunary.app.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.ForkLeft
import androidx.compose.material.icons.rounded.ForkRight
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Merge
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RoundaboutLeft
import androidx.compose.material.icons.rounded.RoundaboutRight
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Signpost
import androidx.compose.material.icons.rounded.Straight
import androidx.compose.material.icons.rounded.TurnLeft
import androidx.compose.material.icons.rounded.TurnRight
import androidx.compose.material.icons.rounded.TurnSharpLeft
import androidx.compose.material.icons.rounded.TurnSharpRight
import androidx.compose.material.icons.rounded.TurnSlightLeft
import androidx.compose.material.icons.rounded.TurnSlightRight
import androidx.compose.material.icons.rounded.UTurnRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.faunary.app.data.AnimalSighting
import com.faunary.app.domain.AnimalCategory
import com.faunary.app.location.DeviceOrientation
import com.faunary.app.location.GeoPoint
import com.faunary.app.location.PlaceType
import com.faunary.app.location.RouteStep
import com.faunary.app.location.SearchedPlace
import com.faunary.app.location.TravelMode
import com.faunary.app.location.rememberOrientationSource
import com.faunary.app.remote.CommunitySighting
import com.faunary.app.remote.LiveRouteDto
import com.faunary.app.remote.MapPin
import com.faunary.app.ui.components.ButtonKind
import com.faunary.app.ui.components.CategoryAvatar
import com.faunary.app.ui.components.FaunaryButton
import com.faunary.app.ui.components.FaunaryCard
import com.faunary.app.ui.components.FaunaryIcons
import com.faunary.app.ui.components.FaunaryTextField
import com.faunary.app.ui.components.IconBadge
import com.faunary.app.ui.components.InfoRow
import com.faunary.app.ui.components.LocateButton
import com.faunary.app.ui.components.MapRoundButton
import com.faunary.app.ui.components.MapRoundIconButton
import com.faunary.app.ui.components.MapZoomControl
import com.faunary.app.ui.components.PermissionCard
import com.faunary.app.ui.components.PhotoThumb
import com.faunary.app.ui.components.Pill
import com.faunary.app.ui.components.SelectableChip
import com.faunary.app.ui.components.SurfaceIconButton
import com.faunary.app.ui.components.icon
import com.faunary.app.ui.components.softShadow
import com.faunary.app.ui.platform.KeepScreenOn
import com.faunary.app.ui.platform.PlatformBackHandler
import com.faunary.app.ui.platform.rememberCopyText
import com.faunary.app.ui.platform.rememberLocationPermission
import com.faunary.app.ui.platform.rememberShowMessage
import com.faunary.app.ui.theme.FaunaryTheme
import com.faunary.app.util.Format
import com.faunary.app.util.Geo
import com.faunary.app.util.currentTimeMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.exp
import kotlin.math.roundToInt

/** Launch intro: whole-globe start (a little west of Indonesia, so the globe turns on the way in). */
private val IntroGlobeCenter = GeoPoint(5.0, 70.0)
private const val INTRO_GLOBE_ZOOM = 0.6
private const val INTRO_FLIGHT_MS = 4_500L


@Composable
fun MapScreen(
    focusId: Long?,
    routeTo: Pair<Double, Double>?,
    routeLabel: String?,
    onOpenDetail: (Long) -> Unit,
    onOpenCommunity: (String) -> Unit,
    onOpenCamera: () -> Unit,
    /** Turn-by-turn navigation or search takes the whole screen: the tab bar should step aside. */
    onFullScreenChange: (Boolean) -> Unit = {},
    unreadNotifications: Int = 0,
    onOpenNotifications: () -> Unit = {},
    viewModel: MapViewModel = koinViewModel(),
    /** The platform's app-update banner under the filters (Android's self-updater); nothing on iOS. */
    updateBanner: @Composable (Modifier) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val routeUi by viewModel.route.collectAsStateWithLifecycle()
    val c = FaunaryTheme.colors
    val density = LocalDensity.current
    val controller = rememberFaunaMapController()
    val cardPaddingPx = with(density) { 260.dp.toPx().toDouble() }

    var locationDismissed by rememberSaveable { mutableStateOf(false) }
    var clusterList by remember { mutableStateOf<ClusterList?>(null) }
    /** The viewer's marker whose details form is open (a freshly deployed one, or one being edited). */
    var editingPin by remember { mutableStateOf<MapPin?>(null) }
    val deploy by viewModel.deploy.collectAsStateWithLifecycle()
    val deployAnimations by viewModel.deployAnimations.collectAsStateWithLifecycle()
    val justDeployed by viewModel.justDeployed.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val recentPlaces by viewModel.recentPlaces.collectAsStateWithLifecycle()
    val place by viewModel.place.collectAsStateWithLifecycle()
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val locationPermission = rememberLocationPermission { granted ->
        if (granted) {
            if (routeUi != null) viewModel.refreshRoute()
            else viewModel.refreshLocation { controller.flyTo(it.latitude, it.longitude, 15.0) }
        }
    }

    // Turn-by-turn navigation: camera follows the user until they pan the map themselves.
    val navigating = routeUi?.navigating == true
    var following by remember { mutableStateOf(true) }
    val windowHeightPx = LocalWindowInfo.current.containerSize.height.toDouble()
    LaunchedEffect(navigating) {
        following = true
        if (navigating) searchOpen = false
    }
    LaunchedEffect(navigating, searchOpen) { onFullScreenChange(navigating || searchOpen) }
    DisposableEffect(Unit) { onDispose { onFullScreenChange(false) } }
    PlatformBackHandler(enabled = searchOpen) { searchOpen = false }
    // Picked in search: frame a city/region whole, zoom in on a single spot.
    val showPlace: (SearchedPlace) -> Unit = { p ->
        searchOpen = false
        clusterList = null
        val box = p.bbox
        if (box != null && p.type.zoom < 14.0) controller.fit(listOf(box[0] to box[1], box[2] to box[3]), cardPaddingPx)
        else controller.flyTo(p.latitude, p.longitude, p.type.zoom, cardPaddingPx)
    }
    KeepScreenOn(navigating)
    PlatformBackHandler(enabled = navigating) { viewModel.stopNavigation() }
    // Device-orientation mode: the map turns and tilts with the phone (rotation-vector sensor,
    // i.e. gyroscope fused with accelerometer and compass). Off, or without the sensor: route direction.
    val orientationSource = rememberOrientationSource()
    val hasSensor = orientationSource != null
    var useDeviceHeading by rememberSaveable { mutableStateOf(true) }
    val deviceMode = hasSensor && useDeviceHeading
    val navFix by rememberUpdatedState(routeUi?.position)
    val navProgress by rememberUpdatedState(routeUi?.progress)
    val navZoom = if (routeUi?.mode == TravelMode.DRIVING) 16.5 else 17.5
    // Phone orientation, ~50×/s while the map is on screen: turns the location arrow and, in
    // navigation, the camera. A StateFlow read every frame, so it never recomposes the screen.
    val orientation = remember { MutableStateFlow<DeviceOrientation?>(null) }
    val declinationAt by rememberUpdatedState(routeUi?.position ?: state.lastFix)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(hasSensor, locationPermission.granted) {
        if (!hasSensor || !locationPermission.granted) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            orientationSource?.orientation { declinationAt }?.collect { orientation.value = it }
        }
    }
    // Camera loop, one step per frame: glides to each GPS fix and follows the phone's rotation/tilt in real time.
    LaunchedEffect(navigating, following, deviceMode) {
        if (!navigating || !following) return@LaunchedEffect
        var cam: NavCamera? = null
        var last = withFrameMillis { it }
        while (isActive) {
            val now = withFrameMillis { it }
            val dt = ((now - last) / 1000.0).coerceIn(0.0, 0.1)
            last = now
            val fix = navFix ?: continue
            val c = cam ?: controller.navigationCamera() ?: continue
            val o = orientation.value
            val targetBearing = if (deviceMode && o != null) o.heading else navProgress?.bearing ?: c.bearing
            val targetPitch = if (deviceMode && o != null) tiltToPitch(o.tilt) else NavigationPitch
            val move = 1 - exp(-dt * 4.0) // position/zoom: smooth glide between 1 s GPS fixes
            val turn = 1 - exp(-dt * 12.0) // rotation/tilt: fast, just enough to hide sensor jitter
            val next = NavCamera(
                latitude = c.latitude + (fix.latitude - c.latitude) * move,
                longitude = c.longitude + (fix.longitude - c.longitude) * move,
                zoom = c.zoom + (navZoom - c.zoom) * move,
                bearing = (c.bearing + angleDelta(c.bearing, targetBearing) * turn + 360.0) % 360.0,
                pitch = c.pitch + (targetPitch - c.pitch) * turn,
            )
            controller.setNavigationCamera(next, topPaddingPx = windowHeightPx * 0.42)
            cam = next
        }
    }

    // Opened from a detail screen's "Rute" button: draw that route straight away.
    LaunchedEffect(routeTo) {
        routeTo?.let { (lat, lng) -> viewModel.startRoute(lat, lng, routeLabel ?: "Tujuan") }
    }

    // Launch intro: open on the whole globe, then fly down to the user (once per app launch;
    // skipped when the map was opened for a specific entry or route).
    val playIntro = remember { focusId == null && routeTo == null && viewModel.camera == null && viewModel.takeIntro() }
    LaunchedEffect(playIntro) {
        if (!playIntro) return@LaunchedEffect
        delay(700) // let the globe draw before it starts moving
        val target = if (locationPermission.granted) viewModel.introTarget() else null
        val fallback = if (target == null) {
            snapshotFlow { state.loaded }.first { it }
            state.all.firstOrNull()?.let { GeoPoint(it.latitude, it.longitude) }
        } else null
        val dest = target ?: fallback ?: DefaultCenter
        controller.flyTo(dest.latitude, dest.longitude, if (target != null) 16.0 else 13.5, durationMs = INTRO_FLIGHT_MS)
    }

    // Centre the camera once when data first arrives: focused entry > latest entry > GPS.
    var centered by rememberSaveable { mutableStateOf(viewModel.camera != null || playIntro) }
    LaunchedEffect(state.loaded) {
        if (!state.loaded || centered || routeTo != null) return@LaunchedEffect
        centered = true
        val focus = focusId?.let { id -> state.all.firstOrNull { it.id == id } }
        when {
            focus != null -> {
                viewModel.select(ownKey(focus.id))
                controller.flyTo(focus.latitude, focus.longitude, 16.0, cardPaddingPx)
            }
            state.all.isNotEmpty() -> controller.flyTo(state.all.first().latitude, state.all.first().longitude, 13.5)
            locationPermission.granted -> viewModel.refreshLocation { controller.flyTo(it.latitude, it.longitude, 15.0) }
        }
    }
    LaunchedEffect(locationPermission.granted) {
        if (locationPermission.granted) viewModel.refreshLocation()
    }

    Box(Modifier.fillMaxSize().background(c.background)) {
        FaunaMap(
            markers = place?.let { state.markers + it.toMarker() } ?: state.markers,
            controller = controller,
            selectedKey = state.selection?.key,
            onMarkerClick = { key ->
                clusterList = null
                if (key == PLACE_KEY) {
                    viewModel.select(null)
                    place?.let { controller.flyTo(it.latitude, it.longitude, bottomPaddingPx = cardPaddingPx) }
                    return@FaunaMap
                }
                val draft = viewModel.ownPin(key)?.takeIf { it.isDraft }
                if (draft != null) {
                    viewModel.select(null)
                    editingPin = draft
                    return@FaunaMap
                }
                viewModel.select(key)
                state.markers.firstOrNull { it.key == key }?.let {
                    controller.flyTo(it.latitude, it.longitude, bottomPaddingPx = cardPaddingPx)
                }
            },
            onCameraIdle = viewModel::onCameraIdle,
            route = if (navigating) routeUi?.remaining ?: routeUi?.route?.points else routeUi?.route?.points,
            fitRoute = !navigating,
            routeDrawnAt = routeUi?.drawnAt ?: 0L,
            sharedRoutes = state.sharedRoutes.map { SharedRouteLine(it.userId, it.points, it.destLat, it.destLng, it.startedAt) },
            heading = orientation,
            onUserPan = { if (navigating) following = false },
            // Long press drops a shared marker there (not while navigating, where the map follows the user).
            onMapLongClick = if (viewModel.canCreatePins && !navigating) { lat, lng ->
                viewModel.select(null)
                clusterList = null
                viewModel.deployPin(lat, lng)
            } else null,
            // Other explorers' deploys play with the markers layer; this device's always does.
            deploys = deployAnimations.filter { it.mine || state.layers.pins },
            routeTopPadding = 190.dp,
            routeBottomPadding = BottomBarSpace + 250.dp,
            onMapClick = {
                viewModel.select(null)
                viewModel.clearPlace()
                clusterList = null
            },
            onStackClick = { keys, lat, lng ->
                viewModel.select(null)
                val items = viewModel.selectionsFor(keys)
                clusterList = if (items.isNotEmpty()) ClusterList(lat, lng, items) else null
                // Keep the zoom: the list shows everything, and "Perbesar peta" spreads them out.
                controller.flyTo(lat, lng, controller.zoom() ?: 15.0, cardPaddingPx)
            },
            showUserLocation = locationPermission.granted,
            ornamentBottomPadding = if (state.selection != null || clusterList != null || routeUi != null || place != null) 0.dp else BottomBarSpace,
            darkTheme = c.isDark,
            // Navigation always uses the 3D map (terrain + buildings); the user's 2D/3D choice returns afterwards.
            threeD = state.map3D || navigating,
            initialCenter = viewModel.camera?.first ?: if (playIntro) IntroGlobeCenter else DefaultCenter,
            initialZoom = viewModel.camera?.second ?: if (playIntro) INTRO_GLOBE_ZOOM else 14.0,
            onCameraSnapshot = { center, zoom -> viewModel.camera = center to zoom },
            modifier = Modifier.fillMaxSize(),
        )

        // Header + filters
        if (!navigating) Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp)) {
            MapHeader(
                searchText = place?.name,
                onSearch = { searchOpen = true },
                gpsAccuracy = state.lastFix?.accuracy?.takeIf { locationPermission.granted },
                visibleCount = state.filter?.let { state.counts[it] ?: 0 } ?: state.totalCount,
                filter = state.filter,
                showBell = state.online,
                unread = unreadNotifications,
                onBell = onOpenNotifications,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(10.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    CategoryFilterChip("Semua", Icons.Rounded.Apps, state.totalCount, state.filter == null) { viewModel.setFilter(null) }
                }
                AnimalCategory.entries.forEach { cat ->
                    val count = state.counts[cat] ?: 0
                    if (count > 0 || cat != AnimalCategory.OTHER) {
                        item(cat.name) {
                            CategoryFilterChip(cat.displayName, cat.icon, count, state.filter == cat) {
                                viewModel.setFilter(if (state.filter == cat) null else cat)
                            }
                        }
                    }
                }
            }
            updateBanner(Modifier.padding(start = 16.dp, end = 76.dp, top = 12.dp))
        }

        // Marker deployment status, just under the header and filters; then a few seconds of "Undo".
        AnimatedVisibility(
            visible = deploy != null || justDeployed != null,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 136.dp),
            enter = fadeIn(tween(200)) + slideInVertically(tween(250)) { -it / 2 },
            exit = fadeOut(tween(250)),
        ) {
            val phase = deploy?.phase ?: PinDeploy.Phase.DEPLOYED
            DeployStatusChip(phase, onUndo = if (deploy == null && justDeployed != null) viewModel::undoDeploy else null)
        }

        // Map controls
        if (!navigating) Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.online) {
                LayersButton(
                    layers = state.layers,
                    communityCount = state.community.size,
                    liveCount = state.liveUsers.size,
                    pinCount = state.pins.size,
                    shareLive = state.settings.shareLiveLocation,
                    onLayers = viewModel::setLayers,
                    onShareLive = { enabled ->
                        viewModel.setShareLive(enabled)
                        if (enabled && !locationPermission.granted) locationPermission.request()
                    },
                )
            }
            DimensionToggle(threeD = state.map3D, onClick = viewModel::toggle3D)
            LocateButton({
                if (locationPermission.granted) {
                    viewModel.refreshLocation { controller.flyTo(it.latitude, it.longitude, 16.0) }
                } else locationPermission.request()
            })
            MapZoomControl(onZoomIn = { controller.zoomBy(1.0) }, onZoomOut = { controller.zoomBy(-1.0) })
        }

        // Bottom area: permission rationale, empty hint or selected preview
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = BottomBarSpace + 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AnimatedVisibility(
                visible = !locationPermission.granted && !locationDismissed && state.selection == null && clusterList == null && routeUi == null && place == null,
                enter = fadeIn(tween(200)), exit = fadeOut(tween(150)),
            ) {
                PermissionCard(
                    icon = Icons.Rounded.LocationOn,
                    title = "Aktifkan lokasi",
                    message = "Faunary mencatat titik GPS setiap kali kamu memotret satwa, supaya temuanmu muncul di peta.",
                    actionLabel = "Izinkan Lokasi",
                    onAction = { locationPermission.request() },
                    permanentlyDenied = locationPermission.permanentlyDenied,
                    onDismiss = { locationDismissed = true },
                )
            }
            AnimatedVisibility(
                // Only when nothing at all is on the map: no own finds, others' finds, markers or explorers.
                visible = state.loaded && state.all.isEmpty() && state.community.isEmpty() && state.pins.isEmpty() && state.liveUsers.isEmpty() && routeUi == null && place == null && (locationPermission.granted || locationDismissed),
                enter = fadeIn(tween(200)), exit = fadeOut(tween(150)),
            ) {
                FaunaryCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryAvatar(AnimalCategory.CAT, size = 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Peta masih kosong", style = MaterialTheme.typography.titleSmall, color = c.foreground)
                            Text("Potret satwa pertamamu — titiknya akan muncul di sini.", style = MaterialTheme.typography.bodySmall, color = c.foregroundSecondary)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    FaunaryButton("Ambil Foto", onOpenCamera, Modifier.fillMaxWidth(), height = 44.dp)
                }
            }
        }

        // Keep the last selection around so the card doesn't blank out during its exit animation.
        var lastSelection by remember { mutableStateOf<MapSelection?>(null) }
        if (state.selection != null) lastSelection = state.selection
        AnimatedVisibility(
            visible = state.selection != null && routeUi == null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(250)) { it / 2 } + fadeIn(tween(250)),
            exit = slideOutVertically(tween(200)) { it / 2 } + fadeOut(tween(200)),
        ) {
            val cardModifier = Modifier.padding(horizontal = 16.dp).padding(bottom = BottomBarSpace + 8.dp)
            when (val sel = lastSelection) {
                is MapSelection.Own -> {
                    val s = sel.sighting
                    SightingPreviewCard(
                        sighting = s,
                        onDetail = { onOpenDetail(s.id) },
                        onRoute = { viewModel.startRoute(s.latitude, s.longitude, s.animalLabel) },
                        onFavorite = { viewModel.toggleFavorite(s.id) },
                        modifier = cardModifier,
                    )
                }
                is MapSelection.Community -> {
                    val s = sel.sighting
                    CommunityPreviewCard(
                        sighting = s,
                        onDetail = { onOpenCommunity(s.id) },
                        onRoute = { viewModel.startRoute(s.latitude, s.longitude, s.animalLabel) },
                        modifier = cardModifier,
                    )
                }
                is MapSelection.Live -> LiveUserCard(
                    user = sel.user,
                    trip = state.sharedRoutes.firstOrNull { it.userId == sel.user.userId },
                    here = state.lastFix,
                    onRoute = { viewModel.startRoute(sel.user.latitude, sel.user.longitude, sel.user.name) },
                    modifier = cardModifier,
                )
                is MapSelection.Pin -> PinCard(
                    pin = sel.pin,
                    mine = sel.mine,
                    here = state.lastFix,
                    onRoute = { viewModel.startRoute(sel.pin.latitude, sel.pin.longitude, sel.pin.displayTitle) },
                    onEdit = { editingPin = sel.pin },
                    onDelete = { viewModel.deletePin(sel.pin.id) },
                    modifier = cardModifier,
                )
                null -> Unit
            }
        }

        // Place picked in search; its card makes way for a selected marker, a cluster list or a route.
        var lastPlace by remember { mutableStateOf<SearchedPlace?>(null) }
        if (place != null) lastPlace = place
        AnimatedVisibility(
            visible = place != null && state.selection == null && clusterList == null && routeUi == null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(250)) { it / 2 } + fadeIn(tween(250)),
            exit = slideOutVertically(tween(200)) { it / 2 } + fadeOut(tween(200)),
        ) {
            lastPlace?.let { p ->
                SearchedPlaceCard(
                    place = p,
                    here = state.lastFix,
                    onRoute = { viewModel.startRoute(p.latitude, p.longitude, p.name) },
                    onClose = viewModel::clearPlace,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = BottomBarSpace + 8.dp),
                )
            }
        }
    }

    AnimatedVisibility(
        visible = routeUi != null && !navigating,
        enter = slideInVertically(tween(250)) { it / 2 } + fadeIn(tween(250)),
        exit = slideOutVertically(tween(200)) { it / 2 } + fadeOut(tween(200)),
    ) {
        routeUi?.let { r ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                RouteCard(
                    route = r,
                    onMode = viewModel::setRouteMode,
                    onRefresh = viewModel::refreshRoute,
                    onGrant = { locationPermission.request() },
                    onStart = viewModel::startNavigation,
                    onClose = viewModel::clearRoute,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = BottomBarSpace + 8.dp),
                )
            }
        }
    }

    // Turn-by-turn: next manoeuvre on top, trip summary at the bottom.
    AnimatedVisibility(
        visible = navigating,
        enter = fadeIn(tween(250)), exit = fadeOut(tween(200)),
    ) {
        routeUi?.let { r ->
            Box(Modifier.fillMaxSize()) {
                if (!r.arrived) {
                    NavigationBanner(r, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp))
                }
                Column(
                    Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    MapRoundIconButton(
                        if (r.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                        if (r.muted) "Nyalakan suara panduan" else "Matikan suara panduan",
                        { viewModel.setMuted(!r.muted) },
                    )
                    if (hasSensor) {
                        OrientationToggle(on = useDeviceHeading, onClick = { useDeviceHeading = !useDeviceHeading })
                    }
                    if (!following) {
                        MapRoundIconButton(FaunaryIcons.Crosshair, "Ikuti posisiku", { following = true })
                    }
                }
                NavigationPanel(
                    route = r,
                    onEnd = viewModel::stopNavigation,
                    onDone = viewModel::clearRoute,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 12.dp),
                )
            }
        }
    }

    // Tapped cluster: list what's inside (photos taken at the same spot never split apart by zooming).
    val cluster = clusterList
    AnimatedVisibility(
        visible = cluster != null && state.selection == null && routeUi == null,
        enter = slideInVertically(tween(250)) { it / 2 } + fadeIn(tween(250)),
        exit = slideOutVertically(tween(200)) { it / 2 } + fadeOut(tween(200)),
    ) {
        if (cluster != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                ClusterListCard(
                    list = cluster,
                    onPick = { sel ->
                        clusterList = null
                        viewModel.select(sel.key)
                    },
                    onZoom = {
                        clusterList = null
                        // Frame every photo of the stack (a single spot zooms right in).
                        controller.fit(cluster.items.map { it.latLng })
                    },
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = BottomBarSpace + 8.dp),
                )
            }
        }
    }

    AnimatedVisibility(
        visible = searchOpen && !navigating,
        enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 12 },
        exit = fadeOut(tween(150)),
    ) {
        MapSearchOverlay(
            search = search,
            recent = recentPlaces,
            here = state.lastFix,
            onQuery = { q ->
                val center = controller.navigationCamera()?.let { GeoPoint(it.latitude, it.longitude) }
                viewModel.setSearchQuery(q, center)
            },
            onPickPlace = { viewModel.pickPlace(it, showPlace) },
            onPickRecent = { viewModel.showPlace(it, showPlace) },
            onPickCoordinates = { point ->
                val label = Format.coordinates(point.latitude, point.longitude)
                viewModel.showPlace(
                    SearchedPlace(id = "coord:$label", name = label, type = PlaceType.COORDINATES, latitude = point.latitude, longitude = point.longitude),
                    showPlace,
                )
            },
            onPickOnMap = { sel ->
                searchOpen = false
                clusterList = null
                viewModel.showOnMap(sel)
                val (lat, lng) = sel.latLng
                controller.flyTo(lat, lng, 16.0, cardPaddingPx)
            },
            onClearRecent = viewModel::clearRecentPlaces,
            onClose = { searchOpen = false },
        )
    }

    if (state.online && !state.settings.publicNoticeSeen) {
        PublicNoticeDialog(onAcknowledge = viewModel::acknowledgePublicNotice)
    }

    editingPin?.let { pin ->
        PinFormDialog(
            pin = pin,
            onSave = { title, note, icon, onDone ->
                viewModel.savePinDetails(pin.id, title, note, icon) { ok ->
                    onDone()
                    if (ok) editingPin = null
                }
            },
            onDelete = {
                viewModel.deletePin(pin.id)
                editingPin = null
            },
            onDismiss = { editingPin = null },
        )
    }

    val message by viewModel.message.collectAsStateWithLifecycle()
    val showMessage = rememberShowMessage()
    LaunchedEffect(message) {
        message?.let {
            showMessage(it)
            viewModel.messageShown()
        }
    }
}

@Composable
private fun SightingPreviewCard(
    sighting: AnimalSighting,
    onDetail: () -> Unit,
    onRoute: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = FaunaryTheme.colors
    FaunaryCard(modifier.fillMaxWidth(), onClick = onDetail, shape = RoundedCornerShape(26.dp)) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(c.border))
        Spacer(Modifier.height(12.dp))
        Row {
            Box {
                PhotoThumb(sighting.photoPath, Modifier.size(104.dp), RoundedCornerShape(18.dp), sighting.animalLabel)
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(c.surface.copy(alpha = 0.92f))
                        .clickable(onClick = onFavorite),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (sighting.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        "Favorit", Modifier.size(16.dp), tint = c.primary,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(sighting.animalCategory.displayName, color = c.badgeNature, icon = sighting.animalCategory.icon)
                    if (sighting.isAiDetected) {
                        Pill("${Format.percent(sighting.confidence)} cocok", icon = Icons.Rounded.AutoAwesome)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(sighting.animalLabel, style = MaterialTheme.typography.titleLarge, color = c.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                sighting.note?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = c.foregroundSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(6.dp))
                InfoRow(Icons.Rounded.LocationOn, sighting.locationName ?: Format.coordinates(sighting.latitude, sighting.longitude))
                Spacer(Modifier.height(2.dp))
                InfoRow(Icons.Rounded.Schedule, Format.relative(sighting.timestamp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FaunaryButton("Rute ke Sini", onRoute, Modifier.weight(1f), kind = ButtonKind.Secondary, icon = Icons.Rounded.Directions, height = 46.dp)
            FaunaryButton("Buka Detail", onDetail, Modifier.weight(1f), trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward, height = 46.dp)
        }
    }
}

/** 3D toggle: filled Canyon while 3D (terrain + buildings + tilt) is on. */
@Composable
private fun DimensionToggle(threeD: Boolean, onClick: () -> Unit) {
    val c = FaunaryTheme.colors
    val bg by animateColorAsState(if (threeD) c.primary else c.surface, tween(200), label = "3dBg")
    val fg by animateColorAsState(if (threeD) c.onPrimary else c.primary, tween(200), label = "3dFg")
    MapRoundButton(
        contentDescription = if (threeD) "Tampilkan peta 2D" else "Tampilkan peta 3D",
        onClick = onClick,
        background = bg,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(FaunaryIcons.Cube, null, Modifier.size(20.dp), tint = fg)
            Text("3D", style = MaterialTheme.typography.labelSmall.copy(lineHeight = 12.sp), color = fg)
        }
    }
}

@Composable
private fun CommunityPreviewCard(
    sighting: CommunitySighting,
    onDetail: () -> Unit,
    onRoute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = FaunaryTheme.colors
    FaunaryCard(modifier.fillMaxWidth(), onClick = onDetail, shape = RoundedCornerShape(26.dp)) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(c.border))
        Spacer(Modifier.height(12.dp))
        Row {
            PhotoThumb(sighting.photoUrl, Modifier.size(104.dp), RoundedCornerShape(18.dp), sighting.animalLabel)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(sighting.animalCategory.displayName, color = c.badgeInfo, icon = sighting.animalCategory.icon)
                    if (sighting.isAiDetected) Pill("${Format.percent(sighting.confidence)} cocok", icon = Icons.Rounded.AutoAwesome)
                }
                Spacer(Modifier.height(8.dp))
                Text(sighting.animalLabel, style = MaterialTheme.typography.titleLarge, color = c.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                InfoRow(FaunaryIcons.User, "Ditemukan oleh ${sighting.displayName}", color = c.info)
                Spacer(Modifier.height(4.dp))
                InfoRow(Icons.Rounded.LocationOn, sighting.locationName ?: Format.coordinates(sighting.latitude, sighting.longitude))
                Spacer(Modifier.height(2.dp))
                InfoRow(Icons.Rounded.Schedule, Format.relative(sighting.takenAtMs))
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FaunaryButton("Rute ke Sini", onRoute, Modifier.weight(1f), kind = ButtonKind.Secondary, icon = Icons.Rounded.Directions, height = 46.dp)
            FaunaryButton("Buka Detail", onDetail, Modifier.weight(1f), trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward, height = 46.dp)
        }
    }
}

@Composable
private fun LiveUserCard(user: LiveUser, trip: LiveRouteDto?, here: GeoPoint?, onRoute: () -> Unit, modifier: Modifier = Modifier) {
    val c = FaunaryTheme.colors
    FaunaryCard(modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(c.secondary.copy(alpha = 0.25f)).border(2.dp, c.secondary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(user.name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = c.foreground)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(user.name, style = MaterialTheme.typography.titleLarge, color = c.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(c.success))
                    Spacer(Modifier.width(6.dp))
                    Text("Sedang menjelajah · ${Format.relative(user.seenAt)}", style = MaterialTheme.typography.bodySmall, color = c.foregroundSecondary)
                }
                here?.let {
                    Text(
                        "${Format.distance(Geo.distanceMeters(it.latitude, it.longitude, user.latitude, user.longitude))} darimu",
                        style = MaterialTheme.typography.labelMedium, color = c.foregroundMuted,
                    )
                }
            }
        }
        // Where they're heading, when they share an active route (drawn in olive on the map).
        trip?.let { t ->
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.secondary.copy(alpha = 0.14f)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (t.mode == TravelMode.DRIVING.profile) Icons.Rounded.DirectionsCar else Icons.AutoMirrored.Rounded.DirectionsWalk,
                    null, Modifier.size(20.dp), tint = c.brand,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Sedang menuju", style = MaterialTheme.typography.labelMedium, color = c.foregroundSecondary)
                    Text(t.destLabel, style = MaterialTheme.typography.titleSmall, color = c.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                t.remainingMeters?.let {
                    Text("${Format.distance(it.toDouble())} lagi", style = MaterialTheme.typography.labelLarge, color = c.brand)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        FaunaryButton("Rute ke Sini", onRoute, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, icon = Icons.Rounded.Directions, height = 46.dp)
    }
}

/** A shared marker: who placed it, how far it is, a route there, and delete for its creator. */
@Composable
private fun PinCard(
    pin: MapPin,
    mine: Boolean,
    here: GeoPoint?,
    onRoute: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = FaunaryTheme.colors
    val icon = PinIcon.fromKey(pin.icon)
    var confirmDelete by remember { mutableStateOf(false) }
    FaunaryCard(modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(icon.color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon.glyph(), icon.label, Modifier.size(26.dp), tint = icon.color)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(pin.displayTitle, style = MaterialTheme.typography.titleLarge, color = c.foreground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        mine -> "Penandamu"
                        pin.isDraft -> "Baru di-deploy oleh ${pin.displayName} · belum diberi nama"
                        else -> "Ditandai oleh ${pin.displayName}"
                    },
                    style = MaterialTheme.typography.bodySmall, color = c.foregroundSecondary,
                )
                here?.let {
                    Text(
                        "${Format.distance(Geo.distanceMeters(it.latitude, it.longitude, pin.latitude, pin.longitude))} darimu",
                        style = MaterialTheme.typography.labelMedium, color = c.foregroundMuted,
                    )
                }
            }
        }
        pin.note?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = c.foreground, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(14.dp))
        FaunaryButton("Rute ke Sini", onRoute, Modifier.fillMaxWidth(), icon = Icons.Rounded.Directions, height = 46.dp)
        if (mine) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FaunaryButton("Ubah", onEdit, Modifier.weight(1f), kind = ButtonKind.Secondary, icon = Icons.Rounded.Edit, height = 44.dp)
                FaunaryButton("Hapus", { confirmDelete = true }, Modifier.weight(1f), kind = ButtonKind.Ghost, icon = Icons.Rounded.DeleteOutline, height = 44.dp)
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = c.surface,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Hapus penanda?", color = c.foreground) },
            text = { Text("\u201C${pin.displayTitle}\u201D akan hilang dari peta semua pengguna.", color = c.foregroundSecondary) },
            confirmButton = {
                FaunaryButton(
                    "Hapus",
                    {
                        confirmDelete = false
                        onDelete()
                    },
                    kind = ButtonKind.Danger, height = 44.dp,
                )
            },
            dismissButton = { FaunaryButton("Batal", { confirmDelete = false }, kind = ButtonKind.Ghost, height = 44.dp) },
        )
    }
}

/**
 * Details of a deployed marker: name, note and icon. It's already on everyone's map; saving updates
 * it there in real time. Also used to edit an existing marker.
 */
@Composable
private fun PinFormDialog(
    pin: MapPin,
    onSave: (title: String, note: String, icon: PinIcon, onDone: () -> Unit) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = FaunaryTheme.colors
    val copyText = rememberCopyText()
    var title by rememberSaveable(pin.id) { mutableStateOf(pin.title.orEmpty()) }
    var note by rememberSaveable(pin.id) { mutableStateOf(pin.note.orEmpty()) }
    var icon by rememberSaveable(pin.id) { mutableStateOf(PinIcon.fromKey(pin.icon)) }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val coordinates = Format.coordinates(pin.latitude, pin.longitude)

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = !saving),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .imePadding()
                .clip(RoundedCornerShape(32.dp))
                .background(c.surface),
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 22.dp),
            ) {
                // Header
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(PinHeaderGreen.copy(alpha = 0.14f)).align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Flag, null, Modifier.size(28.dp), tint = PinHeaderGreen)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    if (pin.isDraft) "Tandai lokasi ini" else "Ubah penanda",
                    style = MaterialTheme.typography.headlineSmall, color = c.foreground,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (pin.isDraft) "Penanda sudah ter-deploy dan terlihat oleh semua pengguna. Lengkapi infonya agar bisa dipakai sebagai tujuan rute."
                    else "Perubahan langsung terlihat oleh semua pengguna.",
                    style = MaterialTheme.typography.bodyMedium, color = c.foregroundSecondary,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))

                // Coordinates + copy
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surfaceMuted).padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.LocationOn, null, Modifier.size(26.dp), tint = c.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Koordinat", style = MaterialTheme.typography.labelMedium, color = c.foregroundSecondary)
                        Text(coordinates, style = MaterialTheme.typography.titleSmall, color = c.foreground)
                    }
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.surface)
                            .border(1.dp, c.border, RoundedCornerShape(12.dp))
                            .clickable(onClickLabel = "Salin koordinat") {
                                copyText(coordinates, "Koordinat disalin")
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.ContentCopy, "Salin koordinat", Modifier.size(18.dp), tint = c.foreground)
                    }
                }
                Spacer(Modifier.height(18.dp))

                // Name
                PinFieldLabel(Icons.Rounded.Signpost, "Nama penanda", required = true)
                Spacer(Modifier.height(8.dp))
                FaunaryTextField(
                    title, { title = it.take(PIN_TITLE_MAX) }, "Mis. Sarang elang",
                    imeAction = ImeAction.Next,
                    trailing = if (title.isNotEmpty()) {
                        {
                            Icon(
                                Icons.Rounded.Close, "Hapus nama",
                                Modifier.size(20.dp).clip(CircleShape).clickable { title = "" },
                                tint = c.foregroundSecondary,
                            )
                        }
                    } else null,
                )
                PinCounter(title.length, PIN_TITLE_MAX)

                // Note
                PinFieldLabel(Icons.Rounded.Description, "Catatan (opsional)")
                Spacer(Modifier.height(8.dp))
                FaunaryTextField(
                    note, { note = it.take(PIN_NOTE_MAX) }, "Tambahkan informasi tentang lokasi ini…",
                    singleLine = false, minLines = 3, imeAction = ImeAction.Default,
                )
                PinCounter(note.length, PIN_NOTE_MAX)

                // Icon
                PinFieldLabel(Icons.Rounded.LocationOn, "Ikon penanda")
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PinIcon.entries.forEach { option ->
                        val selected = option == icon
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(option.color.copy(alpha = if (selected) 0.2f else 0.1f))
                                .border(if (selected) 2.dp else 0.dp, if (selected) option.color else Color.Transparent, RoundedCornerShape(16.dp))
                                .selectable(selected = selected, role = Role.RadioButton) { icon = option }
                                .semantics { contentDescription = option.label },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(option.glyph(), null, Modifier.size(24.dp), tint = option.color)
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))

                // Actions
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FaunaryButton("Batal", onDismiss, Modifier.weight(1f), kind = ButtonKind.Ghost, enabled = !saving, height = 52.dp)
                    FaunaryButton(
                        if (saving) "Menyimpan\u2026" else "Simpan",
                        {
                            saving = true
                            onSave(title, note, icon) { saving = false }
                        },
                        Modifier.weight(1.2f),
                        icon = Icons.Rounded.Flag,
                        enabled = title.isNotBlank() && !saving,
                        height = 52.dp,
                    )
                }
                // Unused or dropped by mistake: markers can go at any time, named or not.
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.align(Alignment.CenterHorizontally).clip(CircleShape)
                        .clickable(enabled = !saving, role = Role.Button) { confirmDelete = true }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(18.dp), tint = c.danger)
                    Spacer(Modifier.width(6.dp))
                    Text("Hapus penanda", style = MaterialTheme.typography.labelLarge, color = c.danger)
                }
            }
            Box(
                Modifier.align(Alignment.TopEnd).padding(14.dp).size(40.dp).clip(CircleShape)
                    .background(c.surfaceMuted).clickable(enabled = !saving, onClickLabel = "Tutup", onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, "Tutup", Modifier.size(20.dp), tint = c.foreground)
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = c.surface,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Hapus penanda?", color = c.foreground) },
            text = { Text("Penanda di titik ini akan hilang dari peta semua pengguna.", color = c.foregroundSecondary) },
            confirmButton = {
                FaunaryButton(
                    "Hapus",
                    {
                        confirmDelete = false
                        onDelete()
                    },
                    kind = ButtonKind.Danger, height = 44.dp,
                )
            },
            dismissButton = { FaunaryButton("Batal", { confirmDelete = false }, kind = ButtonKind.Ghost, height = 44.dp) },
        )
    }
}

@Composable
private fun PinFieldLabel(icon: ImageVector, text: String, required: Boolean = false) {
    val c = FaunaryTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = c.primary)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = c.foreground)
        if (required) Text(" *", style = MaterialTheme.typography.labelLarge, color = c.primary)
    }
}

@Composable
private fun PinCounter(length: Int, max: Int) {
    val c = FaunaryTheme.colors
    Text(
        "$length/$max",
        style = MaterialTheme.typography.labelSmall, color = c.foregroundMuted,
        textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
    )
}

/** "Deploying…" → "Deployed" / "Failed" while a marker goes live; [onUndo] adds "Undo" once it's live. */
@Composable
private fun DeployStatusChip(phase: PinDeploy.Phase, onUndo: (() -> Unit)? = null) {
    val c = FaunaryTheme.colors
    Row(
        Modifier.softShadow(CircleShape, 6.dp).clip(CircleShape).background(c.surface).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (phase) {
            PinDeploy.Phase.DEPLOYING -> CircularProgressIndicator(Modifier.size(16.dp), color = c.primary, strokeWidth = 2.dp)
            PinDeploy.Phase.DEPLOYED, PinDeploy.Phase.SETTLING -> Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp), tint = c.success)
            PinDeploy.Phase.FAILED -> Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(18.dp), tint = c.danger)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            when (phase) {
                PinDeploy.Phase.DEPLOYING -> "Men-deploy penanda\u2026"
                PinDeploy.Phase.DEPLOYED, PinDeploy.Phase.SETTLING -> "Penanda ter-deploy"
                PinDeploy.Phase.FAILED -> "Penanda gagal di-deploy"
            },
            style = MaterialTheme.typography.labelLarge, color = c.foreground,
        )
        if (onUndo != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                "Urungkan",
                style = MaterialTheme.typography.labelLarge, color = c.primary,
                modifier = Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onUndo)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

private const val PIN_TITLE_MAX = 50
private const val PIN_NOTE_MAX = 200

/** Header accent of the new-marker dialog. */
private val PinHeaderGreen = Color(0xFF3F8A7A)

@Composable
private fun PublicNoticeDialog(onAcknowledge: () -> Unit) {
    val c = FaunaryTheme.colors
    AlertDialog(
        onDismissRequest = {},
        containerColor = c.surface,
        shape = RoundedCornerShape(28.dp),
        icon = { Icon(Icons.Rounded.Public, null, tint = c.primary) },
        title = { Text("Temuanmu tampil di peta publik", color = c.foreground) },
        text = {
            Text(
                "Setiap satwa yang kamu simpan, termasuk foto, jenis hewan, catatan, dan titik lokasinya, akan terlihat oleh semua pengguna Faunary. " +
                    "Hindari memotret di rumah atau tempat pribadi. Lokasi live dan rute perjalananmu tidak dibagikan kecuali kamu menyalakannya sendiri.",
                color = c.foregroundSecondary,
            )
        },
        confirmButton = { FaunaryButton("Saya Mengerti", onAcknowledge, height = 44.dp) },
    )
}

data class ClusterList(val latitude: Double, val longitude: Double, val items: List<MapSelection>)

@Composable
private fun ClusterListCard(
    list: ClusterList,
    onPick: (MapSelection) -> Unit,
    onZoom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = FaunaryTheme.colors
    FaunaryCard(modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(c.border))
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${list.items.size} temuan di sekitar sini", style = MaterialTheme.typography.titleMedium, color = c.foreground, modifier = Modifier.weight(1f))
            Text(
                "Perbesar peta", style = MaterialTheme.typography.labelLarge, color = c.primary,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onZoom).padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
            list.items.forEach { sel ->
                val (photo, label, sub, time) = when (sel) {
                    is MapSelection.Own -> listOf(sel.sighting.photoPath, sel.sighting.animalLabel, "Temuanmu", Format.relative(sel.sighting.timestamp))
                    is MapSelection.Community -> listOf(sel.sighting.photoUrl, sel.sighting.animalLabel, "oleh ${sel.sighting.displayName}", Format.relative(sel.sighting.takenAtMs))
                    is MapSelection.Live -> listOf("", sel.user.name, "", "")
                    is MapSelection.Pin -> listOf("", sel.pin.displayTitle, "Penanda", "")
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onPick(sel) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PhotoThumb(photo, Modifier.size(52.dp), RoundedCornerShape(14.dp), label)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.titleSmall, color = c.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(sub, style = MaterialTheme.typography.bodySmall, color = if (sel is MapSelection.Community) c.info else c.primary, maxLines = 1)
                    }
                    Text(time, style = MaterialTheme.typography.labelMedium, color = c.foregroundMuted)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp), tint = c.foregroundMuted)
                }
            }
        }
    }
}

@Composable
private fun RouteCard(
    route: RouteUi,
    onMode: (TravelMode) -> Unit,
    onRefresh: () -> Unit,
    onGrant: () -> Unit,
    onStart: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = FaunaryTheme.colors
    FaunaryCard(modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Directions, background = c.highlight)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Rute ke", style = MaterialTheme.typography.labelMedium, color = c.foregroundSecondary)
                Text(route.label, style = MaterialTheme.typography.titleMedium, color = c.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            SurfaceIconButton(Icons.Rounded.Refresh, "Hitung ulang dari posisiku", onRefresh, size = 40.dp)
            Spacer(Modifier.width(8.dp))
            SurfaceIconButton(Icons.Rounded.Close, "Tutup rute", onClose, size = 40.dp)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TravelMode.entries.forEach { m ->
                SelectableChip(
                    m.label, route.mode == m, { onMode(m) },
                    icon = if (m == TravelMode.WALKING) Icons.AutoMirrored.Rounded.DirectionsWalk else Icons.Rounded.DirectionsCar,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        when {
            route.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), color = c.primary, strokeWidth = 2.5.dp)
                Spacer(Modifier.width(10.dp))
                Text("Menghitung rute…", style = MaterialTheme.typography.bodyMedium, color = c.foregroundSecondary)
            }
            route.route != null -> Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(Format.duration(route.route.durationSeconds), style = MaterialTheme.typography.headlineMedium, color = c.foreground)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        Format.distance(route.route.distanceMeters) + " · " + route.mode.label.lowercase(),
                        style = MaterialTheme.typography.bodyMedium, color = c.foregroundSecondary,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                FaunaryButton("Mulai Navigasi", onStart, Modifier.fillMaxWidth(), icon = Icons.Rounded.Navigation, height = 46.dp)
            }
            else -> Column {
                Text(route.error ?: "Rute tidak tersedia.", style = MaterialTheme.typography.bodyMedium, color = c.danger)
                if (route.needsPermission) {
                    Spacer(Modifier.height(10.dp))
                    FaunaryButton("Izinkan Lokasi", onGrant, Modifier.fillMaxWidth(), height = 44.dp)
                }
            }
        }
    }
}

/** Icon for a Mapbox manoeuvre (type + modifier). Indonesia drives on the left, so U-turns go right. */
private fun maneuverIcon(step: RouteStep?): ImageVector {
    val m = step?.modifier.orEmpty()
    val left = "left" in m
    return when {
        step == null || step.type == "arrive" -> Icons.Rounded.Flag
        step.type.startsWith("roundabout") || step.type == "rotary" -> if (left) Icons.Rounded.RoundaboutLeft else Icons.Rounded.RoundaboutRight
        step.type == "fork" -> if (left) Icons.Rounded.ForkLeft else Icons.Rounded.ForkRight
        step.type == "merge" -> Icons.Rounded.Merge
        m == "uturn" -> Icons.Rounded.UTurnRight
        m == "sharp left" -> Icons.Rounded.TurnSharpLeft
        m == "sharp right" -> Icons.Rounded.TurnSharpRight
        m == "slight left" -> Icons.Rounded.TurnSlightLeft
        m == "slight right" -> Icons.Rounded.TurnSlightRight
        m == "left" -> Icons.Rounded.TurnLeft
        m == "right" -> Icons.Rounded.TurnRight
        else -> Icons.Rounded.Straight
    }
}

/** Next manoeuvre: arrow, distance to it and the instruction. */
@Composable
private fun NavigationBanner(route: RouteUi, modifier: Modifier = Modifier) {
    val c = FaunaryTheme.colors
    val p = route.progress
    FaunaryCard(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = c.primary) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(c.onPrimary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                if (p == null || route.rerouting) {
                    CircularProgressIndicator(Modifier.size(26.dp), color = c.onPrimary, strokeWidth = 2.5.dp)
                } else {
                    Icon(maneuverIcon(p.nextStep), null, Modifier.size(36.dp), tint = c.onPrimary)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                when {
                    route.rerouting -> Text("Menghitung ulang rute…", style = MaterialTheme.typography.titleMedium, color = c.onPrimary)
                    p == null -> Text("Mencari posisimu…", style = MaterialTheme.typography.titleMedium, color = c.onPrimary)
                    else -> {
                        Text(Format.distance(p.distanceToNextStep), style = MaterialTheme.typography.headlineSmall, color = c.onPrimary)
                        Text(
                            p.nextStep?.instruction ?: "Menuju ${route.label}",
                            style = MaterialTheme.typography.bodyLarge, color = c.onPrimary.copy(alpha = 0.9f),
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Remaining time, distance and arrival clock; becomes an arrival card at the destination. */
@Composable
private fun NavigationPanel(route: RouteUi, onEnd: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val c = FaunaryTheme.colors
    val p = route.progress
    FaunaryCard(modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)) {
        if (route.arrived) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Flag, background = c.highlight)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Kamu telah tiba", style = MaterialTheme.typography.titleMedium, color = c.foreground)
                    Text(route.label, style = MaterialTheme.typography.bodyMedium, color = c.foregroundSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(14.dp))
            FaunaryButton("Selesai", onDone, Modifier.fillMaxWidth(), height = 46.dp)
            return@FaunaryCard
        }
        val seconds = p?.remainingSeconds ?: route.route?.durationSeconds ?: 0.0
        val meters = p?.remainingMeters ?: route.route?.distanceMeters ?: 0.0
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Format.duration(seconds), style = MaterialTheme.typography.headlineMedium, color = c.foreground)
                Text(
                    "${Format.distance(meters)} · tiba ${Format.time(currentTimeMillis() + (seconds * 1000).toLong())}",
                    style = MaterialTheme.typography.bodyMedium, color = c.foregroundSecondary,
                )
            }
            FaunaryButton("Akhiri", onEnd, kind = ButtonKind.Danger, icon = Icons.Rounded.Close, height = 46.dp)
        }
    }
}

/** Phone lying flat (0°) → moderate 3D view; held upright (90°) → looking far ahead. Never flat: navigation is 3D. */
private fun tiltToPitch(tilt: Double): Double = (45.0 + tilt / 90.0 * 30.0).coerceIn(45.0, 75.0)

/** Shortest signed turn from [from] to [to], in degrees (-180…180). */
private fun angleDelta(from: Double, to: Double): Double = ((to - from + 540.0) % 360.0) - 180.0

/** Compass toggle: filled while the map follows the phone's rotation and tilt. */
@Composable
private fun OrientationToggle(on: Boolean, onClick: () -> Unit) {
    val c = FaunaryTheme.colors
    val bg by animateColorAsState(if (on) c.primary else c.surface, tween(200), label = "orientBg")
    val fg by animateColorAsState(if (on) c.onPrimary else c.primary, tween(200), label = "orientFg")
    MapRoundButton(
        contentDescription = if (on) "Arah peta mengikuti rute" else "Arah peta mengikuti gerakan HP",
        onClick = onClick,
        background = bg,
    ) {
        Icon(Icons.Rounded.Explore, null, Modifier.size(24.dp), tint = fg)
    }
}

/**
 * Top of the map: one floating card that is also the search bar (tap to search places, addresses
 * and finds), with a live status line (GPS accuracy + how many finds are on the map, following the
 * active filter) and the notification bell.
 */
@Composable
private fun MapHeader(
    /** Name of the place picked in search, shown in the bar instead of the prompt. */
    searchText: String?,
    onSearch: () -> Unit,
    gpsAccuracy: Float?,
    visibleCount: Int,
    filter: AnimalCategory?,
    showBell: Boolean,
    unread: Int,
    onBell: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = FaunaryTheme.colors
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier
            .fillMaxWidth()
            .softShadow(shape, 6.dp)
            .clip(shape)
            .background(c.surface)
            .clickable(onClickLabel = "Cari di peta", role = Role.Button, onClick = onSearch)
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(c.primary), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Pets, null, Modifier.size(24.dp), tint = c.onPrimary)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, Modifier.size(18.dp), tint = if (searchText != null) c.primary else c.foregroundMuted)
                Spacer(Modifier.width(6.dp))
                Text(
                    searchText ?: "Cari tempat, alamat, satwa…",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (searchText != null) c.foreground else c.foregroundSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val gpsOn = gpsAccuracy != null
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (gpsOn) c.success else c.foregroundMuted))
                Spacer(Modifier.width(5.dp))
                val what = filter?.displayName?.lowercase() ?: "temuan"
                Text(
                    (if (gpsOn) "GPS ${gpsAccuracy.roundToInt()}m" else "GPS mati") + " · $visibleCount $what di peta",
                    style = MaterialTheme.typography.labelMedium, color = c.foregroundSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showBell) {
            Spacer(Modifier.width(8.dp))
            HeaderBell(unread, onBell)
        }
    }
}

/**
 * Bell inside the header card; the badge shows unread likes/comments. While there's something unread
 * the bell rings (a short damped swing) now and then, and right away when a new notification arrives.
 */
@Composable
private fun HeaderBell(unread: Int, onClick: () -> Unit) {
    val c = FaunaryTheme.colors
    val swing = remember { Animatable(0f) }
    val badgeScale = remember { Animatable(if (unread > 0) 1f else 0f) }
    var shownCount by remember { mutableStateOf(unread) }
    LaunchedEffect(unread) {
        val grew = unread > 0 && (badgeScale.value == 0f || unread > shownCount)
        if (unread > 0) shownCount = unread // keep the last number while the badge shrinks away
        launch {
            when {
                unread == 0 -> badgeScale.animateTo(0f, tween(160))
                grew || badgeScale.value < 1f -> {
                    // Pop in / bump for a new notification.
                    badgeScale.snapTo(if (badgeScale.value == 0f) 0.3f else 1f)
                    badgeScale.animateTo(1.25f, tween(140))
                    badgeScale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
                }
            }
        }
        if (unread == 0) {
            swing.animateTo(0f, tween(150))
            return@LaunchedEffect
        }
        while (isActive) {
            for (angle in BELL_RING) swing.animateTo(angle, tween(85, easing = FastOutSlowInEasing))
            delay(BELL_RING_PAUSE_MS)
        }
    }
    Box {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(c.surfaceMuted).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                FaunaryIcons.Bell,
                if (unread > 0) "Notifikasi, $unread belum dibaca" else "Notifikasi",
                Modifier.size(24.dp).graphicsLayer {
                    // Swings from the top, like a real bell hanging from its loop.
                    transformOrigin = TransformOrigin(0.5f, 0.12f)
                    rotationZ = swing.value
                },
                tint = c.brand,
            )
        }
        if (badgeScale.value > 0f) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp)
                    .graphicsLayer { scaleX = badgeScale.value; scaleY = badgeScale.value }
                    .border(2.dp, c.surface, CircleShape)
                    .heightIn(min = 20.dp).clip(CircleShape).background(c.primary).padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (shownCount > 99) "99+" else "$shownCount", style = MaterialTheme.typography.labelSmall, color = c.onPrimary)
            }
        }
    }
}

/** Bell swing angles (degrees) for one ring: a damped back-and-forth. */
private val BELL_RING = listOf(18f, -16f, 12f, -9f, 5f, -2f, 0f)
private const val BELL_RING_PAUSE_MS = 3_000L

/**
 * Compact filter chip for the map: category icon in a small disc, name and count. Semi-transparent so the
 * map shows through; filled Canyon when selected, faded when that category has no finds.
 */
@Composable
private fun CategoryFilterChip(label: String, icon: ImageVector, count: Int, selected: Boolean, onClick: () -> Unit) {
    val c = FaunaryTheme.colors
    val bg by animateColorAsState(if (selected) c.primary else c.surface.copy(alpha = 0.92f), tween(200), label = "catBg")
    val fg by animateColorAsState(if (selected) c.onPrimary else c.foreground, tween(200), label = "catFg")
    val empty = count == 0 && !selected
    Row(
        Modifier
            .softShadow(CircleShape, if (selected) 5.dp else 2.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(role = Role.Tab, onClick = onClick)
            .height(36.dp)
            .padding(start = 4.dp, end = 12.dp)
            .alpha(if (empty) 0.55f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(if (selected) c.onPrimary.copy(alpha = 0.2f) else c.surfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(17.dp), tint = if (selected) fg else c.brand)
        }
        Spacer(Modifier.width(7.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
        Spacer(Modifier.width(6.dp))
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) c.onPrimary.copy(alpha = 0.85f) else c.foregroundMuted,
        )
    }
}
