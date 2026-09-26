package com.faunary.app.remote

import android.annotation.SuppressLint
import android.os.Looper
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.faunary.app.data.SettingsRepository
import com.faunary.app.location.LocationRepository
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
import kotlinx.coroutines.flow.distinctUntilChanged
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
                if (active && location.hasPermission()) locationUpdates()
                else {
                    stopSharing()
                    emptyFlow()
                }
            }
            .onEach { publish(it.first, it.second, it.third) }
            .launchIn(scope)
    }

    @SuppressLint("MissingPermission")
    private fun locationUpdates() = callbackFlow {
        // No minimum distance: a fix every 30s doubles as a heartbeat, so explorers standing still
        // (e.g. waiting for a bird) don't fall past the server's 5-minute freshness window.
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 30_000)
            .setMinUpdateIntervalMillis(15_000)
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
        val client = supabase.client ?: return
        val uid = supabase.currentUserId() ?: return
        scope.launch {
            runCatching { client.from("live_locations").delete { filter { eq("user_id", uid) } } }
        }
    }
}
