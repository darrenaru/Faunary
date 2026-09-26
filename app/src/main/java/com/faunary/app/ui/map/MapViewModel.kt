package com.faunary.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.faunary.app.data.AnimalSighting
import com.faunary.app.data.SettingsRepository
import com.faunary.app.data.SightingRepository
import com.faunary.app.domain.AnimalCategory
import com.faunary.app.location.GeoPoint
import com.faunary.app.location.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MapUiState(
    val loaded: Boolean = false,
    val all: List<AnimalSighting> = emptyList(),
    val visible: List<AnimalSighting> = emptyList(),
    val counts: Map<AnimalCategory, Int> = emptyMap(),
    val filter: AnimalCategory? = null,
    val selected: AnimalSighting? = null,
    val lastFix: GeoPoint? = null,
    val map3D: Boolean = false,
) {
    val markers: List<MapMarker>
        get() = visible.map { MapMarker(it.id, it.latitude, it.longitude, it.photoPath, it.animalCategory) }
}

@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: SightingRepository,
    private val location: LocationRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    /** Last camera position, so returning to the Map tab doesn't reset the view. */
    var camera: Pair<GeoPoint, Double>? = null

    private val filter = MutableStateFlow<AnimalCategory?>(null)
    private val selectedId = MutableStateFlow<Long?>(null)

    val state: StateFlow<MapUiState> = combine(
        repository.observeAll(),
        filter,
        selectedId,
        location.lastFix,
        settings.settings,
    ) { all, f, sel, fix, prefs ->
        val visible = if (f == null) all else all.filter { it.animalCategory == f }
        MapUiState(
            loaded = true,
            all = all,
            visible = visible,
            counts = all.groupingBy { it.animalCategory }.eachCount(),
            filter = f,
            selected = visible.firstOrNull { it.id == sel },
            lastFix = fix,
            map3D = prefs.map3D,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState(map3D = settings.settings.value.map3D))

    fun hasLocationPermission() = location.hasPermission()

    fun toggle3D() = settings.setMap3D(!settings.settings.value.map3D)

    fun setFilter(category: AnimalCategory?) {
        filter.value = category
    }

    fun select(id: Long?) {
        selectedId.value = id
    }

    fun toggleFavorite(id: Long) = viewModelScope.launch { repository.toggleFavorite(id) }

    fun refreshLocation(onFix: (GeoPoint) -> Unit = {}) = viewModelScope.launch {
        location.currentLocation()?.let(onFix)
    }
}
