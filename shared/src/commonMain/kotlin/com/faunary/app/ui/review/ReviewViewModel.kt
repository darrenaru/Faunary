package com.faunary.app.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.faunary.app.data.AnimalSighting
import com.faunary.app.data.Detection
import com.faunary.app.data.PhotoProcessor
import com.faunary.app.data.SightingRepository
import com.faunary.app.data.StoredPhoto
import com.faunary.app.domain.AnimalCategory
import com.faunary.app.domain.SpeciesCatalog
import com.faunary.app.location.GeoPoint
import com.faunary.app.location.GpsAccuracy
import com.faunary.app.location.LocationSource
import com.faunary.app.location.Place
import com.faunary.app.ml.AnimalDetector
import com.faunary.app.ml.DetectionSource
import com.faunary.app.ui.navigation.decodeFix
import com.faunary.app.ui.navigation.encodeFix
import com.faunary.app.util.Geo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface LocationStatus {
    data object Loading : LocationStatus
    data object NoPermission : LocationStatus
    /** Only approximate location is allowed (Android 12+, iOS 14+): far too coarse to pin a find. */
    data object Approximate : LocationStatus
    data object Unavailable : LocationStatus
    /** Best GPS fix for where the photo was taken; [refining] while the receiver is still sharpening it. */
    data class Found(val point: GeoPoint, val place: Place?, val refining: Boolean = false) : LocationStatus {
        val preciseEnough: Boolean get() = (point.accuracy ?: 0f) <= GpsAccuracy.MAX_SAVE_METERS
    }
}

data class ReviewUiState(
    val photo: StoredPhoto? = null,
    val detecting: Boolean = true,
    val detections: List<Detection> = emptyList(),
    val detectionSource: DetectionSource? = null,
    /** Nothing detected because the photo shows a picture of an animal (screen, poster…). */
    val depictionOnly: Boolean = false,
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
    val canSave: Boolean
        get() = photo != null && !detecting && label.isNotBlank() && !saving &&
            (location as? LocationStatus.Found)?.preciseEnough == true
}

/** First fix with no camera fix to start from (cold GPS can take a while). */
private const val FIRST_FIX_TIMEOUT_MS = 30_000L
/** How long a fix is sharpened after the shot before settling on the best one. */
private const val REFINE_MS = 20_000L

/** Arguments of the review screen (ReviewRoute on Android). */
data class ReviewArgs(
    /** The fresh camera capture to review. */
    val photoPath: String,
    /** When the shutter was pressed. */
    val capturedAt: Long,
    /** GPS fix at that moment ([encodeFix]), if the camera had a fresh one. */
    val captureFix: String? = null,
)

class ReviewViewModel(
    private val route: ReviewArgs,
    private val photos: PhotoProcessor,
    private val detector: AnimalDetector,
    private val location: LocationSource,
    private val repository: SightingRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ReviewUiState())
    val state = _state.asStateFlow()
    /** The running fix search; replaced (not doubled) when the user asks for a fresh fix. */
    private var locationJob: Job? = null

    init {
        viewModelScope.launch { processPhoto() }
        retryLocation()
    }

    private suspend fun processPhoto() {
        val stored = runCatching { photos.normalize(route.photoPath) }.getOrElse {
            _state.update { it.copy(detecting = false, error = "Foto tidak bisa diproses") }
            return
        }
        _state.update { it.copy(photo = stored) }
        val result = detector.detect(stored.path)
        val detections = result?.detections.orEmpty()
        _state.update { s ->
            val first = detections.firstOrNull()
            s.copy(
                detecting = false,
                detections = detections,
                detectionSource = result?.source,
                depictionOnly = result?.depictionOnly == true,
                selectedIndex = 0,
                label = if (s.label.isBlank()) first?.label.orEmpty() else s.label,
                category = if (s.categoryTouched) s.category else first?.animalCategory ?: s.category,
            )
        }
    }

    /**
     * The find is pinned where it was photographed. Starts from the camera's fix at the shutter press,
     * then keeps listening to high-accuracy GPS for a few seconds and keeps the most precise fix, until
     * it's [GpsAccuracy.GOOD_METERS] or better. Never falls back to an old cached position.
     */
    private suspend fun resolveLocation() {
        if (!location.hasPermission()) {
            _state.update { it.copy(location = LocationStatus.NoPermission) }
            return
        }
        if (!location.hasPreciseLocation()) {
            _state.update { it.copy(location = LocationStatus.Approximate) }
            return
        }
        val anchor = decodeFix(route.captureFix)
        var best: GeoPoint? = (_state.value.location as? LocationStatus.Found)?.point ?: anchor
        _state.update { it.copy(location = best?.let { p -> LocationStatus.Found(p, placeOf(p), refining = true) } ?: LocationStatus.Loading) }
        withTimeoutOrNull(if (best == null) FIRST_FIX_TIMEOUT_MS else REFINE_MS) {
            location.updates()
                .transformWhile { fix ->
                    emit(fix)
                    (fix.accuracy ?: Float.MAX_VALUE) > GpsAccuracy.GOOD_METERS
                }
                .collect { fix ->
                    val current = best
                    val better = current == null || (fix.accuracy ?: Float.MAX_VALUE) < (current.accuracy ?: Float.MAX_VALUE)
                    if (better && nearShot(fix, anchor)) {
                        best = fix
                        _state.update { it.copy(location = LocationStatus.Found(fix, placeOf(fix), refining = true)) }
                    }
                }
        }
        val final = best
        if (final == null) {
            _state.update { it.copy(location = LocationStatus.Unavailable) }
            return
        }
        _state.update { s ->
            val loc = s.location as? LocationStatus.Found
            s.copy(location = LocationStatus.Found(final, loc?.place?.takeIf { loc.point == final }, refining = false))
        }
        geocode(final)
    }

    /**
     * A sharper fix is only taken if it agrees with the one from the shutter press (within both
     * accuracy circles): after walking away, a "better" fix would be where the user is, not the animal.
     */
    private fun nearShot(fix: GeoPoint, shot: GeoPoint?): Boolean {
        shot ?: return true
        val slack = (shot.accuracy ?: GpsAccuracy.MAX_SAVE_METERS) + (fix.accuracy ?: GpsAccuracy.MAX_SAVE_METERS) + 5f
        return Geo.distanceMeters(shot.latitude, shot.longitude, fix.latitude, fix.longitude) <= slack
    }

    /** The place name already looked up for [point], if any (kept while a fix is being refined). */
    private fun placeOf(point: GeoPoint): Place? =
        (_state.value.location as? LocationStatus.Found)?.takeIf { it.point == point }?.place

    private suspend fun geocode(point: GeoPoint) {
        val place = location.reverseGeocode(point.latitude, point.longitude) ?: return
        _state.update { s ->
            val loc = s.location
            if (loc is LocationStatus.Found && loc.point == point) s.copy(location = loc.copy(place = place)) else s
        }
    }

    /** Back from system Settings: pick up a location permission (or its precise upgrade) granted there. */
    fun recheckLocationPermission() {
        val waiting = when (_state.value.location) {
            LocationStatus.NoPermission -> location.hasPermission()
            LocationStatus.Approximate -> location.hasPreciseLocation()
            else -> false
        }
        if (waiting) retryLocation()
    }

    fun retryLocation() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch { resolveLocation() }
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
                locationManual = false,
                locationName = loc.place?.shortName,
                address = loc.place?.fullAddress,
                note = s.note.trim().ifEmpty { null },
                timestamp = route.capturedAt,
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
