package com.faunary.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.faunary.app.data.AnimalSighting
import com.faunary.app.data.Settings
import com.faunary.app.data.SettingsRepository
import com.faunary.app.data.SightingRepository
import com.faunary.app.domain.AnimalCategory
import com.faunary.app.location.GeoPoint
import com.faunary.app.location.LocationRepository
import com.faunary.app.remote.Bounds
import com.faunary.app.remote.CommunityRepository
import com.faunary.app.remote.CommunitySighting
import com.faunary.app.remote.LiveChange
import com.faunary.app.remote.SightingChange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class LiveUser(
    val userId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float?,
    /** Local receive time; users drop off the map after [LIVE_TTL_MS] without an update. */
    val seenAt: Long,
)

data class MapLayers(val own: Boolean = true, val community: Boolean = true, val live: Boolean = true)

sealed interface MapSelection {
    val key: String

    data class Own(val sighting: AnimalSighting) : MapSelection {
        override val key get() = ownKey(sighting.id)
    }

    data class Community(val sighting: CommunitySighting) : MapSelection {
        override val key get() = communityKey(sighting.id)
    }

    data class Live(val user: LiveUser) : MapSelection {
        override val key get() = liveKey(user.userId)
    }
}

fun ownKey(id: Long) = "own:$id"
fun communityKey(id: String) = "com:$id"
fun liveKey(id: String) = "live:$id"

private val LIVE_TTL_MS = TimeUnit.MINUTES.toMillis(5)

data class MapUiState(
    val loaded: Boolean = false,
    val all: List<AnimalSighting> = emptyList(),
    val visible: List<AnimalSighting> = emptyList(),
    val community: List<CommunitySighting> = emptyList(),
    val liveUsers: List<LiveUser> = emptyList(),
    val counts: Map<AnimalCategory, Int> = emptyMap(),
    val filter: AnimalCategory? = null,
    val layers: MapLayers = MapLayers(),
    val selection: MapSelection? = null,
    val lastFix: GeoPoint? = null,
    val settings: Settings = Settings(),
    val online: Boolean = false,
) {
    val map3D: Boolean get() = settings.map3D

    val markers: List<MapMarker>
        get() = buildList {
            if (layers.own) visible.forEach {
                add(MapMarker(ownKey(it.id), it.latitude, it.longitude, it.photoPath, it.animalCategory))
            }
            if (layers.community) community.forEach {
                add(MapMarker(communityKey(it.id), it.latitude, it.longitude, it.photoUrl, it.animalCategory, MarkerKind.COMMUNITY))
            }
            if (layers.live) liveUsers.forEach {
                add(MapMarker(liveKey(it.userId), it.latitude, it.longitude, null, AnimalCategory.OTHER, MarkerKind.LIVE, it.name))
            }
        }
}

