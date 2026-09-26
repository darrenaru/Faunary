package com.faunary.app.ui.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.faunary.app.data.AnimalSighting
import com.faunary.app.data.Detection
import com.faunary.app.data.PhotoStorage
import com.faunary.app.data.SettingsRepository
import com.faunary.app.data.SightingRepository
import com.faunary.app.data.StoredPhoto
import com.faunary.app.domain.AnimalCategory
import com.faunary.app.domain.SpeciesCatalog
import com.faunary.app.location.GeoPoint
import com.faunary.app.location.LocationRepository
import com.faunary.app.location.Place
import com.faunary.app.ml.AnimalDetector
import com.faunary.app.ui.navigation.PICKED_LOCATION_KEY
import com.faunary.app.ui.navigation.ReviewRoute
import com.faunary.app.ui.navigation.decodeLatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface LocationStatus {
    data object Loading : LocationStatus
    data object NoPermission : LocationStatus
    data object Unavailable : LocationStatus
    data class Found(val point: GeoPoint, val place: Place?, val manual: Boolean, val fromExif: Boolean = false) : LocationStatus
}

data class ReviewUiState(
    val photo: StoredPhoto? = null,
    val detecting: Boolean = true,
    val detections: List<Detection> = emptyList(),
    val selectedIndex: Int = 0,
    val label: String = "",
    val category: AnimalCategory = AnimalCategory.OTHER,
    val categoryTouched: Boolean = false,
    val note: String = "",
    val location: LocationStatus = LocationStatus.Loading,
    val saving: Boolean = false,
    val savedId: Long? = null,
    val error: String? = null,
) {
    val selected: Detection? get() = detections.getOrNull(selectedIndex)
    val canSave: Boolean get() = photo != null && !detecting && label.isNotBlank() && location is LocationStatus.Found && !saving
}

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val photos: PhotoStorage,
    private val detector: AnimalDetector,
    private val location: LocationRepository,
    private val repository: SightingRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ReviewRoute>()
    private val _state = MutableStateFlow(ReviewUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch { processPhoto() }
        viewModelScope.launch { resolveLocation() }
        viewModelScope.launch {
            savedStateHandle.getStateFlow<String?>(PICKED_LOCATION_KEY, null).filterNotNull().collect { value ->
                savedStateHandle[PICKED_LOCATION_KEY] = null
                decodeLatLng(value)?.let { (lat, lng) -> setManualLocation(lat, lng) }
            }
        }
    }

    private suspend fun processPhoto() {
        val stored = runCatching { photos.normalize(File(route.photoPath)) }.getOrElse {
            _state.update { it.copy(detecting = false, error = "Foto tidak bisa diproses") }
            return
        }
        _state.update { it.copy(photo = stored) }
        val bitmap = runCatching { photos.loadForInference(stored.path) }.getOrNull()
        val detections = bitmap?.let { detector.detect(it, settings.settings.value.minConfidence) }.orEmpty()
        bitmap?.recycle()
        _state.update { s ->
            val first = detections.firstOrNull()
            s.copy(
                detecting = false,
                detections = detections,
                selectedIndex = 0,
                label = if (s.label.isBlank()) first?.label.orEmpty() else s.label,
                category = if (s.categoryTouched) s.category else first?.animalCategory ?: s.category,
            )
        }
    }

    private suspend fun resolveLocation() {
        val exif = decodeLatLng(route.exif)
        if (exif != null) {
            val point = GeoPoint(exif.first, exif.second)
            _state.update { it.copy(location = LocationStatus.Found(point, null, manual = false, fromExif = true)) }
            geocode(point)
            return
        }
        if (!location.hasPermission()) {
            _state.update { it.copy(location = LocationStatus.NoPermission) }
            return
        }
        _state.update { it.copy(location = LocationStatus.Loading) }
        val fix = location.currentLocation()
        if (fix == null) {
            _state.update { it.copy(location = LocationStatus.Unavailable) }
            return
        }
        _state.update { it.copy(location = LocationStatus.Found(fix, null, manual = false)) }
        geocode(fix)
    }

    private suspend fun geocode(point: GeoPoint) {
        val place = location.reverseGeocode(point.latitude, point.longitude) ?: return
        _state.update { s ->
            val loc = s.location
            if (loc is LocationStatus.Found && loc.point == point) s.copy(location = loc.copy(place = place)) else s
        }
    }

    fun retryLocation() = viewModelScope.launch { resolveLocation() }

    private fun setManualLocation(lat: Double, lng: Double) {
        val point = GeoPoint(lat, lng)
        _state.update { it.copy(location = LocationStatus.Found(point, null, manual = true)) }
        viewModelScope.launch { geocode(point) }
    }

    fun selectDetection(index: Int) {
        _state.update { s ->
            val d = s.detections.getOrNull(index) ?: return@update s
            s.copy(selectedIndex = index, label = d.label, category = d.animalCategory, categoryTouched = false)
        }
    }

    fun setLabel(value: String) {
        _state.update { s ->
            val match = s.detections.firstOrNull { it.label.equals(value.trim(), ignoreCase = true) }
            s.copy(
                label = value,
                category = when {
                    s.categoryTouched -> s.category
                    match != null -> match.animalCategory
                    else -> SpeciesCatalog.categoryForUserLabel(value)
                },
            )
        }
    }

    fun setCategory(category: AnimalCategory) {
        _state.update { it.copy(category = category, categoryTouched = true) }
    }

    fun setNote(value: String) {
        _state.update { it.copy(note = value) }
    }

    fun save() {
        val s = _state.value
        val photo = s.photo ?: return
        val loc = s.location as? LocationStatus.Found ?: return
        if (!s.canSave) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val primary = s.selected
            // Keep the chosen detection first so its box is the "primary" one.
            val ordered = primary?.let { listOf(it) + (s.detections - it) } ?: s.detections
            val sighting = AnimalSighting(
                photoPath = photo.path,
                photoWidth = photo.width,
                photoHeight = photo.height,
                animalLabel = s.label.trim(),
                category = s.category.name,
                confidence = primary?.confidence ?: 0f,
                boundingBoxLeft = primary?.left ?: 0f,
                boundingBoxTop = primary?.top ?: 0f,
                boundingBoxRight = primary?.right ?: 0f,
                boundingBoxBottom = primary?.bottom ?: 0f,
                detections = ordered,
                aiLabel = primary?.label,
                latitude = loc.point.latitude,
                longitude = loc.point.longitude,
                locationAccuracy = loc.point.accuracy,
                locationManual = loc.manual,
                locationName = loc.place?.shortName,
                address = loc.place?.fullAddress,
                note = s.note.trim().ifEmpty { null },
                timestamp = System.currentTimeMillis(),
            )
            val id = repository.add(sighting)
            _state.update { it.copy(saving = false, savedId = id) }
        }
    }

    fun discard() {
        _state.value.photo?.let { photos.delete(it.path) } ?: photos.delete(route.photoPath)
    }

    override fun onCleared() {
        // Leaving without saving: don't keep orphaned photos around.
        if (_state.value.savedId == null) discard()
    }
}
