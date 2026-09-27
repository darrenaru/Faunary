package com.faunary.app.remote

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.faunary.app.data.SettingsRepository
import com.faunary.app.util.Geo
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Row of `live_routes`: where an explorer is heading and the route still ahead of them. */
@Serializable
data class LiveRouteDto(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("dest_label") val destLabel: String,
    @SerialName("dest_lat") val destLat: Double,
    @SerialName("dest_lng") val destLng: Double,
    /** walking or driving. */
    val mode: String,
    /** [[lat, lng], …] ahead of them. */
    val path: List<List<Double>>,
    @SerialName("remaining_meters") val remainingMeters: Float? = null,
    /** When this route was drawn (sender's clock, ms): a just-started route is drawn in with an animation. */
    @SerialName("started_at") val startedAt: Long = 0,
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    val points: List<Pair<Double, Double>> get() = path.mapNotNull { if (it.size >= 2) it[0] to it[1] else null }
}

@Serializable
private data class NewLiveRoute(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("dest_label") val destLabel: String,
    @SerialName("dest_lat") val destLat: Double,
    @SerialName("dest_lng") val destLng: Double,
    val mode: String,
    val path: List<List<Double>>,
    @SerialName("remaining_meters") val remainingMeters: Float?,
    @SerialName("started_at") val startedAt: Long,
)

/** The user's current trip as the map knows it; shared while live location sharing is on. */
data class SharedTrip(
    val destLat: Double,
    val destLng: Double,
    val destLabel: String,
    /** walking or driving. */
    val mode: String,
    /** Route ahead, (lat, lng). */
    val path: List<Pair<Double, Double>>,
    val remainingMeters: Double?,
    /** When the route was drawn (ms). */
    val startedAt: Long,
)

sealed interface LiveRouteChange {
    data class Updated(val route: LiveRouteDto) : LiveRouteChange
    data class Ended(val userId: String) : LiveRouteChange
}

/**
 * Explorers' routes. The own route is published while (a) live location sharing is on, (b) the app is
 * in front and (c) there is a trip; the row is removed as soon as any of that stops, so nobody sees a
 * stale route (the server also hides rows not refreshed for a minute, e.g. after the app was killed).
 * Others' routes are read and followed.
 */
