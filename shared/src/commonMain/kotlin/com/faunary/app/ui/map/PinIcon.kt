package com.faunary.app.ui.map

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Star
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import com.faunary.app.ui.components.FaunaryIcons

/** What a shared marker is about; [key] is what the server stores (`map_pins.icon`). */
enum class PinIcon(val key: String, val label: String, val color: Color, val glyph: () -> ImageVector) {
    FLAG("flag", "Bendera", Color(0xFFDF6D41), { Icons.Rounded.Flag }),
    LEAF("leaf", "Alam", Color(0xFF4E9A6B), { Icons.Rounded.Eco }),
    CAMERA("camera", "Spot foto", Color(0xFF3F7FB5), { FaunaryIcons.Camera }),
    PAW("paw", "Satwa", Color(0xFF7B5CC4), { Icons.Rounded.Pets }),
    STAR("star", "Favorit", Color(0xFFD9A21B), { Icons.Rounded.Star }),
    HEART("heart", "Disukai", Color(0xFFE0474C), { Icons.Rounded.Favorite });

    val argb: Int get() = color.toArgb()

    companion object {
        fun fromKey(key: String?): PinIcon = entries.firstOrNull { it.key == key } ?: FLAG
    }
}
