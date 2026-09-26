package com.faunary.app.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.faunary.app.ui.components.FaunaryIcons
import com.faunary.app.ui.components.MapControlSize
import com.faunary.app.ui.components.MapRoundIconButton
import com.faunary.app.ui.components.softShadow
import com.faunary.app.ui.theme.FaunaryTheme

private val PointerSize = 14.dp

/** Layer switcher: own finds, community finds, live explorers + the user's own live-sharing toggle. */
@Composable
fun LayersButton(
    layers: MapLayers,
    communityCount: Int,
    liveCount: Int,
    shareLive: Boolean,
    onLayers: (MapLayers) -> Unit,
    onShareLive: (Boolean) -> Unit,
) {
    val c = FaunaryTheme.colors
    val density = LocalDensity.current
    var open by remember { mutableStateOf(false) }
    Box {
        MapRoundIconButton(Icons.Rounded.Layers, "Lapisan peta", { open = true })
        if (liveCount > 0) {
            Text(
                "$liveCount",
                style = MaterialTheme.typography.labelSmall,
                color = c.onPrimary,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp)
                    .clip(CircleShape).background(c.secondary).padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        if (open) {
            // Anchored under the button, right edges aligned; the pointer sits under the button centre.
            val y = with(density) { (MapControlSize + 6.dp).roundToPx() }
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, y),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                val visible = remember { MutableTransitionState(false) }
                LaunchedEffect(Unit) { visible.targetState = true }
                AnimatedVisibility(
                    visibleState = visible,
                    enter = fadeIn(tween(180)) + scaleIn(tween(200), initialScale = 0.92f, transformOrigin = TransformOrigin(0.92f, 0f)),
                    exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.95f, transformOrigin = TransformOrigin(0.92f, 0f)),
                ) {
                    LayersPanel(layers, communityCount, liveCount, shareLive, onLayers, onShareLive)
                }
            }
        }
    }
}

@Composable
private fun LayersPanel(
    layers: MapLayers,
    communityCount: Int,
    liveCount: Int,
    shareLive: Boolean,
    onLayers: (MapLayers) -> Unit,
    onShareLive: (Boolean) -> Unit,
) {
    val c = FaunaryTheme.colors
    // Small top-end corner so the controls under the button don't peek through the curve.
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 16.dp, bottomEnd = 28.dp, bottomStart = 28.dp)
    // Nearly full width (16 dp gutters) so titles fit even with large system font sizes.
    val panelWidth = (LocalConfiguration.current.screenWidthDp.dp - 32.dp).coerceAtMost(420.dp)
    Column(Modifier.width(panelWidth), horizontalAlignment = Alignment.End) {
        // Pointer toward the layers button (centred under a MapControlSize-wide button).
        Canvas(
            Modifier
                .padding(end = MapControlSize / 2 - PointerSize / 2)
                .size(width = PointerSize, height = PointerSize / 2 + 1.dp),
        ) {
            val p = Path().apply {
                moveTo(size.width / 2, 0f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(p, c.surface)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .softShadow(shape, 16.dp, floating = true)
                .clip(shape)
                .background(c.surface)
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp, bottom = 16.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 4.dp).clip(CircleShape).background(c.border))
            Spacer(Modifier.height(14.dp))

            // Header
            Row(verticalAlignment = Alignment.Top) {
                IconCircle(Icons.Rounded.Layers, c.brand, c.primary.copy(alpha = 0.12f))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Tampilkan di peta", style = MaterialTheme.typography.titleMedium, color = c.foreground)
                    Text(
                        "Pilih jenis titik yang ingin ditampilkan pada peta.",
                        style = MaterialTheme.typography.bodySmall, color = c.foregroundSecondary,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "Pilih Semua",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.primary.copy(alpha = 0.12f))
                        .clickable(role = Role.Button) { onLayers(MapLayers(own = true, community = true, live = true)) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
            Spacer(Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LayerRow(
                    dot = c.primary, icon = Icons.Rounded.Pets, iconTint = c.primary, iconBg = c.primary.copy(alpha = 0.12f),
                    title = "Temuan saya", count = null, subtitle = "Foto hewan yang kamu unggah",
                    checked = layers.own,
                ) { onLayers(layers.copy(own = it)) }
                LayerRow(
                    dot = c.info, icon = Icons.Rounded.Groups, iconTint = c.info, iconBg = c.info.copy(alpha = 0.15f),
                    title = "Temuan komunitas", count = communityCount, subtitle = "Foto hewan dari pengguna lain",
                    checked = layers.community,
                ) { onLayers(layers.copy(community = it)) }
                LayerRow(
                    dot = c.secondary, icon = FaunaryIcons.Binoculars, iconTint = c.brand, iconBg = c.secondary.copy(alpha = 0.18f),
                    title = "Penjelajah online", count = liveCount, subtitle = "Pengguna yang sedang online",
                    checked = layers.live,
                ) { onLayers(layers.copy(live = it)) }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = c.border)
            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(role = Role.Switch) { onShareLive(!shareLive) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconCircle(Icons.Rounded.NearMe, c.primary, c.primary.copy(alpha = 0.12f))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Bagikan lokasi live saya", style = MaterialTheme.typography.titleSmall, color = c.foreground)
                    Text("Hanya saat aplikasi terbuka", style = MaterialTheme.typography.bodySmall, color = c.foregroundSecondary)
                }
                FaunarySwitch(shareLive, onShareLive)
            }
        }
    }
}

@Composable
private fun LayerRow(
    dot: Color,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    count: Int?,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val c = FaunaryTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, c.border, shape)
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(iconBg), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = iconTint)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = c.foreground, modifier = Modifier.weight(1f, fill = false))
                if (count != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "($count)",
                        style = MaterialTheme.typography.labelMedium, color = c.foregroundSecondary,
                        modifier = Modifier.clip(CircleShape).background(c.surfaceMuted).padding(horizontal = 7.dp, vertical = 1.dp),
                    )
                }
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.foregroundSecondary)
        }
        Spacer(Modifier.width(8.dp))
        FaunarySwitch(checked, onChange)
    }
}

@Composable
private fun IconCircle(icon: ImageVector, tint: Color, background: Color) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(22.dp), tint = tint)
    }
}

@Composable
private fun FaunarySwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = FaunaryTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White, checkedTrackColor = c.primary, checkedBorderColor = c.primary,
            uncheckedThumbColor = Color.White, uncheckedTrackColor = c.borderStrong, uncheckedBorderColor = c.borderStrong,
        ),
        thumbContent = null,
    )
}
