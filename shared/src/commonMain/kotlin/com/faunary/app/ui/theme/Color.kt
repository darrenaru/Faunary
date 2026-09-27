package com.faunary.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Primary palette
val Canyon = Color(0xFFDF6D41)
val Buttercream = Color(0xFFF7D89A)
val MorningSky = Color(0xFF8DA6CC)
val OliveGrove = Color(0xFFAAA648)
val EarthBrown = Color(0xFF7A4E28)
val DeepBrown = Color(0xFF4A3023)
val Cream = Color(0xFFF5EDE0)
val SoftCream = Color(0xFFFBF8F1)

/**
 * Semantic tokens that don't map cleanly onto Material 3 roles.
 * Light and dark sets share the same warm identity.
 */
@Immutable
data class FaunaryColors(
    val background: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val foreground: Color,
    val foregroundSecondary: Color,
    val foregroundMuted: Color,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val accent: Color,
    val highlight: Color,
    val onHighlight: Color,
    val brand: Color,
    val border: Color,
    val borderStrong: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
    val badgeNature: Color,
    val badgeInfo: Color,
    val badgeSoft: Color,
    val detectionBox: Color,
    val shadow: Color,
    val isDark: Boolean,
)

val LightFaunaryColors = FaunaryColors(
    background = Cream,
    surface = SoftCream,
    surfaceMuted = Color(0xFFF0E5D8),
    foreground = DeepBrown,
    foregroundSecondary = Color(0xFF7A6658),
    foregroundMuted = Color(0xFF9B8D80),
    primary = Canyon,
    onPrimary = Color.White,
    secondary = OliveGrove,
    accent = MorningSky,
    highlight = Buttercream,
    onHighlight = DeepBrown,
    brand = EarthBrown,
    border = Color(0xFFE4D8C8),
    borderStrong = Color(0xFFD3C1AD),
    success = Color(0xFF82985A),
    warning = Color(0xFFD39A45),
    danger = Color(0xFFC75C4D),
    info = Color(0xFF7395BF),
    badgeNature = Color(0xFFD8DEB2),
    badgeInfo = Color(0xFFD8E2F0),
    badgeSoft = Color(0xFFF0E5D8),
    detectionBox = OliveGrove,
    shadow = DeepBrown,
    isDark = false,
)

val DarkFaunaryColors = FaunaryColors(
    background = Color(0xFF29231F),
    surface = Color(0xFF342B25),
    surfaceMuted = Color(0xFF40342B),
    foreground = Cream,
    foregroundSecondary = Color(0xFFD8C9BA),
    foregroundMuted = Color(0xFFAFA094),
    primary = Color(0xFFE8784E),
    onPrimary = Color(0xFF2B211C),
    secondary = Color(0xFFB2B05A),
    accent = Color(0xFF91A9CD),
    highlight = Color(0xFFF4D18E),
    onHighlight = Color(0xFF2B211C),
    brand = Color(0xFFE0B48A),
    border = Color(0xFF514238),
    borderStrong = Color(0xFF655246),
    success = Color(0xFF9DB274),
    warning = Color(0xFFE0AE5E),
    danger = Color(0xFFE07A6A),
    info = Color(0xFF91A9CD),
    badgeNature = Color(0xFF4A4A30),
    badgeInfo = Color(0xFF34404F),
    badgeSoft = Color(0xFF40342B),
    detectionBox = Color(0xFFC9C66A),
    shadow = Color.Black,
    isDark = true,
)

val LocalFaunaryColors = staticCompositionLocalOf { LightFaunaryColors }
