package com.faunary.app.remote

import com.faunary.app.util.Log
import com.faunary.app.util.randomUuid
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Row of the `notification_list` view: someone liked or commented on one of the user's finds. */
@Serializable
data class AppNotification(
    val id: String,
    val type: String,
    @SerialName("sighting_id") val sightingId: String,
    @SerialName("comment_id") val commentId: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("actor_id") val actorId: String,
    @SerialName("actor_name") val actorName: String = "Penjelajah",
    @SerialName("comment_body") val commentBody: String? = null,
    @SerialName("animal_label") val animalLabel: String,
    @SerialName("photo_path") val photoPath: String,
) {
    val isLike: Boolean get() = type == "like"
    val isRead: Boolean get() = readAt != null
    val photoUrl: String get() = publicPhotoUrl(photoPath)

    @OptIn(ExperimentalTime::class)
    val createdAtMs: Long get() = runCatching { Instant.parse(createdAt).toEpochMilliseconds() }.getOrDefault(0L)

    /** "Mandaaa menyukai foto Anjing-mu" / "Mandaaa mengomentari foto Anjing-mu". */
    val headline: String
        get() = if (isLike) "$actorName menyukai foto $animalLabel-mu" else "$actorName mengomentari foto $animalLabel-mu"
}

/** The signed-in user's like/comment notifications. Returns null/false on failure. */
class NotificationRepository(private val supabase: SupabaseProvider) {

    val isAvailable: Boolean get() = supabase.isConfigured

    suspend fun list(limit: Long = 100): List<AppNotification>? {
        val client = supabase.client ?: return null
        supabase.ensureUserId() ?: return null
        return runCatching {
            client.from("notification_list").select {
                order("created_at", Order.DESCENDING)
                limit(limit)
            }.decodeList<AppNotification>()
        }.onFailure { Log.w(TAG, "list failed", it) }.getOrNull()
    }

    @OptIn(ExperimentalTime::class)
    suspend fun markAllRead(): Boolean {
        val client = supabase.client ?: return false
        val me = supabase.ensureUserId() ?: return false
        return runCatching {
            client.from("notifications").update(buildJsonObject { put("read_at", Clock.System.now().toString()) }) {
                filter {
                    eq("recipient_id", me)
                    exact("read_at", null)
                }
            }
        }.onFailure { Log.w(TAG, "mark read failed", it) }.isSuccess
    }

    @OptIn(ExperimentalTime::class)
    suspend fun markRead(id: String): Boolean {
        val client = supabase.client ?: return false
        supabase.ensureUserId() ?: return false
        return runCatching {
            client.from("notifications").update(buildJsonObject { put("read_at", Clock.System.now().toString()) }) {
                filter { eq("id", id) }
            }
        }.isSuccess
    }

    /** Emits on every new/changed/removed notification of the user (RLS limits events to their rows). */
    fun changes(): Flow<Unit> {
        val client = supabase.client ?: return emptyFlow()
        return flow {
            supabase.ensureUserId() ?: return@flow
            val channel = client.channel("notifications-${randomUuid()}")
            val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "notifications" }
            channel.subscribe()
            try {
                changes.collect { emit(Unit) }
            } finally {
                client.realtime.removeChannel(channel)
            }
        }.retryWhen { cause, attempt ->
            if (cause is CancellationException) return@retryWhen false
            Log.w(TAG, "realtime notifications failed (attempt $attempt), retrying", cause)
            delay(2_000L shl attempt.toInt().coerceAtMost(4))
            true
        }
    }

    private companion object {
        const val TAG = "FaunaryNotify"
    }
}
