package com.faunary.app.remote

import android.util.Log
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Row of the `map_pin_list` view: a spot someone marked on the shared map. */
@Serializable
data class MapPin(
    val id: String,
    @SerialName("user_id") val userId: String,
    /** Null right after deployment, until the creator fills in the details. */
    val title: String? = null,
    val note: String? = null,
    val latitude: Double,
    val longitude: Double,
    @SerialName("created_at") val createdAt: String,
    @SerialName("display_name") val displayName: String = "Penjelajah",
    /** flag, leaf, camera, paw, star or heart. */
    val icon: String = "flag",
) {
    val isDraft: Boolean get() = title.isNullOrBlank()
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: "Penanda baru"
}

@Serializable
private data class NewMapPin(
    val title: String?,
    val note: String?,
    val latitude: Double,
    val longitude: Double,
    val icon: String,
)

sealed interface PinChange {
    data class Upserted(val pin: MapPin) : PinChange
    data class Deleted(val id: String) : PinChange
}

/** Why creating a marker failed, so the UI can say something useful. */
enum class PinError { OFFLINE, LIMIT, FAILED }

@Serializable
private data class PinDetails(val title: String, val note: String?, val icon: String)

/**
 * Shared map markers: anyone signed in sees them. A marker is deployed first (no details yet) and
 * filled in by its creator, who is also the only one who can edit or delete it.
 */
@Singleton
class PinRepository @Inject constructor(private val supabase: SupabaseProvider) {

    val isAvailable: Boolean get() = supabase.isConfigured

    suspend fun myId(): String? = supabase.ensureUserId()

    /** Newest markers everywhere (there are few, unlike photos). Null on failure. */
    suspend fun all(limit: Long = 1_000): List<MapPin>? {
        val client = supabase.client ?: return null
        supabase.ensureUserId() ?: return null
        return runCatching {
            client.from("map_pin_list").select {
                order("created_at", Order.DESCENDING)
                limit(limit)
            }.decodeList<MapPin>()
        }.onFailure { Log.w(TAG, "fetch pins failed", it) }.getOrNull()
    }

    private suspend fun pin(id: String): MapPin? {
        val client = supabase.client ?: return null
        return runCatching {
            client.from("map_pin_list").select { filter { eq("id", id) } }.decodeSingleOrNull<MapPin>()
        }.getOrNull()
    }

    /** Deploys a marker at the spot (details come later via [update]). @return it, or why it couldn't be made. */
    suspend fun deploy(latitude: Double, longitude: Double): Result<MapPin> =
        create(title = null, note = null, latitude = latitude, longitude = longitude, icon = "flag")

    private suspend fun create(title: String?, note: String?, latitude: Double, longitude: Double, icon: String): Result<MapPin> {
        val client = supabase.client ?: return Result.failure(PinException(PinError.OFFLINE))
        supabase.ensureUserId() ?: return Result.failure(PinException(PinError.OFFLINE))
        return runCatching {
            client.from("map_pins").insert(NewMapPin(title?.trim()?.ifEmpty { null }, note?.trim()?.ifEmpty { null }, latitude, longitude, icon)) {
                select()
            }.decodeSingle<IdOnly>().id
        }.mapCatching { id -> pin(id) ?: error("created pin not readable") }
            .recoverCatching { e ->
                Log.w(TAG, "create pin failed", e)
                throw PinException(if (e.message?.contains("map pin limit") == true) PinError.LIMIT else PinError.FAILED)
            }
    }

    /** Saves the creator's details; everyone sees them through realtime. Null on failure. */
    suspend fun update(id: String, title: String, note: String?, icon: String): MapPin? {
        val client = supabase.client ?: return null
        supabase.ensureUserId() ?: return null
        return runCatching {
            client.from("map_pins").update(PinDetails(title.trim(), note?.trim()?.ifEmpty { null }, icon)) {
                filter { eq("id", id) }
            }
            pin(id)
        }.onFailure { Log.w(TAG, "update pin failed", it) }.getOrNull()
    }

    suspend fun delete(id: String): Boolean {
        val client = supabase.client ?: return false
        supabase.ensureUserId() ?: return false
        return runCatching {
            client.from("map_pins").delete { filter { eq("id", id) } }
        }.onFailure { Log.w(TAG, "delete pin failed", it) }.isSuccess
    }

    /** Live adds/removals, so a marker appears on everyone's map as soon as it's saved. */
    fun changes(): Flow<PinChange> {
        val client = supabase.client ?: return emptyFlow()
        return flow {
            supabase.ensureUserId() ?: return@flow
            val channel = client.channel("map-pins-${UUID.randomUUID()}")
            val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "map_pins" }
            channel.subscribe()
            try {
                changes.collect { action ->
                    // Inserts are re-read through the view to get the creator's name.
                    val upserted = when (action) {
                        is PostgresAction.Insert -> action.record
                        is PostgresAction.Update -> action.record
                        else -> null
                    }
                    upserted?.get("id")?.jsonPrimitive?.content?.let { pin(it) }?.let { emit(PinChange.Upserted(it)) }
                    when (action) {
                        is PostgresAction.Delete ->
                            action.oldRecord["id"]?.jsonPrimitive?.content?.let { emit(PinChange.Deleted(it)) }
                        else -> Unit
                    }
                }
            } finally {
                client.realtime.removeChannel(channel)
            }
        }.retryWhen { cause, attempt ->
            if (cause is CancellationException) return@retryWhen false
            Log.w(TAG, "realtime pins failed (attempt $attempt), retrying", cause)
            delay(2_000L shl attempt.toInt().coerceAtMost(4))
            true
        }
    }

    @Serializable
    private data class IdOnly(val id: String)

    private companion object {
        const val TAG = "FaunaryPins"
    }
}

class PinException(val error: PinError) : Exception(error.name)
