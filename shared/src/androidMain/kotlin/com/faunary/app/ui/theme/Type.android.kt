package com.faunary.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.faunary.shared.resources.Res
import com.faunary.shared.resources.fraunces
import com.faunary.shared.resources.inter
import org.jetbrains.compose.resources.Font

@OptIn(ExperimentalTextApi::class)
@Composable
private fun interFont(weight: Int) = Font(
    Res.font.inter,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

@OptIn(ExperimentalTextApi::class)
@Composable
private fun frauncesFont(weight: Int) = Font(
    Res.font.fraunces,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("SOFT", 50f),
    ),
)

// Fonts come from compose resources, which only load inside composition.
@Composable
internal actual fun faunaryFontFamilies(): Pair<FontFamily, FontFamily> {
    val inter = FontFamily(interFont(400), interFont(500), interFont(600), interFont(700))
    // Display accent font — use sparingly.
    val fraunces = FontFamily(frauncesFont(500), frauncesFont(600), frauncesFont(700))
    return inter to fraunces
}
