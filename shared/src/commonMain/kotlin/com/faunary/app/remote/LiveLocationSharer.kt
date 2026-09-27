package com.faunary.app.remote

import com.faunary.app.data.SettingsRepository
import com.faunary.app.location.LocationSource
import com.faunary.app.util.Geo
import com.faunary.app.util.Log
import com.faunary.app.util.appInForeground
import com.faunary.app.util.currentTimeMillis
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Publishes the user's position to `live_locations` while (a) they opted in, (b) the app is in
 * the foreground and (c) location permission is granted. Turning any of those off removes the row,
 * so nobody sees a stale position. No background location is ever collected.
 */
class LiveLocationSharer(
    private val supabase: SupabaseProvider,
    private val settings: SettingsRepository,
    private val location: LocationSource,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        if (!supabase.isConfigured) return
        val foreground = appInForeground()
        combine(foreground, settings.settings.map { it.shareLiveLocation }) { fg, share -> fg && share }
            .distinctUntilChanged()
            .flatMapLatest { active ->
                if (active) {
                    // Sharing is often switched on before location is allowed: start as soon as it is.
                    flow {
                        while (!location.hasPermission()) delay(PERMISSION_POLL_MS)
                        emitAll(locationUpdates())
                    }
                } else {
                    stopSharing()
                    emptyFlow()
                }
            }
            .onEach { (lat, lng, acc) -> if (shouldPublish(lat, lng)) publish(lat, lng, acc) }
            .launchIn(scope)
    }

    private var lastSent: Triple<Double, Double, Long>? = null

    /**
     * GPS arrives every ~5 s; send it when the explorer has moved [MOVE_THRESHOLD_M] or more, and
     * otherwise at least every [HEARTBEAT_MS] so standing still doesn't look like leaving.
     */
    private fun shouldPublish(lat: Double, lng: Double): Boolean {
        val now = currentTimeMillis()
        val prev = lastSent
        val moved = prev == null || Geo.distanceMeters(prev.first, prev.second, lat, lng) >= MOVE_THRESHOLD_M
        val stale = prev == null || now - prev.third >= HEARTBEAT_MS
        if (!moved && !stale) return false
        lastSent = Triple(lat, lng, now)
        return true
    }

    /**
     * GPS-grade fixes every ~5 s (only while the app is open and sharing is on); shouldPublish()
     * decides which ones are worth sending. CoreLocation wants the main thread, so it is collected there.
     */
    private fun locationUpdates() = location.updates(FIX_INTERVAL_MS)
        .map { Triple(it.latitude, it.longitude, it.accuracy) }
        .flowOn(Dispatchers.Main)

    private suspend fun publish(lat: Double, lng: Double, accuracy: Float?) {
        val client = supabase.client ?: return
        val uid = supabase.ensureUserId() ?: return
        runCatching {
            client.from("live_locations").upsert(
                LiveLocationDto(uid, settings.settings.value.explorerName, lat, lng, accuracy),
            )
        }.onFailure { Log.w("FaunaryLive", "publish failed", it) }
    }

    private fun stopSharing() {
        lastSent = null
        val client = supabase.client ?: return
        val uid = supabase.currentUserId() ?: return
        scope.launch {
            runCatching { client.from("live_locations").delete { filter { eq("user_id", uid) } } }
        }
    }
}

private const val FIX_INTERVAL_MS = 5_000L
private const val MOVE_THRESHOLD_M = 10.0
private const val HEARTBEAT_MS = 30_000L
private const val PERMISSION_POLL_MS = 2_000L
