package com.faunary.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Body and display font families: Inter + Fraunces on Android, the system font (San Francisco) on iOS. */
@Composable
internal expect fun faunaryFontFamilies(): Pair<FontFamily, FontFamily>

@Composable
fun faunaryTypography(): Typography {
    val (body, display) = faunaryFontFamilies()
    return typography(body, display)
}

private fun typography(body: FontFamily, display: FontFamily) = Typography(
    displayLarge = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 46.sp),
    displayMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp),
    displaySmall = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineLarge = TextStyle(fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp),
    titleSmall = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp),
)
