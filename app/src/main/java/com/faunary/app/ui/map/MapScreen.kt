package com.faunary.app.ui.map

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
import com.faunary.app.ui.components.GpsChip
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
import com.faunary.app.util.openDirections
import com.faunary.app.util.rememberPermissionState

/** Space reserved at the bottom for the floating navigation bar. */
val BottomBarSpace = 104.dp

@Composable
fun MapScreen(
    focusId: Long?,
    onOpenDetail: (Long) -> Unit,
    onOpenCommunity: (String) -> Unit,
    onOpenCamera: () -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = FaunaryTheme.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    val controller = rememberFaunaMapController()
    val cardPaddingPx = with(density) { 260.dp.toPx().toDouble() }

    var locationDismissed by rememberSaveable { mutableStateOf(false) }
    val locationPermission = rememberPermissionState(LocationPermissions) { granted ->
        if (granted) viewModel.refreshLocation { controller.flyTo(it.latitude, it.longitude, 15.0) }
    }

    // Centre the camera once when data first arrives: focused entry > latest entry > GPS.
    var centered by rememberSaveable { mutableStateOf(viewModel.camera != null) }
    LaunchedEffect(state.loaded) {
        if (!state.loaded || centered) return@LaunchedEffect
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
                viewModel.select(key)
                state.markers.firstOrNull { it.key == key }?.let {
                    controller.flyTo(it.latitude, it.longitude, bottomPaddingPx = cardPaddingPx)
                }
            },
            onCameraIdle = viewModel::onCameraIdle,
            onMapClick = { viewModel.select(null) },
            showUserLocation = locationPermission.granted,
            ornamentBottomPadding = if (state.selection != null) 0.dp else BottomBarSpace,
            darkTheme = c.isDark,
            threeD = state.map3D,
            initialCenter = viewModel.camera?.first ?: DefaultCenter,
            initialZoom = viewModel.camera?.second ?: 14.0,
            onCameraSnapshot = { center, zoom -> viewModel.camera = center to zoom },
            modifier = Modifier.fillMaxSize(),
        )

        // Header + filters
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .softShadow(RoundedCornerShape(18.dp), 4.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(c.surface)
                        .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(c.primary), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Pets, null, Modifier.size(20.dp), tint = c.onPrimary)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Faunary", style = MaterialTheme.typography.titleMedium, color = c.foreground)
                        Text("Peta koleksi", style = MaterialTheme.typography.labelMedium, color = c.foregroundSecondary)
                    }
                }
                Spacer(Modifier.weight(1f))
                GpsChip(state.lastFix?.accuracy?.takeIf { locationPermission.granted })
            }
            Spacer(Modifier.height(12.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    SelectableChip("Semua", state.filter == null, { viewModel.setFilter(null) }, icon = Icons.Rounded.Pets, count = state.all.size)
                }
                AnimalCategory.entries.forEach { cat ->
                    val count = state.counts[cat] ?: 0
                    if (count > 0 || cat != AnimalCategory.OTHER) {
                        item(cat.name) {
                            SelectableChip(cat.displayName, state.filter == cat, { viewModel.setFilter(if (state.filter == cat) null else cat) }, leading = cat.emoji, count = count)
                        }
                    }
                }
            }
        }

        // Map controls
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.online) {
                LayersButton(
                    layers = state.layers,
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
            Column(
                Modifier.softShadow(RoundedCornerShape(14.dp), 4.dp).clip(RoundedCornerShape(14.dp)).background(c.surface),
            ) {
                SurfaceIconButton(Icons.Rounded.Add, "Perbesar", { controller.zoomBy(1.0) })
                SurfaceIconButton(Icons.Rounded.Remove, "Perkecil", { controller.zoomBy(-1.0) })
            }
        }

        // Bottom area: permission rationale, empty hint or selected preview
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = BottomBarSpace + 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AnimatedVisibility(
                visible = !locationPermission.granted && !locationDismissed && state.selection == null,
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
                visible = state.loaded && state.all.isEmpty() && (locationPermission.granted || locationDismissed),
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
            visible = state.selection != null,
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
                        onRoute = { context.openDirections(s.latitude, s.longitude, s.animalLabel) },
                        onFavorite = { viewModel.toggleFavorite(s.id) },
                        modifier = cardModifier,
                    )
                }
                is MapSelection.Community -> {
                    val s = sel.sighting
                    CommunityPreviewCard(
                        sighting = s,
                        onDetail = { onOpenCommunity(s.id) },
                        onRoute = { context.openDirections(s.latitude, s.longitude, s.animalLabel) },
                        modifier = cardModifier,
                    )
                }
                is MapSelection.Live -> LiveUserCard(
                    user = sel.user,
                    here = state.lastFix,
                    onRoute = { context.openDirections(sel.user.latitude, sel.user.longitude, sel.user.name) },
                    modifier = cardModifier,
                )
                null -> Unit
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
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .softShadow(shape, 4.dp)
            .size(44.dp)
            .clip(shape)
            .background(if (threeD) c.primary else c.surface)
            .border(1.dp, if (threeD) c.primary else c.border, shape)
            .clickable(onClickLabel = if (threeD) "Tampilkan peta 2D" else "Tampilkan peta 3D", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "3D",
            style = MaterialTheme.typography.labelLarge,
            color = if (threeD) c.onPrimary else c.brand,
        )
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

