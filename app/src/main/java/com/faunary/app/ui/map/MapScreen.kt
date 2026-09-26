package com.faunary.app.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.animation.animateColorAsState
import com.faunary.app.ui.components.FaunaryIcons
import androidx.compose.ui.unit.sp
import com.faunary.app.ui.components.MapRoundButton
import com.faunary.app.ui.components.MapRoundIconButton
import com.faunary.app.ui.components.MapZoomControl
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import com.faunary.app.location.DeviceOrientation
import com.faunary.app.location.RouteStep
import com.faunary.app.location.deviceOrientation
import com.faunary.app.location.hasOrientationSensor
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import kotlin.math.exp
import com.faunary.app.location.TravelMode
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.ForkLeft
import androidx.compose.material.icons.rounded.ForkRight
import androidx.compose.material.icons.rounded.Merge
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.RoundaboutLeft
import androidx.compose.material.icons.rounded.RoundaboutRight
import androidx.compose.material.icons.rounded.Straight
import androidx.compose.material.icons.rounded.TurnLeft
import androidx.compose.material.icons.rounded.TurnRight
import androidx.compose.material.icons.rounded.TurnSharpLeft
import androidx.compose.material.icons.rounded.TurnSharpRight
import androidx.compose.material.icons.rounded.TurnSlightLeft
import androidx.compose.material.icons.rounded.TurnSlightRight
import androidx.compose.material.icons.rounded.UTurnRight
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import com.faunary.app.ui.components.IconBadge
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.faunary.app.location.GeoPoint
import com.faunary.app.remote.CommunitySighting
import com.faunary.app.util.Geo
import com.faunary.app.update.UpdateCard
import com.faunary.app.update.UpdateViewModel
import com.faunary.app.update.rememberInstallAction
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.faunary.app.data.AnimalSighting
import com.faunary.app.domain.AnimalCategory
import com.faunary.app.ui.components.ButtonKind
import com.faunary.app.ui.components.CategoryAvatar
import com.faunary.app.ui.components.FaunaryButton
import com.faunary.app.ui.components.FaunaryCard
import com.faunary.app.ui.components.InfoRow
import com.faunary.app.ui.components.LocateButton
import com.faunary.app.ui.components.PermissionCard
import com.faunary.app.ui.components.PhotoThumb
import com.faunary.app.ui.components.Pill
import com.faunary.app.ui.components.SelectableChip
import com.faunary.app.ui.components.SurfaceIconButton
import com.faunary.app.ui.components.icon
import com.faunary.app.ui.components.softShadow
import com.faunary.app.ui.theme.FaunaryTheme
import com.faunary.app.util.Format
import com.faunary.app.util.LocationPermissions
import com.faunary.app.util.rememberPermissionState

/** Launch intro: whole-globe start (a little west of Indonesia, so the globe turns on the way in). */
private val IntroGlobeCenter = GeoPoint(5.0, 70.0)
private const val INTRO_GLOBE_ZOOM = 0.6
private const val INTRO_FLIGHT_MS = 4_500L

/** Space reserved at the bottom for the floating navigation bar. */
val BottomBarSpace = 104.dp

