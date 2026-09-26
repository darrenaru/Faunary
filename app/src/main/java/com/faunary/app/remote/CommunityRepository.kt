package com.faunary.app.remote

import android.util.Log
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class Bounds(val south: Double, val west: Double, val north: Double, val east: Double) {
    fun contains(lat: Double, lng: Double) = lat in south..north && lng in west..east
}

sealed interface SightingChange {
    data class Upserted(val sighting: CommunitySighting) : SightingChange
    data class Deleted(val id: String) : SightingChange
}

sealed interface LiveChange {
    data class Moved(val location: LiveLocationDto) : LiveChange
    data class Left(val userId: String) : LiveChange
}

/** Read side of the shared map: other people's sightings and live positions. */
@Singleton
class CommunityRepository @Inject constructor(private val supabase: SupabaseProvider) {

    val isAvailable: Boolean get() = supabase.isConfigured

    /** Most recent finds anywhere, so distant sightings are on the map without panning to them. Null on failure. */
    suspend fun recentSightings(limit: Long = 500): List<CommunitySighting>? {
        val client = supabase.client ?: return null
        val me = supabase.ensureUserId() ?: return null
        return runCatching {
            client.from("community_sightings").select {
                filter { neq("user_id", me) }
                order("taken_at_ms", Order.DESCENDING)
                limit(limit)
            }.decodeList<CommunitySighting>()
        }.onFailure { Log.w(TAG, "fetch recent failed", it) }.getOrNull()
    }

    /** Finds inside [bounds] (for dense areas beyond the recent set). Null on failure so callers can retry. */
    suspend fun sightingsIn(bounds: Bounds, limit: Long = 300): List<CommunitySighting>? {
        val client = supabase.client ?: return null
        val me = supabase.ensureUserId() ?: return null
        return runCatching {
            client.from("community_sightings").select {
                filter {
                    gte("latitude", bounds.south)
                    lte("latitude", bounds.north)
                    gte("longitude", bounds.west)
                    lte("longitude", bounds.east)
                    neq("user_id", me)
                }
                order("taken_at_ms", Order.DESCENDING)
                limit(limit)
            }.decodeList<CommunitySighting>()
        }.onFailure { Log.w(TAG, "fetch sightings failed", it) }.getOrNull()
    }

    suspend fun sighting(id: String): CommunitySighting? {
        val client = supabase.client ?: return null
        supabase.ensureUserId() ?: return null
        return runCatching {
            client.from("community_sightings").select { filter { eq("id", id) } }.decodeSingleOrNull<CommunitySighting>()
        }.getOrNull()
    }

    /** Live inserts/updates/deletes of other users' sightings. */
    fun sightingChanges(): Flow<SightingChange> {
        val client = supabase.client ?: return emptyFlow()
        return flow {
            val me = supabase.ensureUserId() ?: return@flow
            val channel = client.channel("public-sightings-${UUID.randomUUID()}")
            val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "sightings" }
            channel.subscribe()
            try {
                changes.collect { action ->
                    when (action) {
                        is PostgresAction.Insert -> emitUpsert(action.record, me)
                        is PostgresAction.Update -> emitUpsert(action.record, me)
                        is PostgresAction.Delete -> {
                            action.oldRecord["id"]?.jsonPrimitive?.content?.let { emit(SightingChange.Deleted(it)) }
                        }
                        else -> Unit
                    }
                }
            } finally {
                client.realtime.removeChannel(channel)
            }
        }.resilient("sightings")
    }

    private suspend fun FlowCollector<SightingChange>.emitUpsert(record: JsonObject, me: String) {
        val id = record["id"]?.jsonPrimitive?.content ?: return
        if (record["user_id"]?.jsonPrimitive?.content == me) return
        // Re-read through the view to get the finder's display name.
        sighting(id)?.let { emit(SightingChange.Upserted(it)) }
    }

    suspend fun liveLocations(): List<LiveLocationDto> {
        val client = supabase.client ?: return emptyList()
        val me = supabase.ensureUserId() ?: return emptyList()
        return runCatching {
            client.from("live_locations").select { filter { neq("user_id", me) } }.decodeList<LiveLocationDto>()
        }.onFailure { Log.w(TAG, "fetch live failed", it) }.getOrDefault(emptyList())
    }

    fun liveChanges(): Flow<LiveChange> {
        val client = supabase.client ?: return emptyFlow()
        return flow {
            val me = supabase.ensureUserId() ?: return@flow
            val channel = client.channel("live-locations-${UUID.randomUUID()}")
            val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "live_locations" }
            channel.subscribe()
            try {
                changes.collect { action ->
                    when (action) {
                        is PostgresAction.Insert -> action.decodeRecord<LiveLocationDto>()
                        is PostgresAction.Update -> action.decodeRecord<LiveLocationDto>()
                        is PostgresAction.Delete -> {
                            action.oldRecord["user_id"]?.jsonPrimitive?.content
                                ?.takeIf { it != me }?.let { emit(LiveChange.Left(it)) }
                            null
                        }
                        else -> null
                    }?.takeIf { it.userId != me }?.let { emit(LiveChange.Moved(it)) }
                }
            } finally {
                client.realtime.removeChannel(channel)
            }
        }.resilient("live")
    }

    /** Realtime hiccups (network drops, server restarts) must never crash the UI: log and resubscribe. */
    private fun <T> Flow<T>.resilient(name: String): Flow<T> = retryWhen { cause, attempt ->
        if (cause is CancellationException) return@retryWhen false
        Log.w(TAG, "realtime $name failed (attempt $attempt), retrying", cause)
        delay((2_000L shl attempt.toInt().coerceAtMost(4)))
        true
    }

    private companion object {
        const val TAG = "FaunaryCommunity"
    }
}