/** Layer switcher: own finds, community finds, live explorers + the user's own live-sharing toggle. */
@Composable
private fun LayersButton(
    layers: MapLayers,
    liveCount: Int,
    shareLive: Boolean,
    onLayers: (MapLayers) -> Unit,
    onShareLive: (Boolean) -> Unit,
) {
    val c = FaunaryTheme.colors
    var open by remember { mutableStateOf(false) }
    Box {
        SurfaceIconButton(Icons.Rounded.Layers, "Lapisan peta", { open = true })
        if (liveCount > 0) {
            Text(
                "$liveCount",
                style = MaterialTheme.typography.labelSmall,
                color = c.onPrimary,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp)
                    .clip(CircleShape).background(c.secondary).padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        DropdownMenu(open, { open = false }, shape = RoundedCornerShape(18.dp), containerColor = c.surface) {
            Text("Tampilkan di peta", style = MaterialTheme.typography.labelMedium, color = c.foregroundMuted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            LayerItem("Temuan saya", c.primary, layers.own) { onLayers(layers.copy(own = it)) }
            LayerItem("Temuan komunitas", c.info, layers.community) { onLayers(layers.copy(community = it)) }
            LayerItem("Penjelajah online ($liveCount)", c.secondary, layers.live) { onLayers(layers.copy(live = it)) }
            HorizontalDivider(color = c.border)
            DropdownMenuItem(
                text = {
                    Column {
                        Text("Bagikan lokasi live saya", color = c.foreground)
                        Text("Hanya saat aplikasi terbuka", style = MaterialTheme.typography.bodySmall, color = c.foregroundSecondary)
                    }
                },
                trailingIcon = {
                    Switch(
                        checked = shareLive,
                        onCheckedChange = onShareLive,
                        colors = SwitchDefaults.colors(checkedThumbColor = c.onPrimary, checkedTrackColor = c.primary),
                    )
                },
                onClick = { onShareLive(!shareLive) },
            )
        }
    }
}

@Composable
private fun LayerItem(label: String, dot: androidx.compose.ui.graphics.Color, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = FaunaryTheme.colors
    DropdownMenuItem(
        text = { Text(label, color = c.foreground) },
        leadingIcon = { Box(Modifier.size(12.dp).clip(CircleShape).background(dot)) },
        trailingIcon = {
            Checkbox(checked, onChange, colors = CheckboxDefaults.colors(checkedColor = c.primary, checkmarkColor = c.onPrimary))
        },
        onClick = { onChange(!checked) },
    )
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
