package com.faunary.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.faunary.app.ui.platform.rememberOpenAppSettings
import com.faunary.app.ui.theme.FaunaryTheme
import com.faunary.app.ui.theme.Radius
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class MainTab(val label: String, val icon: ImageVector) {
    Map("Peta", FaunaryIcons.Maps),
    Gallery("Galeri", FaunaryIcons.Gallery),
    Journal("Jurnal", FaunaryIcons.Journal),
    Profile("Profil", FaunaryIcons.User),
}

/** Floating bottom navigation with the raised Canyon camera action in the middle. */
@Composable
fun FloatingBottomBar(
    current: MainTab?,
    onTab: (MainTab) -> Unit,
    onCamera: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = FaunaryTheme.colors
    val shape = RoundedCornerShape(Radius.xl)
    val camera = rememberTapBounce()
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .softShadow(shape, 14.dp, floating = true)
                .clip(shape)
                .background(c.surface)
                .border(1.dp, c.border, shape)
                .height(68.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(MainTab.Map, current, onTab, Modifier.weight(1f))
            NavItem(MainTab.Gallery, current, onTab, Modifier.weight(1f))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(30.dp))
                Text("Kamera", style = MaterialTheme.typography.labelSmall, color = c.foregroundSecondary)
            }
            NavItem(MainTab.Journal, current, onTab, Modifier.weight(1f))
            NavItem(MainTab.Profile, current, onTab, Modifier.weight(1f))
        }
        Box(
            Modifier
                .offset(y = (-30).dp)
                .softShadow(CircleShape, 12.dp, floating = true)
                .size(60.dp)
                .clip(CircleShape)
                .background(c.primary)
                .border(4.dp, c.surface, CircleShape)
                .clickable(
                    interactionSource = camera.interaction,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = "Ambil foto satwa",
                ) {
                    camera.bounce()
                    onCamera()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(FaunaryIcons.Camera, "Kamera", Modifier.size(26.dp).then(camera.modifier), tint = c.onPrimary)
        }
    }
}

@Composable
private fun NavItem(tab: MainTab, current: MainTab?, onTab: (MainTab) -> Unit, modifier: Modifier) {
    val c = FaunaryTheme.colors
    val selected = tab == current
    val tint by animateColorAsState(if (selected) c.primary else c.foregroundMuted, tween(200), label = "navTint")
    val pill by animateColorAsState(if (selected) c.primary.copy(alpha = 0.12f) else c.surface.copy(alpha = 0f), tween(200), label = "navPill")
    val tap = rememberTapBounce()
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(interactionSource = tap.interaction, indication = null, role = Role.Tab) {
                tap.bounce()
                onTab(tab)
            }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.clip(CircleShape).background(pill).padding(horizontal = 14.dp, vertical = 3.dp)) {
            Icon(tab.icon, null, Modifier.size(22.dp).then(tap.modifier), tint = tint)
        }
        Spacer(Modifier.height(2.dp))
        Text(tab.label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

/** Light press feedback for bar icons: sinks while held, then a small springy pop on tap. */
private class TapBounce(
    val interaction: MutableInteractionSource,
    private val pop: Animatable<Float, *>,
    private val pressedScale: State<Float>,
    private val scope: CoroutineScope,
) {
    val modifier: Modifier = Modifier.graphicsLayer {
        val s = pressedScale.value * pop.value
        scaleX = s
        scaleY = s
        // A hint of lift at the top of the pop.
        translationY = -(pop.value - 1f) * 12.dp.toPx()
    }

    fun bounce() {
        scope.launch {
            pop.animateTo(1.18f, tween(110, easing = FastOutSlowInEasing))
            pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow))
        }
    }
}

@Composable
private fun rememberTapBounce(): TapBounce {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressedScale = animateFloatAsState(if (pressed) 0.86f else 1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "press")
    val pop = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    return remember { TapBounce(interaction, pop, pressedScale, scope) }
}

/** Explains why a permission is needed before the system dialog appears. */
@Composable
fun PermissionCard(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    permanentlyDenied: Boolean = false,
    onDismiss: (() -> Unit)? = null,
) {
    val c = FaunaryTheme.colors
    val openSettings = rememberOpenAppSettings()
    FaunaryCard(modifier) {
        Row(verticalAlignment = Alignment.Top) {
            IconBadge(icon, background = c.highlight)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = c.foreground)
                Spacer(Modifier.height(2.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium, color = c.foregroundSecondary)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (onDismiss != null) {
                FaunaryButton("Nanti", onDismiss, Modifier.weight(1f), kind = ButtonKind.Ghost, height = 44.dp)
            }
            FaunaryButton(
                text = if (permanentlyDenied) "Buka Pengaturan" else actionLabel,
                onClick = {
                    if (permanentlyDenied) openSettings() else onAction()
                },
                modifier = Modifier.weight(if (onDismiss != null) 1.4f else 1f),
                height = 44.dp,
            )
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = FaunaryTheme.colors
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(c.highlight.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(38.dp), tint = c.brand)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.displaySmall.copy(fontSize = MaterialTheme.typography.headlineSmall.fontSize), color = c.foreground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = c.foregroundSecondary, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            FaunaryButton(actionLabel, onAction, icon = FaunaryIcons.Camera)
        }
    }
}

/** Size of the round floating map controls (layers, 3D, locate, zoom). */
val MapControlSize = 50.dp

/** Round, borderless floating map button with a soft shadow. */
@Composable
fun MapRoundButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = FaunaryTheme.colors.surface,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .softShadow(CircleShape, 6.dp)
            .size(MapControlSize)
            .clip(CircleShape)
            .background(background)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun MapRoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = FaunaryTheme.colors.brand,
) = MapRoundButton(contentDescription, onClick, modifier) {
    Icon(icon, contentDescription, Modifier.size(24.dp), tint = tint)
}

/** Zoom in/out stacked in one vertical capsule. */
@Composable
fun MapZoomControl(onZoomIn: () -> Unit, onZoomOut: () -> Unit, modifier: Modifier = Modifier) {
    val c = FaunaryTheme.colors
    val shape = RoundedCornerShape(50)
    Column(
        modifier.softShadow(shape, 6.dp).width(MapControlSize).clip(shape).background(c.surface),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        listOf(Triple(Icons.Rounded.Add, "Perbesar", onZoomIn), Triple(Icons.Rounded.Remove, "Perkecil", onZoomOut)).forEach { (icon, label, action) ->
            Box(
                Modifier.fillMaxWidth().height(MapControlSize + 2.dp).clickable(role = Role.Button, onClickLabel = label, onClick = action),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, label, Modifier.size(24.dp), tint = c.brand)
            }
        }
    }
}

@Composable
fun LocateButton(onClick: () -> Unit, modifier: Modifier = Modifier) =
    MapRoundIconButton(FaunaryIcons.Crosshair, "Lokasi saya", onClick, modifier, tint = FaunaryTheme.colors.primary)
