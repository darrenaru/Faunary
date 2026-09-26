package com.faunary.app.ui.gallery

import com.faunary.app.ui.components.icon
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.faunary.app.data.AnimalSighting
import com.faunary.app.domain.AnimalCategory
import com.faunary.app.domain.CollectionStats
import com.faunary.app.location.GeoPoint
import com.faunary.app.ui.components.EmptyState
import com.faunary.app.ui.components.FaunaryCard
import com.faunary.app.ui.components.FaunaryTextField
import com.faunary.app.ui.components.InfoRow
import com.faunary.app.ui.components.PhotoThumb
import com.faunary.app.ui.components.Pill
import com.faunary.app.ui.components.SelectableChip
import com.faunary.app.ui.components.SurfaceIconButton
import com.faunary.app.ui.components.softShadow
import com.faunary.app.ui.map.BottomBarSpace
import com.faunary.app.ui.theme.FaunaryTheme
import com.faunary.app.util.Format
import com.faunary.app.util.Geo

@Composable
fun GalleryScreen(
    onOpenDetail: (Long) -> Unit,
    onOpenCamera: () -> Unit,
    viewModel: GalleryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = FaunaryTheme.colors
    val f = state.filters
    val columns = if (f.grid) 2 else 1
    val fullSpan: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize().background(c.background).statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = BottomBarSpace + 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = fullSpan, key = "header") {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Galeri", style = MaterialTheme.typography.labelLarge, color = c.primary)
                    Text("Koleksi satwamu", style = MaterialTheme.typography.displaySmall, color = c.foreground)
                }
                SurfaceIconButton(if (f.grid) Icons.Rounded.ViewAgenda else Icons.Rounded.GridView, "Ganti tampilan", viewModel::toggleLayout)
            }
        }

        if (state.totalCount > 0) {
            item(span = fullSpan, key = "stats") { StatsCard(state.stats) }
        }

        item(span = fullSpan, key = "search") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FaunaryTextField(
                    f.query, viewModel::setQuery, "Cari hewan, lokasi, catatan…",
                    leadingIcon = Icons.Rounded.Search,
                    modifier = Modifier.weight(1f),
                    trailing = if (f.query.isNotEmpty()) {
                        { Icon(Icons.Rounded.Close, "Hapus", Modifier.size(20.dp).clip(CircleShape).clickable { viewModel.setQuery("") }, tint = c.foregroundMuted) }
                    } else null,
                )
                Spacer(Modifier.width(10.dp))
                SortMenu(f, viewModel::setSort, viewModel::setPeriod, viewModel::toggleFavoritesOnly)
            }
        }

        item(span = fullSpan, key = "chips") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { SelectableChip("Semua", f.category == null, { viewModel.setCategory(null) }, count = state.totalCount) }
                AnimalCategory.entries.forEach { cat ->
                    val n = state.counts[cat] ?: 0
                    if (n > 0) item(cat.name) {
                        SelectableChip(cat.displayName, f.category == cat, { viewModel.setCategory(if (f.category == cat) null else cat) }, icon = cat.icon, count = n)
                    }
                }
            }
        }

        if (state.loaded && state.totalCount == 0) {
            item(span = fullSpan, key = "empty") {
                EmptyState(
                    Icons.Rounded.Pets,
                    "Belum ada koleksi",
                    "Setiap satwa yang kamu potret akan tersimpan di sini lengkap dengan lokasi dan hasil deteksi AI.",
                    actionLabel = "Potret Satwa Pertama",
                    onAction = onOpenCamera,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        } else if (state.loaded && state.resultCount == 0) {
            item(span = fullSpan, key = "noresult") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState(Icons.Rounded.SearchOff, "Satwa belum ditemukan", "Coba kata kunci lain, nama tempat, atau ganti filter kategori.")
                    Text(
                        "Reset filter", style = MaterialTheme.typography.labelLarge, color = c.primary,
                        modifier = Modifier.clip(CircleShape).clickable(onClick = viewModel::clearFilters).padding(12.dp),
                    )
                }
            }
        }

        state.sections.forEach { section ->
            item(span = fullSpan, key = "section-${section.title}") {
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(section.title, style = MaterialTheme.typography.titleMedium, color = c.foreground, modifier = Modifier.weight(1f))
                    Pill("${section.items.size} temuan", color = c.badgeSoft, contentColor = c.foregroundSecondary)
                }
            }
            items(section.items, key = { it.id }) { s ->
                if (f.grid) {
                    GridCard(s, state.here, { onOpenDetail(s.id) }, { viewModel.toggleFavorite(s.id) })
                } else {
                    ListCard(s, state.here, { onOpenDetail(s.id) })
                }
            }
        }
    }
}