@Singleton
class LiveRouteRepository @Inject constructor(
    private val supabase: SupabaseProvider,
    private val settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val trip = MutableStateFlow<SharedTrip?>(null)
    private var started = false
    /** Whether our row may be on the server (so it's only deleted when there's something to delete). */
    @Volatile private var published = false
    /** Whether sharing is currently on; a publish that finishes after it turned off is taken back. */
    @Volatile private var sharing = false
    @Volatile private var lastPublishAt = 0L

    val isAvailable: Boolean get() = supabase.isConfigured

    /** The map's current trip (null when there's none or it's finished). */
    fun setTrip(value: SharedTrip?) {
        trip.value = value
        startIfNeeded()
    }

    private fun startIfNeeded() {
        if (started || !supabase.isConfigured) return
        started = true
        val foreground = ProcessLifecycleOwner.get().lifecycle.currentStateFlow.map { it.isAtLeast(Lifecycle.State.STARTED) }
        val active = combine(trip, foreground, settings.settings.map { it.shareLiveLocation }) { t, fg, share ->
            t?.takeIf { fg && share }
        }
        // Ends right away when the trip / sharing / foreground stops. Otherwise publishes the latest state
        // at most every PUBLISH_INTERVAL_MS, and again every HEARTBEAT_MS while it doesn't change
        // (standing still), so the server never takes an ongoing trip for a stale one.
        scope.launch {
            active.collectLatest { t ->
                if (t == null) {
                    sharing = false
                    clear()
                    return@collectLatest
                }
                sharing = true
                while (true) {
                    val wait = PUBLISH_INTERVAL_MS - (System.currentTimeMillis() - lastPublishAt)
                    if (wait > 0) delay(wait)
                    // A newer state cancels this block; never cut an upload off halfway.
                    withContext(NonCancellable) { publish(t) }
                    delay(HEARTBEAT_MS)
                }
            }
        }
    }

    private suspend fun publish(t: SharedTrip) {
        if (!sharing) return
        lastPublishAt = System.currentTimeMillis()
        val client = supabase.client ?: return
        val uid = supabase.ensureUserId() ?: return
        val path = simplify(t.path)
        if (path.size < 2) return
        runCatching {
            client.from("live_routes").upsert(
                NewLiveRoute(
                    userId = uid,
                    displayName = settings.settings.value.explorerName,
                    destLabel = t.destLabel.take(80).ifBlank { "Tujuan" },
                    destLat = t.destLat,
                    destLng = t.destLng,
                    mode = t.mode,
                    path = path.map { (lat, lng) -> listOf(lat, lng) },
                    remainingMeters = t.remainingMeters?.toFloat(),
                    startedAt = t.startedAt,
                ),
            )
            published = true
            // Trip ended while this was on its way: remove it again instead of leaving a stale route.
            if (!sharing) clear()
        }.onFailure { Log.w(TAG, "publish route failed", it) }
    }

    private fun clear() {
        if (!published) return
        val client = supabase.client ?: return
        val uid = supabase.currentUserId() ?: return
        published = false
        scope.launch {
            runCatching { client.from("live_routes").delete { filter { eq("user_id", uid) } } }
                .onFailure { Log.w(TAG, "clear route failed", it) }
        }
    }

    /** Others' current routes. */
    suspend fun others(): List<LiveRouteDto> {
        val client = supabase.client ?: return emptyList()
        val me = supabase.ensureUserId() ?: return emptyList()
        return runCatching {
            client.from("live_routes").select { filter { neq("user_id", me) } }.decodeList<LiveRouteDto>()
        }.onFailure { Log.w(TAG, "fetch routes failed", it) }.getOrDefault(emptyList())
    }

    /** Others' routes as they move and end. */
    fun changes(): Flow<LiveRouteChange> {
        val client = supabase.client ?: return emptyFlow()
        return flow {
            val me = supabase.ensureUserId() ?: return@flow
            val channel = client.channel("live-routes-${UUID.randomUUID()}")
            val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "live_routes" }
            channel.subscribe()
            try {
                changes.collect { action ->
                    when (action) {
                        is PostgresAction.Insert -> action.decodeRecord<LiveRouteDto>()
                        is PostgresAction.Update -> action.decodeRecord<LiveRouteDto>()
                        is PostgresAction.Delete -> {
                            action.oldRecord["user_id"]?.jsonPrimitive?.content
                                ?.takeIf { it != me }?.let { emit(LiveRouteChange.Ended(it)) }
                            null
                        }
                        else -> null
                    }?.takeIf { it.userId != me }?.let { emit(LiveRouteChange.Updated(it)) }
                }
            } finally {
                client.realtime.removeChannel(channel)
            }
        }.retryWhen { cause, attempt ->
            if (cause is CancellationException) return@retryWhen false
            Log.w(TAG, "realtime routes failed (attempt $attempt), retrying", cause)
            delay(2_000L shl attempt.toInt().coerceAtMost(4))
            true
        }
    }

    private companion object {
        const val TAG = "FaunaryLiveRoute"
        const val PUBLISH_INTERVAL_MS = 5_000L
        const val HEARTBEAT_MS = 20_000L
        const val MIN_SPACING_M = 8.0
        const val MAX_POINTS = 400

        /** Drops points closer than [MIN_SPACING_M] (ends kept) and thins long routes to [MAX_POINTS]. */
        fun simplify(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
            if (points.size <= 2) return points
            val kept = mutableListOf(points.first())
            for (i in 1 until points.size - 1) {
                val last = kept.last()
                if (Geo.distanceMeters(last.first, last.second, points[i].first, points[i].second) >= MIN_SPACING_M) kept += points[i]
            }
            kept += points.last()
            if (kept.size <= MAX_POINTS) return kept
            val step = kept.size.toDouble() / (MAX_POINTS - 1)
            return (0 until MAX_POINTS - 1).map { kept[(it * step).toInt()] } + kept.last()
        }
    }
}
