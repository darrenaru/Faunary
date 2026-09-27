package com.faunary.app.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.faunary.app.data.AnimalSighting
import com.faunary.app.ui.components.CategoryAvatar
import com.faunary.app.ui.components.EmptyState
import com.faunary.app.ui.components.FaunaryCard
import com.faunary.app.ui.components.FaunaryIcons
import com.faunary.app.ui.components.IconBadge
import com.faunary.app.ui.components.PhotoThumb
import com.faunary.app.ui.components.Pill
import com.faunary.app.ui.components.SectionHeader
import com.faunary.app.ui.gallery.CompositionBar
import com.faunary.app.ui.gallery.categoryColor
import com.faunary.app.ui.map.BottomBarSpace
import com.faunary.app.ui.theme.FaunaryTheme
import com.faunary.app.util.Format
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

@Composable
fun JournalScreen(
    onOpenDetail: (Long) -> Unit,
    onOpenCamera: () -> Unit,
    viewModel: JournalViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = FaunaryTheme.colors
    val st = state.stats

    LazyColumn(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = BottomBarSpace + 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text("Jurnal", style = MaterialTheme.typography.labelLarge, color = c.primary)
                Text("Catatan ekspedisi", style = MaterialTheme.typography.displaySmall, color = c.foreground)
            }
        }

        if (state.loaded && st.total == 0) {
            item {
                EmptyState(
                    FaunaryIcons.Journal, "Jurnalmu masih kosong",
                    "Statistik, rekor, dan riwayat pertemuan akan tumbuh seiring kamu mendokumentasikan satwa.",
                    actionLabel = "Mulai Mendokumentasikan", onAction = onOpenCamera,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
            return@LazyColumn
        }

        // Hero block (muted full-width color block)
        item {
            val ink = if (c.isDark) c.foreground else c.onHighlight
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(c.secondary.copy(alpha = if (c.isDark) 0.35f else 0.9f)).padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocalFireDepartment, null, Modifier.size(22.dp), tint = ink)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (st.streakDays > 0) "${st.streakDays} hari beruntun" else "Mulai streak baru hari ini",
                        style = MaterialTheme.typography.labelLarge, color = ink,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Kamu telah menemukan ${st.total} satwa di ${st.distinctSpots} titik berbeda.",
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = MaterialTheme.typography.displaySmall.fontFamily),
                    color = ink,
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(Icons.Rounded.Pets, "${st.total}", "Total temuan", Modifier.weight(1f))
                StatTile(Icons.Rounded.Category, "${st.distinctSpecies}", "Jenis berbeda", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(Icons.Rounded.LocationOn, "${st.distinctSpots}", "Titik lokasi", Modifier.weight(1f))
                StatTile(Icons.Rounded.Today, "${st.thisWeek}", "7 hari terakhir", Modifier.weight(1f))
            }
        }

        item {
            FaunaryCard { CompositionBar(st.byCategory, st.total) }
        }

        if (st.topLabels.isNotEmpty()) {
            item {
                FaunaryCard {
                    SectionHeader("Paling sering ditemui", icon = Icons.Rounded.EmojiEvents)
                    Spacer(Modifier.height(12.dp))
                    val max = st.topLabels.first().count.toFloat()
                    st.topLabels.forEachIndexed { i, l ->
                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", style = MaterialTheme.typography.titleSmall, color = c.foregroundMuted, modifier = Modifier.width(22.dp))
                            CategoryAvatar(l.category, size = 34.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row {
                                    Text(l.label, style = MaterialTheme.typography.titleSmall, color = c.foreground, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${l.count}×", style = MaterialTheme.typography.labelLarge, color = c.foregroundSecondary)
                                }
                                Spacer(Modifier.height(5.dp))
                                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(c.surfaceMuted)) {
                                    Box(Modifier.fillMaxWidth(l.count / max).fillMaxHeight().clip(CircleShape).background(categoryColor(l.category)))
                                }
                            }
                        }
                    }
                }
            }
        }

        st.aiAcceptance?.let { acc ->
            item {
                FaunaryCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.AutoAwesome, background = c.highlight)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Ketepatan AI", style = MaterialTheme.typography.titleSmall, color = c.foreground)
                            Text("Deteksi yang kamu terima tanpa koreksi", style = MaterialTheme.typography.bodySmall, color = c.foregroundSecondary)
                        }
                        Text("${(acc * 100).roundToInt()}%", style = MaterialTheme.typography.headlineMedium, color = c.primary)
                    }
                }
            }
        }

        if (state.onThisDay.isNotEmpty()) {
            item { SectionHeader("Hari ini di tahun lalu", icon = Icons.Rounded.History) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.onThisDay, key = { it.id }) { s ->
                        Column(Modifier.width(140.dp).clip(RoundedCornerShape(18.dp)).clickable { onOpenDetail(s.id) }) {
                            PhotoThumb(s.photoPath, Modifier.size(140.dp), RoundedCornerShape(18.dp))
                            Spacer(Modifier.height(6.dp))
                            Text(s.animalLabel, style = MaterialTheme.typography.titleSmall, color = c.foreground, maxLines = 1)
                            Text(Format.fullDate(s.timestamp).substringAfterLast(" "), style = MaterialTheme.typography.labelMedium, color = c.foregroundMuted)
                        }
                    }
                }
            }
        }

        item { SectionHeader("Riwayat pertemuan", icon = Icons.Rounded.History, trailing = "${state.recent.size} terbaru") }
        items(state.recent, key = { it.id }) { s -> TimelineRow(s, isLast = s == state.recent.last()) { onOpenDetail(s.id) } }
    }
}

@Composable
private fun StatTile(icon: ImageVector, value: String, label: String, modifier: Modifier) {
    val c = FaunaryTheme.colors
    FaunaryCard(modifier) {
        IconBadge(icon, size = 36.dp)
        Spacer(Modifier.height(10.dp))
        Text(value, style = MaterialTheme.typography.headlineMedium, color = c.foreground)
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.foregroundSecondary)
    }
}

@Composable
private fun TimelineRow(s: AnimalSighting, isLast: Boolean, onClick: () -> Unit) {
    val c = FaunaryTheme.colors
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick)) {
        Column(Modifier.width(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(22.dp))
            Box(Modifier.size(12.dp).clip(CircleShape).background(categoryColor(s.animalCategory)))
            if (!isLast) Box(Modifier.width(2.dp).height(58.dp).background(c.border))
        }
        Spacer(Modifier.width(8.dp))
        Row(Modifier.weight(1f).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            PhotoThumb(s.photoPath, Modifier.size(56.dp), RoundedCornerShape(14.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.animalLabel, style = MaterialTheme.typography.titleSmall, color = c.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(s.locationName ?: Format.coordinates(s.latitude, s.longitude), style = MaterialTheme.typography.bodySmall, color = c.foregroundSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Pill(Format.relative(s.timestamp), color = c.badgeSoft, contentColor = c.foregroundSecondary)
        }
    }
}
