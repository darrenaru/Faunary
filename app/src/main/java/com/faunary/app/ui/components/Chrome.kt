package com.faunary.app.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.faunary.app.ui.theme.FaunaryTheme
import com.faunary.app.ui.theme.Radius
import kotlin.math.roundToInt

enum class MainTab(val label: String, val icon: ImageVector) {
    Map("Peta", Icons.Rounded.Map),
    Gallery("Galeri", Icons.Rounded.PhotoLibrary),
    Journal("Jurnal", Icons.Rounded.AutoStories),
    Profile("Profil", Icons.Rounded.Person),
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
                .clickable(role = Role.Button, onClickLabel = "Ambil foto satwa", onClick = onCamera),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.PhotoCamera, "Kamera", Modifier.size(26.dp), tint = c.onPrimary)
        }
    }
}

@Composable
private fun NavItem(tab: MainTab, current: MainTab?, onTab: (MainTab) -> Unit, modifier: Modifier) {
    val c = FaunaryTheme.colors
    val selected = tab == current
    val tint by animateColorAsState(if (selected) c.primary else c.foregroundMuted, tween(200), label = "navTint")
    val pill by animateColorAsState(if (selected) c.primary.copy(alpha = 0.12f) else c.surface.copy(alpha = 0f), tween(200), label = "navPill")
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { onTab(tab) }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.clip(CircleShape).background(pill).padding(horizontal = 14.dp, vertical = 3.dp)) {
            Icon(tab.icon, null, Modifier.size(22.dp), tint = tint)
        }
        Spacer(Modifier.height(2.dp))
        Text(tab.label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
fun GpsChip(accuracyMeters: Float?, modifier: Modifier = Modifier) {
    val c = FaunaryTheme.colors
    val ok = accuracyMeters != null
    Row(
        modifier
            .softShadow(CircleShape, 3.dp)
            .clip(CircleShape)
            .background(c.surface)
            .border(1.dp, c.border, CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (ok) c.success else c.foregroundMuted))
        Text(
            if (ok) "GPS ${accuracyMeters.roundToInt()}m" else "GPS mati",
            style = MaterialTheme.typography.labelMedium,
            color = c.foreground,
        )
    }
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
    val context = LocalContext.current
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
                    if (permanentlyDenied) {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    } else onAction()
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
            FaunaryButton(actionLabel, onAction, icon = Icons.Rounded.PhotoCamera)
        }
    }
}

@Composable
fun LocateButton(onClick: () -> Unit, modifier: Modifier = Modifier) =
    SurfaceIconButton(Icons.Rounded.MyLocation, "Lokasi saya", onClick, modifier, tint = FaunaryTheme.colors.primary)
