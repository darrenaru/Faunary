package com.faunary.app.remote

import android.annotation.SuppressLint
import android.os.Looper
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.faunary.app.data.SettingsRepository
import com.faunary.app.location.LocationRepository
import com.faunary.app.util.Geo
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publishes the user's position to `live_locations` while (a) they opted in, (b) the app is in
 * the foreground and (c) location permission is granted. Turning any of those off removes the row,
 * so nobody sees a stale position. No background location is ever collected.
 */
@Singleton
class LiveLocationSharer @Inject constructor(
    private val supabase: SupabaseProvider,
    private val settings: SettingsRepository,
    private val location: LocationRepository,
    private val fused: FusedLocationProviderClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        if (!supabase.isConfigured) return
        val foreground = ProcessLifecycleOwner.get().lifecycle.currentStateFlow
            .map { it.isAtLeast(Lifecycle.State.STARTED) }
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
        val now = System.currentTimeMillis()
        val prev = lastSent
        val moved = prev == null || Geo.distanceMeters(prev.first, prev.second, lat, lng) >= MOVE_THRESHOLD_M
        val stale = prev == null || now - prev.third >= HEARTBEAT_MS
        if (!moved && !stale) return false
        lastSent = Triple(lat, lng, now)
        return true
    }

    @SuppressLint("MissingPermission")
    private fun locationUpdates() = callbackFlow {
        // GPS-grade fixes every ~5 s (only while the app is open and sharing is on);
        // shouldPublish() decides which ones are worth sending.
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, FIX_INTERVAL_MS)
            .setMinUpdateIntervalMillis(FIX_INTERVAL_MS / 2)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(Triple(it.latitude, it.longitude, if (it.hasAccuracy()) it.accuracy else null)) }
            }
        }
        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { fused.removeLocationUpdates(callback) }
    }

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
