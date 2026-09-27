package com.faunary.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

// Radius scale
object Radius {
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 22.dp
    val xl = 28.dp
}

private val FaunaryShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.xs),
    small = RoundedCornerShape(Radius.sm),
    medium = RoundedCornerShape(Radius.md),
    large = RoundedCornerShape(Radius.lg),
    extraLarge = RoundedCornerShape(Radius.xl),
)

private fun schemeFor(c: FaunaryColors) = if (c.isDark) {
    darkColorScheme(
        primary = c.primary, onPrimary = c.onPrimary,
        primaryContainer = c.highlight, onPrimaryContainer = c.onHighlight,
        secondary = c.secondary, onSecondary = c.background,
        secondaryContainer = c.badgeNature, onSecondaryContainer = c.foreground,
        tertiary = c.accent, onTertiary = c.background,
        background = c.background, onBackground = c.foreground,
        surface = c.surface, onSurface = c.foreground,
        surfaceVariant = c.surfaceMuted, onSurfaceVariant = c.foregroundSecondary,
        surfaceContainerLowest = c.background, surfaceContainerLow = c.surface,
        surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.surfaceMuted,
        outline = c.borderStrong, outlineVariant = c.border,
        error = c.danger, onError = c.background,
    )
} else {
    lightColorScheme(
        primary = c.primary, onPrimary = c.onPrimary,
        primaryContainer = c.highlight, onPrimaryContainer = c.onHighlight,
        secondary = c.secondary, onSecondary = c.foreground,
        secondaryContainer = c.badgeNature, onSecondaryContainer = c.foreground,
        tertiary = c.accent, onTertiary = c.foreground,
        background = c.background, onBackground = c.foreground,
        surface = c.surface, onSurface = c.foreground,
        surfaceVariant = c.surfaceMuted, onSurfaceVariant = c.foregroundSecondary,
        surfaceContainerLowest = c.surface, surfaceContainerLow = c.surface,
        surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.surfaceMuted,
        outline = c.borderStrong, outlineVariant = c.border,
        error = c.danger, onError = SoftCream,
    )
}

@Composable
fun FaunaryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkFaunaryColors else LightFaunaryColors
    val typography = faunaryTypography()
    CompositionLocalProvider(LocalFaunaryColors provides colors) {
        MaterialTheme(
            colorScheme = schemeFor(colors),
            typography = typography,
            shapes = FaunaryShapes,
        ) {
            // Bare Text() reads LocalTextStyle, which is the platform default font unless provided.
            ProvideTextStyle(typography.bodyMedium, content)
        }
    }
}

object FaunaryTheme {
    val colors: FaunaryColors
        @Composable @ReadOnlyComposable get() = LocalFaunaryColors.current
}
