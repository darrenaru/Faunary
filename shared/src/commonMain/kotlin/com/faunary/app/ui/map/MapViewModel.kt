package com.faunary.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.faunary.app.data.AnimalSighting
import com.faunary.app.data.Settings
import com.faunary.app.data.SettingsRepository
import com.faunary.app.data.SightingRepository
import com.faunary.app.domain.AnimalCategory
import com.faunary.app.location.GeoPoint
import com.faunary.app.location.LocationSource
import com.faunary.app.location.PlaceSearchRepository
import com.faunary.app.location.PlaceSuggestion
import com.faunary.app.location.Route
import com.faunary.app.location.RouteProgress
import com.faunary.app.location.RouteRepository
import com.faunary.app.location.RouteTracker
import com.faunary.app.location.SearchedPlace
import com.faunary.app.location.TravelMode
import com.faunary.app.location.VoiceGuide
import com.faunary.app.remote.Bounds
import com.faunary.app.remote.CommunityRepository
import com.faunary.app.remote.CommunitySighting
import com.faunary.app.remote.DeploySignal
import com.faunary.app.remote.LiveChange
import com.faunary.app.remote.LiveRouteChange
import com.faunary.app.remote.LiveRouteDto
import com.faunary.app.remote.LiveRouteRepository
import com.faunary.app.remote.MapPin
import com.faunary.app.remote.PinChange
import com.faunary.app.remote.PinError
import com.faunary.app.remote.PinException
import com.faunary.app.remote.PinRepository
import com.faunary.app.remote.SharedTrip
import com.faunary.app.remote.SightingChange
import com.faunary.app.util.Format
import com.faunary.app.util.Geo
import com.faunary.app.util.currentTimeMillis
import com.faunary.app.util.randomUuid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

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
    /** When this route was shown (a new route, not a silent reroute): its line is drawn in, here and for others. */
    val drawnAt: Long = 0L,
    /** Visible to others (live sharing on): from "Rute ke Sini" until it's closed, reached, or navigation ends. */
    val shared: Boolean = true,
    val muted: Boolean = false,
)

/**
 * A marker being deployed at a long-pressed spot, drawn and animated by the map as a single pin:
 * the real marker ([id], the same on every device) stays hidden until the animation has settled on
 * its spot. [mine]: deployed here; otherwise another explorer's deploy, played in step with theirs.
 */
data class PinDeploy(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val phase: Phase,
    val mine: Boolean = true,
) {
    enum class Phase {
        DEPLOYING,
        DEPLOYED,
        /** Animation done and at rest: the real marker is shown underneath, then this pin is removed. */
        SETTLING,
        FAILED,
    }

    /** Whether [pin] is the one this deployment is drawing (so the map must not draw it twice). */
    fun covers(pin: MapPin): Boolean = pin.id == id && (phase == Phase.DEPLOYING || phase == Phase.DEPLOYED)
}

/** Shortest the deploy animation runs, so a fast server doesn't turn it into a flicker. */
private const val MIN_DEPLOY_MS = 1_600L
/**
 * Another explorer's new marker arrived as a row without its "deployed" broadcast (missed, or no
 * "deploying" either): ripple this long before the pop, about what its creator saw.
 */
private const val REMOTE_INSERT_GRACE_MS = MIN_DEPLOY_MS + 400L
/** Least time another explorer's pin falls and lands before it can pop. */
private const val REMOTE_MIN_DROP_MS = 600L
/** Someone's "deploying" with no outcome in this long (app closed, connection lost): settle it from what's known. */
private const val REMOTE_DEPLOY_TIMEOUT_MS = 15_000L
/** How long the "deployed" pop and the failure fade play before the overlay goes. */
private const val DEPLOY_OUTRO_MS = 700L
/** Overlap of the resting animated pin and the real one, so the swap never leaves a gap. */
private const val DEPLOY_HANDOFF_MS = 300L
/** How long "Undo" is offered after a marker is deployed (e.g. after a mistaken long press). */
private const val UNDO_DEPLOY_MS = 6_000L

data class MapLayers(val own: Boolean = true, val community: Boolean = true, val live: Boolean = true, val pins: Boolean = true)

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

    /** A shared marker; [mine] = the viewer created it and may delete it. */
    data class Pin(val pin: MapPin, val mine: Boolean) : MapSelection {
        override val key get() = pinKey(pin.id)
    }
}