@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: SightingRepository,
    private val location: LocationRepository,
    private val settings: SettingsRepository,
    private val communityRepo: CommunityRepository,
) : ViewModel() {

    /** Last camera position, so returning to the Map tab doesn't reset the view. */
    var camera: Pair<GeoPoint, Double>? = null

    private val filter = MutableStateFlow<AnimalCategory?>(null)
    private val selectedKey = MutableStateFlow<String?>(null)
    private val layers = MutableStateFlow(MapLayers())
    private val community = MutableStateFlow<Map<String, CommunitySighting>>(emptyMap())
    private val live = MutableStateFlow<Map<String, LiveUser>>(emptyMap())
    private var lastBounds: Bounds? = null
    private var fetchJob: Job? = null

    private val own = combine(repository.observeAll(), filter, location.lastFix, settings.settings) { all, f, fix, prefs ->
        MapUiState(
            loaded = true,
            all = all,
            visible = if (f == null) all else all.filter { it.animalCategory == f },
            counts = all.groupingBy { it.animalCategory }.eachCount(),
            filter = f,
            lastFix = fix,
            settings = prefs,
            online = communityRepo.isAvailable,
        )
    }

    val state: StateFlow<MapUiState> = combine(own, community, live, layers, selectedKey) { base, com, liveMap, lay, sel ->
        val comList = com.values.filter { base.filter == null || it.animalCategory == base.filter }
        val liveList = liveMap.values.toList()
        base.copy(
            community = comList,
            liveUsers = liveList,
            layers = lay,
            selection = when {
                sel == null -> null
                sel.startsWith("own:") -> base.visible.firstOrNull { ownKey(it.id) == sel }?.let { MapSelection.Own(it) }
                sel.startsWith("com:") -> comList.firstOrNull { communityKey(it.id) == sel }?.let { MapSelection.Community(it) }
                else -> liveList.firstOrNull { liveKey(it.userId) == sel }?.let { MapSelection.Live(it) }
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState(settings = settings.settings.value))

    init {
        if (communityRepo.isAvailable) {
            viewModelScope.launch {
                communityRepo.sightingChanges().collect { change ->
                    when (change) {
                        is SightingChange.Upserted -> if (lastBounds?.contains(change.sighting.latitude, change.sighting.longitude) != false) {
                            community.update { it + (change.sighting.id to change.sighting) }
                        }
                        is SightingChange.Deleted -> community.update { it - change.id }
                    }
                }
            }
            viewModelScope.launch {
                communityRepo.liveChanges().collect { change ->
                    when (change) {
                        is LiveChange.Moved -> with(change.location) {
                            live.update { it + (userId to LiveUser(userId, displayName, latitude, longitude, accuracy, System.currentTimeMillis())) }
                        }
                        is LiveChange.Left -> live.update { it - change.userId }
                    }
                }
            }
            // Initial live snapshot, periodic refresh as a realtime fallback, and pruning of stale users.
            viewModelScope.launch {
                while (isActive) {
                    val now = System.currentTimeMillis()
                    val fresh = communityRepo.liveLocations().associate {
                        it.userId to LiveUser(it.userId, it.displayName, it.latitude, it.longitude, it.accuracy, now)
                    }
                    live.update { current ->
                        (current + fresh).filterValues { now - it.seenAt < LIVE_TTL_MS }
                    }
                    delay(60_000)
                }
            }
        }
    }

    /** Called when the map settles: (re)loads community sightings for the visible area. */
    fun onCameraIdle(bounds: Bounds) {
        if (!communityRepo.isAvailable) return
        // Pad the viewport so small pans don't trigger a refetch every time.
        val padLat = (bounds.north - bounds.south) * 0.25
        val padLng = (bounds.east - bounds.west) * 0.25
        val padded = Bounds(bounds.south - padLat, bounds.west - padLng, bounds.north + padLat, bounds.east + padLng)
        val prev = lastBounds
        if (prev != null && prev.contains(bounds.south, bounds.west) && prev.contains(bounds.north, bounds.east)) return
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            delay(300)
            val result = communityRepo.sightingsIn(padded)
            lastBounds = padded
            community.value = result.associateBy { it.id }
        }
    }

    fun hasLocationPermission() = location.hasPermission()

    fun toggle3D() = settings.setMap3D(!settings.settings.value.map3D)

    fun acknowledgePublicNotice() = settings.markPublicNoticeSeen()

    fun setShareLive(enabled: Boolean) = settings.setShareLiveLocation(enabled)

    fun setFilter(category: AnimalCategory?) {
        filter.value = category
    }

    fun setLayers(value: MapLayers) {
        layers.value = value
    }

    fun select(key: String?) {
        selectedKey.value = key
    }

    fun toggleFavorite(id: Long) = viewModelScope.launch { repository.toggleFavorite(id) }

    fun refreshLocation(onFix: (GeoPoint) -> Unit = {}) = viewModelScope.launch {
        location.currentLocation()?.let(onFix)
    }
}