@Composable
fun MapScreen(
    focusId: Long?,
    routeTo: Pair<Double, Double>?,
    routeLabel: String?,
    onOpenDetail: (Long) -> Unit,
    onOpenCommunity: (String) -> Unit,
    onOpenCamera: () -> Unit,
    onNavigatingChange: (Boolean) -> Unit = {},
    unreadNotifications: Int = 0,
    onOpenNotifications: () -> Unit = {},
    viewModel: MapViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
) {
    val update by updateViewModel.state.collectAsStateWithLifecycle()
    val updateDismissed by updateViewModel.bannerDismissed.collectAsStateWithLifecycle()
    val installUpdate = rememberInstallAction(updateViewModel)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val routeUi by viewModel.route.collectAsStateWithLifecycle()
    val c = FaunaryTheme.colors
    val density = LocalDensity.current
    val controller = rememberFaunaMapController()
    val cardPaddingPx = with(density) { 260.dp.toPx().toDouble() }

    var locationDismissed by rememberSaveable { mutableStateOf(false) }
    var clusterList by remember { mutableStateOf<ClusterList?>(null) }
    val locationPermission = rememberPermissionState(LocationPermissions) { granted ->
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
        onNavigatingChange(navigating)
    }
    DisposableEffect(Unit) { onDispose { onNavigatingChange(false) } }
    val view = LocalView.current
    DisposableEffect(navigating) {
        view.keepScreenOn = navigating
        onDispose { view.keepScreenOn = false }
    }
    BackHandler(enabled = navigating) { viewModel.stopNavigation() }
    // Device-orientation mode: the map turns and tilts with the phone (rotation-vector sensor,
    // i.e. gyroscope fused with accelerometer and compass). Off, or without the sensor: route direction.
    val context = LocalContext.current
    val hasSensor = remember { hasOrientationSensor(context) }
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
            deviceOrientation(context) { declinationAt }.collect { orientation.value = it }
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
            markers = state.markers,
            controller = controller,
            selectedKey = state.selection?.key,
            onMarkerClick = { key ->
                clusterList = null
                viewModel.select(key)
                state.markers.firstOrNull { it.key == key }?.let {
                    controller.flyTo(it.latitude, it.longitude, bottomPaddingPx = cardPaddingPx)
                }
            },
            onCameraIdle = viewModel::onCameraIdle,
            route = if (navigating) routeUi?.remaining ?: routeUi?.route?.points else routeUi?.route?.points,
            fitRoute = !navigating,
            heading = orientation,
            onUserPan = { if (navigating) following = false },
            routeTopPadding = 190.dp,
            routeBottomPadding = BottomBarSpace + 250.dp,
            onMapClick = {
                viewModel.select(null)
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
            ornamentBottomPadding = if (state.selection != null || clusterList != null || routeUi != null) 0.dp else BottomBarSpace,
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
            AnimatedVisibility(
                visible = update.hasUpdate && !updateDismissed && (update.ready || update.downloading || update.waitingForWifi),
                enter = fadeIn(tween(200)), exit = fadeOut(tween(150)),
            ) {
                UpdateCard(
                    state = update,
                    onInstall = installUpdate,
                    onDownloadNow = updateViewModel::downloadNow,
                    onDismiss = { updateViewModel.bannerDismissed.value = true },
                    modifier = Modifier.padding(start = 16.dp, end = 76.dp, top = 12.dp),
                )
            }
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
                visible = !locationPermission.granted && !locationDismissed && state.selection == null && clusterList == null && routeUi == null,
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
                visible = state.loaded && state.all.isEmpty() && routeUi == null && (locationPermission.granted || locationDismissed),
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
                    here = state.lastFix,
                    onRoute = { viewModel.startRoute(sel.user.latitude, sel.user.longitude, sel.user.name) },
                    modifier = cardModifier,
                )
                null -> Unit
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
                        MapRoundIconButton(Icons.Rounded.MyLocation, "Ikuti posisiku", { following = true })
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

    if (state.online && !state.settings.publicNoticeSeen) {
        PublicNoticeDialog(onAcknowledge = viewModel::acknowledgePublicNotice)
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
                InfoRow(Icons.Rounded.Person, "Ditemukan oleh ${sighting.displayName}", color = c.info)
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
private fun LiveUserCard(user: LiveUser, here: GeoPoint?, onRoute: () -> Unit, modifier: Modifier = Modifier) {
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
        Spacer(Modifier.height(14.dp))
        FaunaryButton("Rute ke Sini", onRoute, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, icon = Icons.Rounded.Directions, height = 46.dp)
    }
}

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
                    "Hindari memotret di rumah atau tempat pribadi. Lokasi live-mu tidak dibagikan kecuali kamu menyalakannya sendiri.",
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
                    "${Format.distance(meters)} · tiba ${Format.time(System.currentTimeMillis() + (seconds * 1000).toLong())}",
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
 * Top of the map: one floating card with the brand, a live status line (GPS accuracy + how many
 * finds are on the map, following the active filter) and the notification bell.
 */
@Composable
private fun MapHeader(
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
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(c.primary), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Pets, null, Modifier.size(24.dp), tint = c.onPrimary)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Faunary", style = MaterialTheme.typography.titleMedium, color = c.foreground, maxLines = 1)
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

/** Bell inside the header card; the badge shows unread likes/comments. */
@Composable
private fun HeaderBell(unread: Int, onClick: () -> Unit) {
    val c = FaunaryTheme.colors
    Box {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(c.surfaceMuted).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (unread > 0) Icons.Rounded.Notifications else Icons.Rounded.NotificationsNone,
                if (unread > 0) "Notifikasi, $unread belum dibaca" else "Notifikasi",
                Modifier.size(22.dp), tint = c.brand,
            )
        }
        if (unread > 0) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp)
                    .border(2.dp, c.surface, CircleShape)
                    .heightIn(min = 20.dp).clip(CircleShape).background(c.primary).padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (unread > 99) "99+" else "$unread", style = MaterialTheme.typography.labelSmall, color = c.onPrimary)
            }
        }
    }
}

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

private val MapSelection.latLng: Pair<Double, Double>
    get() = when (this) {
        is MapSelection.Own -> sighting.latitude to sighting.longitude
        is MapSelection.Community -> sighting.latitude to sighting.longitude
        is MapSelection.Live -> user.latitude to user.longitude
    }
