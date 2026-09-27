package com.faunary.app.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.faunary.app.data.AnimalSighting
import com.faunary.app.data.SightingRepository
import com.faunary.app.domain.AnimalCategory
import com.faunary.app.domain.CollectionStats
import com.faunary.app.location.GeoPoint
import com.faunary.app.location.LocationSource
import com.faunary.app.util.Format
import com.faunary.app.util.currentTimeMillis
import com.faunary.app.util.localDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SortOrder(val label: String) { NEWEST("Terbaru"), OLDEST("Terlama"), NAME("Nama A–Z") }

enum class Period(val label: String) { ALL("Semua waktu"), WEEK("7 hari"), MONTH("30 hari"), YEAR("Tahun ini") }

data class GallerySection(val title: String, val items: List<AnimalSighting>)

data class GalleryFilters(
    val query: String = "",
    val category: AnimalCategory? = null,
    val sort: SortOrder = SortOrder.NEWEST,
    val period: Period = Period.ALL,
    val favoritesOnly: Boolean = false,
    val grid: Boolean = true,
)

data class GalleryUiState(
    val loaded: Boolean = false,
    val filters: GalleryFilters = GalleryFilters(),
    val stats: CollectionStats = CollectionStats(),
    val counts: Map<AnimalCategory, Int> = emptyMap(),
    val totalCount: Int = 0,
    val sections: List<GallerySection> = emptyList(),
    val resultCount: Int = 0,
    val here: GeoPoint? = null,
)

class GalleryViewModel(
    private val repository: SightingRepository,
    private val location: LocationSource,
) : ViewModel() {

    private val filters = MutableStateFlow(GalleryFilters())

    val state: StateFlow<GalleryUiState> = combine(repository.observeAll(), filters, location.lastFix) { all, f, here ->
        val filtered = all.filter { s -> matches(s, f) }
        val sorted = when (f.sort) {
            SortOrder.NEWEST -> filtered.sortedByDescending { it.timestamp }
            SortOrder.OLDEST -> filtered.sortedBy { it.timestamp }
            SortOrder.NAME -> filtered.sortedBy { it.animalLabel.lowercase() }
        }
        val sections = if (f.sort == SortOrder.NAME) {
            listOf(GallerySection("Semua temuan", sorted)).filter { it.items.isNotEmpty() }
        } else {
            sorted.groupBy { Format.monthYear(it.timestamp) }.map { (k, v) -> GallerySection(k.replaceFirstChar { it.uppercase() }, v) }
        }
        GalleryUiState(
            loaded = true,
            filters = f,
            stats = CollectionStats.from(all),
            counts = all.groupingBy { it.animalCategory }.eachCount(),
            totalCount = all.size,
            sections = sections,
            resultCount = sorted.size,
            here = here,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GalleryUiState())

    init {
        if (location.hasPermission()) viewModelScope.launch { location.currentLocation() }
    }

    private fun matches(s: AnimalSighting, f: GalleryFilters): Boolean {
        if (f.category != null && s.animalCategory != f.category) return false
        if (f.favoritesOnly && !s.isFavorite) return false
        val now = currentTimeMillis()
        val inPeriod = when (f.period) {
            Period.ALL -> true
            Period.WEEK -> s.timestamp >= now - 7 * DAY_MS
            Period.MONTH -> s.timestamp >= now - 30 * DAY_MS
            Period.YEAR -> localDateTime(s.timestamp).year == localDateTime(now).year
        }
        if (!inPeriod) return false
        val q = f.query.trim().lowercase()
        if (q.isEmpty()) return true
        return listOfNotNull(s.animalLabel, s.animalCategory.displayName, s.locationName, s.address, s.note)
            .any { it.lowercase().contains(q) }
    }

    fun setQuery(q: String) = filters.update { it.copy(query = q) }
    fun setCategory(c: AnimalCategory?) = filters.update { it.copy(category = c) }
    fun setSort(s: SortOrder) = filters.update { it.copy(sort = s) }
    fun setPeriod(p: Period) = filters.update { it.copy(period = p) }
    fun toggleFavoritesOnly() = filters.update { it.copy(favoritesOnly = !it.favoritesOnly) }
    fun toggleLayout() = filters.update { it.copy(grid = !it.grid) }
    fun clearFilters() = filters.update { GalleryFilters(grid = it.grid) }
    fun toggleFavorite(id: Long) = viewModelScope.launch { repository.toggleFavorite(id) }
}

private const val DAY_MS = 24 * 60 * 60 * 1000L