/** Where a selection sits on the map, as (lat, lng). */
val MapSelection.latLng: Pair<Double, Double>
    get() = when (this) {
        is MapSelection.Own -> sighting.latitude to sighting.longitude
        is MapSelection.Community -> sighting.latitude to sighting.longitude
        is MapSelection.Live -> user.latitude to user.longitude
        is MapSelection.Pin -> pin.latitude to pin.longitude
    }

fun ownKey(id: Long) = "own:$id"
fun communityKey(id: String) = "com:$id"
fun liveKey(id: String) = "live:$id"
fun pinKey(id: String) = "pin:$id"

/** Map search: the typed text, matches among what's on the map, and places/addresses from Mapbox. */
data class SearchUi(
    val query: String = "",
    /** Own and community finds, shared markers and live explorers whose name/notes match, nearest first. */
    val onMap: List<MapSelection> = emptyList(),
    val places: List<PlaceSuggestion> = emptyList(),
    /** The query read as "lat, lng". */
    val coordinates: GeoPoint? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

/** Map key of the searched place's marker. */
const val PLACE_KEY = "place"

private const val SEARCH_DEBOUNCE_MS = 250L
private const val SEARCH_MIN_CHARS = 2
private const val SEARCH_MAX_ON_MAP = 8
private val LatLngPattern = Regex("""^\s*(-?\d{1,2}(?:\.\d+)?)\s*[,;\s]\s*(-?\d{1,3}(?:\.\d+)?)\s*$""")

private const val LIVE_TTL_MS = 5 * 60 * 1000L
/** Within this distance of the destination the trip counts as done. */
private const val ARRIVE_METERS = 20.0
private const val REROUTE_COOLDOWN_MS = 15_000L

data class MapUiState(
    val loaded: Boolean = false,
    val all: List<AnimalSighting> = emptyList(),
    val visible: List<AnimalSighting> = emptyList(),
    val community: List<CommunitySighting> = emptyList(),
    val liveUsers: List<LiveUser> = emptyList(),
    /** Shared markers (not affected by the animal filter). */
    val pins: List<MapPin> = emptyList(),
    /** Routes of explorers sharing their live location (shown with the live layer). */
    val sharedRoutes: List<LiveRouteDto> = emptyList(),
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
            if (layers.pins) pins.forEach {
                add(MapMarker(pinKey(it.id), it.latitude, it.longitude, null, AnimalCategory.OTHER, MarkerKind.PIN, it.displayTitle, pinIcon = PinIcon.fromKey(it.icon)))
            }
            if (layers.live) liveUsers.forEach {
                add(MapMarker(liveKey(it.userId), it.latitude, it.longitude, null, AnimalCategory.OTHER, MarkerKind.LIVE, it.name))
            }
        }
}

