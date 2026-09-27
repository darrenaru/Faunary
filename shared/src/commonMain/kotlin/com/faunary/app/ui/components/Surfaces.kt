package com.faunary.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.faunary.app.ui.theme.FaunaryTheme
import com.faunary.app.ui.theme.Radius

/** Very soft brown-tinted shadow. */
fun Modifier.softShadow(shape: Shape, elevation: Dp = 10.dp, floating: Boolean = false): Modifier {
    val tint = Color(0xFF4A3023).copy(alpha = if (floating) 0.16f else 0.10f)
    return shadow(elevation = elevation, shape = shape, ambientColor = tint, spotColor = tint)
}

@Composable
fun FaunaryCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(Radius.lg),
    color: Color = FaunaryTheme.colors.surface,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    elevated: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = FaunaryTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .then(if (elevated) Modifier.softShadow(shape, 6.dp) else Modifier)
            .clip(shape)
            .background(color)
            .border(1.dp, c.border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = FaunaryTheme.colors.highlight,
    // Dark ink only on the light buttercream fill; tinted badges use the regular foreground.
    contentColor: Color = if (color == FaunaryTheme.colors.highlight) FaunaryTheme.colors.onHighlight else FaunaryTheme.colors.foreground,
    icon: ImageVector? = null,
    leading: String? = null,
) {
    Row(
        modifier
            .clip(CircleShape)
            .background(color)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(14.dp), tint = contentColor)
        if (leading != null) Text(leading, style = MaterialTheme.typography.labelMedium)
        Text(text, style = MaterialTheme.typography.labelMedium, color = contentColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SelectableChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: String? = null,
    icon: ImageVector? = null,
    count: Int? = null,
) {
    val c = FaunaryTheme.colors
    val bg by animateColorAsState(if (selected) c.primary else c.surface, tween(200), label = "chipBg")
    val fg by animateColorAsState(if (selected) c.onPrimary else c.foreground, tween(200), label = "chipFg")
    Row(
        modifier
            .softShadow(CircleShape, if (selected) 6.dp else 2.dp)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, if (selected) Color.Transparent else c.border, CircleShape)
            .clickable(role = Role.Tab, onClick = onClick)
            .heightIn(min = 38.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(16.dp), tint = fg)
        if (leading != null) Text(leading, style = MaterialTheme.typography.labelLarge)
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
        if (count != null) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) c.onPrimary else c.foregroundSecondary,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (selected) Color.White.copy(alpha = 0.22f) else c.surfaceMuted)
                    .padding(horizontal = 7.dp, vertical = 1.dp),
            )
        }
    }
}

enum class ButtonKind { Primary, Secondary, Ghost, Danger }

@Composable
fun FaunaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 50.dp,
) {
    val c = FaunaryTheme.colors
    val (bg, fg, border) = when (kind) {
        ButtonKind.Primary -> Triple(c.primary, c.onPrimary, null)
        ButtonKind.Secondary -> Triple(c.highlight, c.onHighlight, null)
        ButtonKind.Ghost -> Triple(Color.Transparent, c.brand, BorderStroke(1.dp, c.border))
        ButtonKind.Danger -> Triple(c.danger.copy(alpha = 0.12f), c.danger, null)
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(150), label = "btnScale")
    val shape = RoundedCornerShape(Radius.md)
    Row(
        modifier
            .scale(scale)
            .height(height)
            .clip(shape)
            .background(if (enabled) bg else c.surfaceMuted)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val tint = if (enabled) fg else c.foregroundMuted
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp), tint = tint)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1)
        if (trailingIcon != null) {
            Spacer(Modifier.width(8.dp))
            Icon(trailingIcon, null, Modifier.size(20.dp), tint = tint)
        }
    }
}

@Composable
fun SurfaceIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = FaunaryTheme.colors.brand,
    background: Color = FaunaryTheme.colors.surface,
    shape: Shape = RoundedCornerShape(14.dp),
) {
    val c = FaunaryTheme.colors
    Box(
        modifier
            .softShadow(shape, 4.dp)
            .size(size)
            .clip(shape)
            .background(background)
            .border(1.dp, c.border, shape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(22.dp), tint = tint)
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    icon: ImageVector? = null,
) {
    val c = FaunaryTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp), tint = c.brand)
            Spacer(Modifier.width(8.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.foreground, modifier = Modifier.weight(1f))
        if (trailing != null) Pill(trailing, color = c.badgeSoft, contentColor = c.foregroundSecondary)
    }
}

@Composable
fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    background: Color = FaunaryTheme.colors.badgeSoft,
    // On the (light) highlight fill, brand tan is unreadable in dark mode — use the on-highlight ink.
    tint: Color = if (background == FaunaryTheme.colors.highlight) FaunaryTheme.colors.onHighlight else FaunaryTheme.colors.brand,
    size: Dp = 40.dp,
) {
    Box(modifier.size(size).clip(RoundedCornerShape(12.dp)).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size * 0.5f), tint = tint)
    }
}

@Composable
fun InfoRow(icon: ImageVector, text: String, modifier: Modifier = Modifier, color: Color = FaunaryTheme.colors.foregroundSecondary) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(15.dp), tint = color)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
