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
import com.faunary.app.location.Route
import com.faunary.app.location.RouteProgress
import com.faunary.app.location.RouteRepository
import com.faunary.app.location.RouteTracker
import com.faunary.app.location.TravelMode
import com.faunary.app.location.VoiceGuide
import com.faunary.app.remote.Bounds
import com.faunary.app.remote.CommunityRepository
import com.faunary.app.remote.CommunitySighting
import com.faunary.app.remote.LiveChange
import com.faunary.app.remote.SightingChange
import com.faunary.app.util.Geo
import java.util.Locale
import kotlin.math.roundToInt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

/** In-app route to a destination; [route] is null while loading or on error. */
data class RouteUi(
    val destLat: Double,
    val destLng: Double,
    val label: String,
    val mode: TravelMode = TravelMode.WALKING,
    val loading: Boolean = true,
    val route: Route? = null,
    val error: String? = null,
    val needsPermission: Boolean = false,
    /** Turn-by-turn mode: the camera follows the user and prompts are shown/spoken. */
    val navigating: Boolean = false,
    val position: GeoPoint? = null,
    val progress: RouteProgress? = null,
    /** Route line still ahead of the user while navigating. */
    val remaining: List<Pair<Double, Double>>? = null,
    val rerouting: Boolean = false,
    val arrived: Boolean = false,
    val muted: Boolean = false,
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
/** Within this distance of the destination the trip counts as done. */
private const val ARRIVE_METERS = 20.0
private const val REROUTE_COOLDOWN_MS = 15_000L

data class MapUiState(
    val loaded: Boolean = false,
    val all: List<AnimalSighting> = emptyList(),
    val visible: List<AnimalSighting> = emptyList(),
    val community: List<CommunitySighting> = emptyList(),
    val liveUsers: List<LiveUser> = emptyList(),
    /** Per-category totals of everything the map can show (own + community, per enabled layer). */
    val counts: Map<AnimalCategory, Int> = emptyMap(),
    val totalCount: Int = 0,
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
                add(MapMarker(ownKey(it.id), it.latitude, it.longitude, it.photoPath, it.animalCategory, time = it.timestamp))
            }
            if (layers.community) community.forEach {
                add(MapMarker(communityKey(it.id), it.latitude, it.longitude, it.photoUrl, it.animalCategory, MarkerKind.COMMUNITY, time = it.takenAtMs))
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
    private val routes: RouteRepository,
    private val voice: VoiceGuide,
) : ViewModel() {

    private val routeState = MutableStateFlow<RouteUi?>(null)
    val route: StateFlow<RouteUi?> = routeState.asStateFlow()
    private var routeJob: Job? = null
    private var navJob: Job? = null
    private var tracker: RouteTracker? = null
    /** Prompts already given, as "<step index>:<far|near>", so each is said once. */
    private val announced = mutableSetOf<String>()
    private var offRouteFixes = 0
    private var lastRerouteAt = 0L

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
            filter = f,
            lastFix = fix,
            settings = prefs,
            online = communityRepo.isAvailable,
        )
    }

    val state: StateFlow<MapUiState> = combine(own, community, live, layers, selectedKey) { base, com, liveMap, lay, sel ->
        val comList = com.values.filter { base.filter == null || it.animalCategory == base.filter }
        val liveList = liveMap.values.toList()
        // Chip counts ignore the active filter (each chip shows its own total) but respect layers.
        val categories = (if (lay.own) base.all.map { it.animalCategory } else emptyList()) +
            (if (lay.community) com.values.map { it.animalCategory } else emptyList())
        base.copy(
            counts = categories.groupingBy { it }.eachCount(),
            totalCount = categories.size,
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
                        // New finds are always added, wherever they are: the map clusters them.
                        is SightingChange.Upserted -> community.update { it + (change.sighting.id to change.sighting) }
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
            // Recent finds everywhere, refreshed periodically as a fallback for missed realtime events.
            viewModelScope.launch {
                while (isActive) {
                    val recent = communityRepo.recentSightings()
                    if (recent != null) community.update { current -> current + recent.associateBy { it.id } }
                    delay(if (recent == null) 10_000 else 180_000)
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
            // Merge, don't replace: the recent set and realtime inserts outside this area must stay.
            // On failure lastBounds stays unchanged, so the next idle retries this area.
            val result = communityRepo.sightingsIn(padded) ?: return@launch
            lastBounds = padded
            community.update { current -> current + result.associateBy { it.id } }
        }
    }

    fun startRoute(lat: Double, lng: Double, label: String) {
        selectedKey.value = null
        stopNavigation()
        val prev = routeState.value
        routeState.value = RouteUi(lat, lng, label, mode = prev?.mode ?: TravelMode.WALKING, muted = prev?.muted ?: false)
        fetchRoute()
    }

    fun setRouteMode(mode: TravelMode) {
        routeState.update { it?.copy(mode = mode) }
        fetchRoute()
    }

    /** Recomputes from the user's current position (e.g. after walking a bit). */
    fun refreshRoute() = fetchRoute()

    fun clearRoute() {
        stopNavigation()
        routeJob?.cancel()
        routeState.value = null
    }

    /** Switches the drawn route into turn-by-turn navigation from the user's live position. */
    fun startNavigation() {
        val current = routeState.value ?: return
        val r = current.route ?: return
        if (!location.hasPermission()) return
        tracker = RouteTracker(r)
        announced.clear()
        offRouteFixes = 0
        routeState.update { it?.copy(navigating = true, arrived = false, rerouting = false, progress = null, remaining = null) }
        if (!current.muted) {
            voice.start()
            val first = r.steps.firstOrNull()?.instruction?.let { " $it." } ?: ""
            voice.speak("Memulai navigasi ke ${current.label}.$first")
        }
        navJob?.cancel()
        navJob = viewModelScope.launch { location.updates().collect(::onNavigationFix) }
    }

    /** Leaves turn-by-turn mode; the route stays drawn. */
    fun stopNavigation() {
        navJob?.cancel()
        navJob = null
        tracker = null
        voice.stop()
        routeState.update { it?.copy(navigating = false, position = null, progress = null, remaining = null, rerouting = false, arrived = false) }
    }

    fun setMuted(muted: Boolean) {
        routeState.update { it?.copy(muted = muted) }
        if (muted) voice.stop() else if (routeState.value?.navigating == true) voice.start()
    }

    private fun onNavigationFix(fix: GeoPoint) {
        val t = tracker ?: return
        val current = routeState.value ?: return
        if (current.arrived) return
        val p = t.update(fix.latitude, fix.longitude)
        val walking = current.mode == TravelMode.WALKING
        val arrived = p.remainingMeters < ARRIVE_METERS ||
            Geo.distanceMeters(fix.latitude, fix.longitude, current.destLat, current.destLng) < ARRIVE_METERS
        routeState.update { it?.copy(position = fix, progress = p, remaining = t.remainingPoints(p), arrived = arrived) }

        if (arrived) {
            say("Kamu telah tiba di ${current.label}.")
            navJob?.cancel()
            return
        }

        // Prompts: once well ahead of a manoeuvre ("Dalam 200 meter, belok kiri…") and once right at it.
        p.nextStep?.let { step ->
            val far = if (walking) 60.0 else 400.0
            val near = if (walking) 15.0 else 60.0
            val i = p.nextStepIndex
            when {
                p.distanceToNextStep <= near -> if (announced.add("$i:near")) {
                    announced.add("$i:far")
                    say(step.instruction)
                }
                p.distanceToNextStep <= far -> if (announced.add("$i:far")) {
                    say("Dalam ${spokenDistance(p.distanceToNextStep)}, ${step.instruction.replaceFirstChar { it.lowercase() }}")
                }
            }
        }

        // Off the line for a few fixes in a row (allowing for GPS accuracy): fetch a new route from here.
        val tolerance = (if (walking) 30.0 else 50.0) + (fix.accuracy ?: 0f).coerceAtMost(30f)
        offRouteFixes = if (p.offRouteMeters > tolerance) offRouteFixes + 1 else 0
        val now = System.currentTimeMillis()
        if (offRouteFixes >= 3 && !current.rerouting && now - lastRerouteAt > REROUTE_COOLDOWN_MS) {
            lastRerouteAt = now
            offRouteFixes = 0
            say("Menghitung ulang rute.")
            fetchRoute(origin = fix)
        }
    }

    private fun say(text: String) {
        if (routeState.value?.muted != true) voice.speak(text)
    }

    /** "120 meter", "1,5 kilometer": rounded so prompts sound natural. */
    private fun spokenDistance(meters: Double): String = when {
        meters < 1000 -> "${((meters / 10).roundToInt() * 10).coerceAtLeast(10)} meter"
        else -> String.format(Locale.forLanguageTag("id-ID"), "%.1f kilometer", meters / 1000).replace(",0 ", " ")
    }

    override fun onCleared() {
        voice.stop()
    }

    /** Loads the route; while navigating this is a silent reroute from [origin] that keeps the old line until it succeeds. */
    private fun fetchRoute(origin: GeoPoint? = null) {
        routeJob?.cancel()
        routeJob = viewModelScope.launch {
            val target = routeState.value ?: return@launch
            if (target.navigating) {
                routeState.update { it?.copy(rerouting = true) }
                val from = origin ?: target.position ?: location.lastFix.value
                if (from == null) {
                    routeState.update { it?.copy(rerouting = false) }
                    return@launch
                }
                routes.route(from, target.destLat, target.destLng, target.mode)
                    .onSuccess { r ->
                        tracker = RouteTracker(r)
                        announced.clear()
                        routeState.update { it?.copy(route = r, rerouting = false) }
                    }
                    .onFailure { routeState.update { it?.copy(rerouting = false) } }
                return@launch
            }
            routeState.update { it?.copy(loading = true, error = null, needsPermission = false) }
            if (!location.hasPermission()) {
                routeState.update { it?.copy(loading = false, needsPermission = true, error = "Izinkan lokasi untuk menghitung rute dari posisimu.") }
                return@launch
            }
            val from = location.currentLocation() ?: location.lastFix.value
            if (from == null) {
                routeState.update { it?.copy(loading = false, error = "Posisimu belum terdeteksi. Pastikan GPS aktif lalu coba lagi.") }
                return@launch
            }
            routes.route(from, target.destLat, target.destLng, target.mode)
                .onSuccess { r -> routeState.update { it?.copy(loading = false, route = r) } }
                .onFailure { routeState.update { it?.copy(loading = false, route = null, error = "Rute tidak bisa dimuat. Periksa koneksi internet lalu coba lagi.") } }
        }
    }

    fun hasLocationPermission() = location.hasPermission()

    /** True exactly once per app launch: the map then opens on the globe and flies to the user. */
    fun takeIntro(): Boolean {
        if (introPlayed) return false
        introPlayed = true
        return true
    }

    /** Where the intro flight lands: the known position right away, else a fresh GPS fix. */
    suspend fun introTarget(): GeoPoint? = location.lastKnown() ?: location.currentLocation()

    private companion object {
        /** Process-wide, so it resets on a cold start but not when returning to the Map tab. */
        var introPlayed = false
    }

    fun toggle3D() = settings.setMap3D(!settings.settings.value.map3D)

    fun acknowledgePublicNotice() = settings.markPublicNoticeSeen()

    fun setShareLive(enabled: Boolean) = settings.setShareLiveLocation(enabled)

    fun setFilter(category: AnimalCategory?) {
        filter.value = category
    }

    fun setLayers(value: MapLayers) {
        layers.value = value
    }

    /** The finds behind a tapped stack, in the given (newest first) order. */
    fun selectionsFor(keys: List<String>): List<MapSelection> {
        val s = state.value
        val own = s.visible.associateBy { ownKey(it.id) }
        val com = s.community.associateBy { communityKey(it.id) }
        return keys.mapNotNull { k -> own[k]?.let { MapSelection.Own(it) } ?: com[k]?.let { MapSelection.Community(it) } }
    }

    fun select(key: String?) {
        selectedKey.value = key
    }

    fun toggleFavorite(id: Long) = viewModelScope.launch { repository.toggleFavorite(id) }

    fun refreshLocation(onFix: (GeoPoint) -> Unit = {}) = viewModelScope.launch {
        location.currentLocation()?.let(onFix)
    }
}