class MapViewModel(
    private val repository: SightingRepository,
    private val location: LocationSource,
    private val settings: SettingsRepository,
    private val communityRepo: CommunityRepository,
    private val routes: RouteRepository,
    private val voice: VoiceGuide,
    private val pinRepo: PinRepository,
    private val liveRoutes: LiveRouteRepository,
    private val placeSearch: PlaceSearchRepository,
) : ViewModel() {

    private val _search = MutableStateFlow(SearchUi())
    val search: StateFlow<SearchUi> = _search.asStateFlow()
    val recentPlaces: StateFlow<List<SearchedPlace>> = placeSearch.recent
    private var searchJob: Job? = null

    /** Place picked from search: drawn with its own marker and card until closed. */
    private val _place = MutableStateFlow<SearchedPlace?>(null)
    val place: StateFlow<SearchedPlace?> = _place.asStateFlow()

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
    private val pins = MutableStateFlow<Map<String, MapPin>>(emptyMap())
    private val otherRoutes = MutableStateFlow<Map<String, LiveRouteDto>>(emptyMap())
    private val myId = MutableStateFlow<String?>(null)

    /** The marker just deployed, while its "Undo" is on offer. */
    private val _justDeployed = MutableStateFlow<String?>(null)
    val justDeployed: StateFlow<String?> = _justDeployed.asStateFlow()

    /** One-off feedback for marker actions (shown as a toast). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun messageShown() {
        _message.value = null
    }
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

    /** Deploy animations by marker id: this device's and other explorers', played in step on every map. */
    private val deploys = MutableStateFlow<Map<String, PinDeploy>>(emptyMap())
    val deployAnimations: StateFlow<List<PinDeploy>> =
        deploys.map { it.values.toList() }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    /** This device's marker being deployed (for the status chip). */
    val deploy: StateFlow<PinDeploy?> =
        deploys.map { all -> all.values.firstOrNull { it.mine } }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    /** How another explorer's deploy ends (true = saved), once known; keyed by marker id. */
    private val remoteOutcomes = mutableMapOf<String, CompletableDeferred<Boolean>>()

    private val shared = combine(pins, myId, deploys, ::Triple)
    private val view = combine(layers, selectedKey, ::Pair)

    private val explorers = combine(live, otherRoutes, ::Pair)

    val state: StateFlow<MapUiState> = combine(own, community, explorers, view, shared) { base, com, (liveMap, routeMap), (lay, sel), (pinMap, me, deploying) ->
        // A marker still being deployed is drawn only by the deploy animation, never twice.
        val pinList = pinMap.values.filterNot { deploying[it.id]?.covers(it) == true }.sortedByDescending { it.createdAt }
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
            pins = pinList,
            sharedRoutes = if (lay.live) routeMap.values.toList() else emptyList(),
            layers = lay,
            selection = when {
                sel == null -> null
                sel.startsWith("own:") -> base.visible.firstOrNull { ownKey(it.id) == sel }?.let { MapSelection.Own(it) }
                sel.startsWith("com:") -> comList.firstOrNull { communityKey(it.id) == sel }?.let { MapSelection.Community(it) }
                sel.startsWith("pin:") -> pinList.firstOrNull { pinKey(it.id) == sel }?.let { MapSelection.Pin(it, mine = it.userId == me) }
                else -> liveList.firstOrNull { liveKey(it.userId) == sel }?.let { MapSelection.Live(it) }
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState(settings = settings.settings.value))

    init {
        if (liveRoutes.isAvailable) {
            // Share the own route from "Rute ke Sini" on (while live sharing is on; the repository decides);
            // closing the route, ending navigation or arriving removes it from everyone's map …
            viewModelScope.launch {
                routeState.collect { r ->
                    val route = r?.route
                    liveRoutes.setTrip(
                        if (route == null || r.arrived || !r.shared) null
                        else SharedTrip(
                            destLat = r.destLat, destLng = r.destLng, destLabel = r.label, mode = r.mode.profile,
                            path = r.remaining ?: route.points,
                            remainingMeters = r.progress?.remainingMeters ?: route.distanceMeters,
                            startedAt = r.drawnAt,
                        ),
                    )
                }
            }
            // … and follow everyone else's (realtime, plus a periodic refresh as a fallback).
            viewModelScope.launch {
                liveRoutes.changes().collect { change ->
                    when (change) {
                        is LiveRouteChange.Updated -> otherRoutes.update { it + (change.route.userId to change.route) }
                        is LiveRouteChange.Ended -> otherRoutes.update { it - change.userId }
                    }
                }
            }
            // The server only returns routes refreshed in the last minute, so this also drops the route of
            // an app that was killed mid-trip (no realtime event is sent for that).
            viewModelScope.launch {
                while (isActive) {
                    otherRoutes.value = liveRoutes.others().associateBy { it.userId }
                    delay(20_000)
                }
            }
        }
        if (pinRepo.isAvailable) {
            viewModelScope.launch { myId.value = pinRepo.myId() }
            viewModelScope.launch {
                pinRepo.changes().collect(::onPinChange)
            }
            // Full list (replaced, so removals made while realtime was down disappear too).
            viewModelScope.launch {
                while (isActive) {
                    val all = pinRepo.all()
                    if (all != null) pins.value = all.associateBy { it.id }
                    delay(if (all == null) 10_000 else 300_000)
                }
            }
        }
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
                            live.update { it + (userId to LiveUser(userId, displayName, latitude, longitude, accuracy, currentTimeMillis())) }
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
                    val now = currentTimeMillis()
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
        // Ending navigation also takes the route off everyone else's map (it stays drawn here).
        routeState.update {
            it?.copy(
                navigating = false, position = null, progress = null, remaining = null, rerouting = false, arrived = false,
                shared = it.shared && !it.navigating,
            )
        }
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
        val now = currentTimeMillis()
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
        else -> "${Format.decimal(meters / 1000, 1, ',')} kilometer".replace(",0 ", " ")
    }

    override fun onCleared() {
        voice.stop()
        liveRoutes.setTrip(null)
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
                .onSuccess { r -> routeState.update { it?.copy(loading = false, route = r, drawnAt = currentTimeMillis(), shared = true) } }
                .onFailure { routeState.update { it?.copy(loading = false, route = null, error = "Rute tidak bisa dimuat. Periksa koneksi internet lalu coba lagi.") } }
        }
    }

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

    val canCreatePins: Boolean get() = pinRepo.isAvailable

    /**
     * Long press: deploys a marker there. It goes to the server straight away (so everyone sees it in
     * real time, unnamed) while the map plays the deploy animation; the creator then taps it to fill in
     * the details. One deployment at a time. Every step is broadcast, so other explorers' maps play
     * the same animation at the same moment; the marker's id is made here, so theirs and ours is one pin.
     */
    fun deployPin(latitude: Double, longitude: Double) {
        if (deploys.value.values.any { it.mine }) return
        val id = randomUuid()
        deploys.update { it + (id to PinDeploy(id, latitude, longitude, PinDeploy.Phase.DEPLOYING)) }
        viewModelScope.launch {
            val me = myId.value ?: pinRepo.myId()?.also { myId.value = it }
            fun announce(phase: String) {
                if (me != null) launch { pinRepo.announce(DeploySignal(id, me, latitude, longitude, phase)) }
            }
            announce(DeploySignal.PHASE_DEPLOYING)
            val started = currentTimeMillis()
            val result = pinRepo.deploy(id, latitude, longitude)
            delay((MIN_DEPLOY_MS - (currentTimeMillis() - started)).coerceAtLeast(0))
            result
                .onSuccess { pin ->
                    pins.update { it + (pin.id to pin) }
                    myId.value = pin.userId
                    _message.value = "Penanda ter-deploy. Ketuk penanda untuk mengisi infonya."
                }
                .onFailure { e ->
                    _message.value = when ((e as? PinException)?.error) {
                        PinError.LIMIT -> "Batas 50 penanda tercapai. Hapus penanda lama untuk membuat yang baru."
                        PinError.OFFLINE -> "Penanda butuh koneksi internet. Coba lagi saat online."
                        else -> "Penanda gagal di-deploy. Periksa koneksi lalu coba lagi."
                    }
                }
            announce(if (result.isSuccess) DeploySignal.PHASE_DEPLOYED else DeploySignal.PHASE_FAILED)
            finishDeploy(id, result.isSuccess)
            result.getOrNull()?.let { pin ->
                _justDeployed.value = pin.id
                delay(UNDO_DEPLOY_MS)
                if (_justDeployed.value == pin.id) _justDeployed.value = null
            }
        }
    }

    /**
     * Ends a deploy animation: the pop (or the failure fade), then — once the real marker is on the
     * map underneath — the animated pin goes. Same for this device's markers and other explorers'.
     */
    private suspend fun finishDeploy(id: String, saved: Boolean) {
        setDeployPhase(id, if (saved) PinDeploy.Phase.DEPLOYED else PinDeploy.Phase.FAILED)
        delay(DEPLOY_OUTRO_MS)
        if (saved) {
            // The row usually arrived by realtime already; if not, fetch it so the swap never leaves a gap.
            if (id !in pins.value) pinRepo.pin(id)?.let { p -> pins.update { it + (p.id to p) } }
            // Resting animated pin + real pin underneath (identical), then drop the animated one.
            setDeployPhase(id, PinDeploy.Phase.SETTLING)
            delay(DEPLOY_HANDOFF_MS)
        }
        deploys.update { it - id }
    }

    private fun setDeployPhase(id: String, phase: PinDeploy.Phase) {
        deploys.update { all -> all[id]?.let { all + (id to it.copy(phase = phase)) } ?: all }
    }

    private fun onPinChange(change: PinChange) {
        when (change) {
            is PinChange.Upserted -> {
                val pin = change.pin
                val known = pin.id in pins.value
                pins.update { it + (pin.id to pin) }
                // Someone else's new marker: animate it here too, unless it's already playing (its
                // "deploying" broadcast came first) — then the row just confirms it's saved.
                if (change.inserted && !known && pin.userId != myId.value) {
                    val outcome = remoteOutcomes[pin.id]
                    if (outcome != null) completeLater(outcome, REMOTE_INSERT_GRACE_MS)
                    else if (pin.id !in deploys.value) playRemoteDeploy(pin.id, pin.latitude, pin.longitude, saved = true)
                }
            }
            is PinChange.Deleted -> {
                pins.update { it - change.id }
                remoteOutcomes[change.id]?.complete(false)
            }
            is PinChange.Deploy -> onDeploySignal(change.signal)
        }
    }

    /** Another explorer's deploy animation moved on: start, pop or fade it here at the same moment. */
    private fun onDeploySignal(signal: DeploySignal) {
        if (signal.userId == myId.value) return
        val id = signal.id
        when (signal.phase) {
            DeploySignal.PHASE_DEPLOYING ->
                if (id !in deploys.value && id !in pins.value) playRemoteDeploy(id, signal.latitude, signal.longitude, saved = false)
            DeploySignal.PHASE_DEPLOYED -> remoteOutcomes[id]?.complete(true)
                ?: run { if (id !in deploys.value && id !in pins.value) playRemoteDeploy(id, signal.latitude, signal.longitude, saved = true) }
            DeploySignal.PHASE_FAILED -> remoteOutcomes[id]?.complete(false)
        }
    }

    /**
     * Plays another explorer's deploy: drop and ripples until the outcome is known, then the same pop
     * or fade their map shows. [saved]: the marker is already stored (only its row or its "deployed"
     * arrived), so it pops after a short ripple.
     */
    private fun playRemoteDeploy(id: String, latitude: Double, longitude: Double, saved: Boolean) {
        val outcome = CompletableDeferred<Boolean>()
        remoteOutcomes[id] = outcome
        deploys.update { it + (id to PinDeploy(id, latitude, longitude, PinDeploy.Phase.DEPLOYING, mine = false)) }
        if (saved) completeLater(outcome, REMOTE_INSERT_GRACE_MS)
        viewModelScope.launch {
            try {
                val started = currentTimeMillis()
                val ok = withTimeoutOrNull(REMOTE_DEPLOY_TIMEOUT_MS) { outcome.await() } ?: (id in pins.value)
                delay((REMOTE_MIN_DROP_MS - (currentTimeMillis() - started)).coerceAtLeast(0))
                finishDeploy(id, ok)
            } finally {
                remoteOutcomes.remove(id)
                deploys.update { it - id }
            }
        }
    }

    /** Resolves [outcome] as saved after [delayMs], unless its broadcast settles it first. */
    private fun completeLater(outcome: CompletableDeferred<Boolean>, delayMs: Long) {
        viewModelScope.launch {
            delay(delayMs)
            outcome.complete(true)
        }
    }

    /** Takes back the marker just deployed (a long press by mistake). */
    fun undoDeploy() {
        val id = _justDeployed.value ?: return
        _justDeployed.value = null
        deletePin(id)
    }

    /** The creator's details for a deployed marker; [onDone] gets true once saved. */
    fun savePinDetails(id: String, title: String, note: String, icon: PinIcon, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val saved = pinRepo.update(id, title, note, icon.key)
        if (saved != null) {
            pins.update { it + (saved.id to saved) }
            selectedKey.value = pinKey(saved.id)
            onDone(true)
        } else {
            _message.value = "Info penanda gagal disimpan. Periksa koneksi lalu coba lagi."
            onDone(false)
        }
    }

    /** A marker by its map key, if it's one of the viewer's own (to open its form on tap). */
    fun ownPin(key: String): MapPin? {
        val me = myId.value ?: return null
        return pins.value[key.removePrefix("pin:")]?.takeIf { key.startsWith("pin:") && it.userId == me }
    }

    fun deletePin(id: String) = viewModelScope.launch {
        if (_justDeployed.value == id) _justDeployed.value = null
        if (pinRepo.delete(id)) {
            pins.update { it - id }
            if (selectedKey.value == pinKey(id)) selectedKey.value = null
            _message.value = "Penanda dihapus"
        } else {
            _message.value = "Penanda gagal dihapus. Periksa koneksi lalu coba lagi."
        }
    }

    fun select(key: String?) {
        selectedKey.value = key
    }

    /**
     * New search text: what's on the map and coordinates match right away; places and addresses
     * are looked up (debounced) near [near], the centre of the visible map.
     */
    fun setSearchQuery(query: String, near: GeoPoint?) {
        searchJob?.cancel()
        val text = query.trim()
        val coordinates = parseCoordinates(text)
        val lookUp = text.length >= SEARCH_MIN_CHARS && coordinates == null
        _search.value = SearchUi(
            query = query,
            onMap = if (text.length >= SEARCH_MIN_CHARS) searchMap(text) else emptyList(),
            // Keep the previous places while the next ones load, so the list doesn't flash empty.
            places = if (lookUp) _search.value.places else emptyList(),
            coordinates = coordinates,
            loading = lookUp,
        )
        if (!lookUp) return
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val fix = location.lastFix.value
            placeSearch.suggest(text, near ?: fix, fix)
                .onSuccess { found -> _search.update { it.copy(places = found, loading = false, error = null) } }
                .onFailure {
                    _search.update {
                        it.copy(places = emptyList(), loading = false, error = "Pencarian tempat butuh koneksi internet. Periksa koneksi lalu coba lagi.")
                    }
                }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _search.value = SearchUi()
    }

    /** Resolves a suggestion to a spot, shows it on the map and hands it to [onReady] (to move the camera). */
    fun pickPlace(suggestion: PlaceSuggestion, onReady: (SearchedPlace) -> Unit) = viewModelScope.launch {
        placeSearch.retrieve(suggestion)
            .onSuccess { showPlace(it, onReady) }
            .onFailure { _message.value = "Lokasi “${suggestion.name}” gagal dimuat. Periksa koneksi lalu coba lagi." }
    }

    /** A recent search or typed coordinates: already has its spot. */
    fun showPlace(place: SearchedPlace, onReady: (SearchedPlace) -> Unit = {}) {
        placeSearch.remember(place)
        selectedKey.value = null
        _place.value = place
        onReady(place)
    }

    fun clearPlace() {
        _place.value = null
    }

    fun clearRecentPlaces() = placeSearch.clearRecent()

    /** Opens a search match that's on the map, turning its layer on and clearing a filter that hides it. */
    fun showOnMap(selection: MapSelection) {
        _place.value = null
        val category = when (selection) {
            is MapSelection.Own -> selection.sighting.animalCategory
            is MapSelection.Community -> selection.sighting.animalCategory
            else -> null
        }
        if (category != null && filter.value != null && filter.value != category) filter.value = null
        layers.update {
            when (selection) {
                is MapSelection.Own -> it.copy(own = true)
                is MapSelection.Community -> it.copy(community = true)
                is MapSelection.Live -> it.copy(live = true)
                is MapSelection.Pin -> it.copy(pins = true)
            }
        }
        selectedKey.value = selection.key
    }

    /** Everything on the map whose text contains every typed word (any order), nearest first. */
    private fun searchMap(query: String): List<MapSelection> {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        fun matches(vararg fields: String?) =
            fields.filterNotNull().joinToString(" ").lowercase().let { text -> words.all { it in text } }
        val me = myId.value
        val found = buildList {
            state.value.all.forEach {
                if (matches(it.animalLabel, it.animalCategory.displayName, it.note, it.locationName, it.address)) add(MapSelection.Own(it))
            }
            community.value.values.forEach {
                if (matches(it.animalLabel, it.animalCategory.displayName, it.note, it.locationName, it.displayName)) add(MapSelection.Community(it))
            }
            pins.value.values.forEach {
                if (matches(it.displayTitle, it.note, it.displayName)) add(MapSelection.Pin(it, mine = it.userId == me))
            }
            live.value.values.forEach {
                if (matches(it.name)) add(MapSelection.Live(it))
            }
        }
        val here = location.lastFix.value ?: return found.take(SEARCH_MAX_ON_MAP)
        return found
            .sortedBy { sel -> sel.latLng.let { (lat, lng) -> Geo.distanceMeters(here.latitude, here.longitude, lat, lng) } }
            .take(SEARCH_MAX_ON_MAP)
    }

    private fun parseCoordinates(text: String): GeoPoint? {
        val m = LatLngPattern.matchEntire(text) ?: return null
        val lat = m.groupValues[1].toDoubleOrNull() ?: return null
        val lng = m.groupValues[2].toDoubleOrNull() ?: return null
        return if (lat in -90.0..90.0 && lng in -180.0..180.0) GeoPoint(lat, lng) else null
    }

    fun toggleFavorite(id: Long) = viewModelScope.launch { repository.toggleFavorite(id) }

    fun refreshLocation(onFix: (GeoPoint) -> Unit = {}) = viewModelScope.launch {
        location.currentLocation()?.let(onFix)
    }
}
