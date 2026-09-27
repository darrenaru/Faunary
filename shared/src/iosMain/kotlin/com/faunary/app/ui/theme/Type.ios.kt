package com.faunary.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

// The system font (San Francisco) for everything, as iOS users expect.
@Composable
internal actual fun faunaryFontFamilies(): Pair<FontFamily, FontFamily> = FontFamily.Default to FontFamily.Default