@Composable
private fun StatsCard(stats: CollectionStats) {
    val c = FaunaryTheme.colors
    FaunaryCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Jurnal ekspedisi", style = MaterialTheme.typography.titleSmall, color = c.foreground, modifier = Modifier.weight(1f))
            if (stats.streakDays > 0) {
                Pill("${stats.streakDays} hari beruntun", icon = Icons.Rounded.LocalFireDepartment, color = c.highlight)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row {
            Column(Modifier.weight(1f)) {
                Text("Total ditemukan", style = MaterialTheme.typography.bodySmall, color = c.foregroundSecondary)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${stats.total}", style = MaterialTheme.typography.headlineLarge, color = c.foreground)
                    Spacer(Modifier.width(4.dp))
                    Text("ekor", style = MaterialTheme.typography.bodyMedium, color = c.foregroundSecondary, modifier = Modifier.padding(bottom = 5.dp))
                }
                if (stats.thisWeek > 0) {
                    InfoRow(Icons.AutoMirrored.Rounded.TrendingUp, "+${stats.thisWeek} minggu ini", color = c.success)
                }
            }
            Box(Modifier.width(1.dp).height(64.dp).background(c.border))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Sebaran lokasi", style = MaterialTheme.typography.bodySmall, color = c.foregroundSecondary)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${stats.distinctSpots}", style = MaterialTheme.typography.headlineLarge, color = c.foreground)
                    Spacer(Modifier.width(4.dp))
                    Text("titik", style = MaterialTheme.typography.bodyMedium, color = c.foregroundSecondary, modifier = Modifier.padding(bottom = 5.dp))
                }
                stats.topArea?.let { InfoRow(Icons.Rounded.LocationOn, it) }
            }
        }
        if (stats.byCategory.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            CompositionBar(stats.byCategory, stats.total)
        }
    }
}

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

@Composable
private fun SortMenu(
    f: GalleryFilters,
    onSort: (SortOrder) -> Unit,
    onPeriod: (Period) -> Unit,
    onFavorites: () -> Unit,
) {
    val c = FaunaryTheme.colors
    var open by remember { mutableStateOf(false) }
    val active = f.sort != SortOrder.NEWEST || f.period != Period.ALL || f.favoritesOnly
    Box {
        SurfaceIconButton(
            Icons.Rounded.SwapVert, "Urutkan & filter", { open = true }, size = 50.dp,
            tint = if (active) c.onPrimary else c.brand,
            background = if (active) c.primary else c.surface,
        )
        DropdownMenu(open, { open = false }, shape = RoundedCornerShape(18.dp), containerColor = c.surface) {
            Text("Urutkan", style = MaterialTheme.typography.labelMedium, color = c.foregroundMuted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            SortOrder.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.label, color = if (s == f.sort) c.primary else c.foreground) },
                    onClick = { onSort(s); open = false },
                )
            }
            HorizontalDivider(color = c.border)
            Text("Rentang waktu", style = MaterialTheme.typography.labelMedium, color = c.foregroundMuted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            Period.entries.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.label, color = if (p == f.period) c.primary else c.foreground) },
                    onClick = { onPeriod(p); open = false },
                )
            }
            HorizontalDivider(color = c.border)
            DropdownMenuItem(
                text = { Text("Hanya favorit", color = if (f.favoritesOnly) c.primary else c.foreground) },
                leadingIcon = { Icon(if (f.favoritesOnly) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = c.primary) },
                onClick = { onFavorites(); open = false },
            )
        }
    }
}

private fun distanceText(s: AnimalSighting, here: GeoPoint?): String? =
    here?.let { Format.distance(Geo.distanceMeters(it.latitude, it.longitude, s.latitude, s.longitude)) }

@Composable
private fun GridCard(s: AnimalSighting, here: GeoPoint?, onClick: () -> Unit, onFavorite: () -> Unit) {
    val c = FaunaryTheme.colors
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.softShadow(shape, 5.dp).clip(shape).background(c.surface).border(1.dp, c.border, shape).clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            PhotoThumb(s.photoPath, Modifier.fillMaxSize(), RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp), s.animalLabel)
            if (s.isAiDetected) {
                Pill(Format.percent(s.confidence), Modifier.align(Alignment.TopStart).padding(8.dp), icon = Icons.Rounded.AutoAwesome)
            }
            Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(30.dp).clip(CircleShape)
                    .background(c.surface.copy(alpha = 0.9f)).clickable(onClick = onFavorite),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (s.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favorit", Modifier.size(16.dp), tint = c.primary)
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.animalCategory.displayName, style = MaterialTheme.typography.labelMedium, color = c.primary, modifier = Modifier.weight(1f))
                Text(Format.shortDate(s.timestamp), style = MaterialTheme.typography.labelMedium, color = c.foregroundMuted)
            }
            Spacer(Modifier.height(2.dp))
            Text(s.animalLabel, style = MaterialTheme.typography.titleSmall, color = c.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                InfoRow(Icons.Rounded.LocationOn, s.locationName ?: "Tanpa nama", Modifier.weight(1f))
                distanceText(s, here)?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = c.foregroundMuted)
                }
            }
        }
    }
}

@Composable
private fun ListCard(s: AnimalSighting, here: GeoPoint?, onClick: () -> Unit) {
    val c = FaunaryTheme.colors
    FaunaryCard(onClick = onClick, contentPadding = PaddingValues(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PhotoThumb(s.photoPath, Modifier.size(84.dp), RoundedCornerShape(16.dp), s.animalLabel)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(s.animalCategory.displayName, color = c.badgeNature, icon = s.animalCategory.icon)
                    if (s.isFavorite) Icon(Icons.Rounded.Favorite, null, Modifier.size(16.dp).align(Alignment.CenterVertically), tint = c.primary)
                }
                Spacer(Modifier.height(4.dp))
                Text(s.animalLabel, style = MaterialTheme.typography.titleSmall, color = c.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                InfoRow(Icons.Rounded.LocationOn, listOfNotNull(s.locationName, distanceText(s, here)).joinToString(" · ").ifEmpty { "Tanpa nama" })
                Text(
                    "${Format.shortDate(s.timestamp)} · ${Format.time(s.timestamp)}" + if (s.isAiDetected) " · AI ${Format.percent(s.confidence)}" else "",
                    style = MaterialTheme.typography.labelMedium, color = c.foregroundMuted,
                )
            }
        }
    }
}
