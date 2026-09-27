package com.faunary.app.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.faunary.app.domain.AnimalCategory
import com.faunary.app.ui.theme.FaunaryTheme
@Composable
fun categoryColor(cat: AnimalCategory): Color {
    val c = FaunaryTheme.colors
    return when (cat) {
        AnimalCategory.CAT -> c.primary
        AnimalCategory.BIRD -> c.accent
        AnimalCategory.DOG -> c.warning
        AnimalCategory.WILD -> c.secondary
        AnimalCategory.OTHER -> c.borderStrong
    }
}

@Composable
fun CompositionBar(byCategory: Map<AnimalCategory, Int>, total: Int) {
    val c = FaunaryTheme.colors
    val entries = byCategory.entries.sortedByDescending { it.value }
    Text("Komposisi temuan", style = MaterialTheme.typography.labelLarge, color = c.foregroundSecondary)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        entries.forEach { (cat, n) ->
            Box(Modifier.weight(n.toFloat() / total.coerceAtLeast(1)).fillMaxHeight().background(categoryColor(cat)))
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        entries.take(4).forEach { (cat, n) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(categoryColor(cat)))
                Spacer(Modifier.width(5.dp))
                Text("${cat.displayName} $n", style = MaterialTheme.typography.labelMedium, color = c.foregroundSecondary)
            }
        }
    }
}
