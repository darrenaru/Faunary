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
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.retryWhen
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Row of the `sighting_social` view. */
@Serializable
data class SocialStats(
    @SerialName("sighting_id") val sightingId: String,
    @SerialName("like_count") val likeCount: Int = 0,
    @SerialName("comment_count") val commentCount: Int = 0,
    @SerialName("liked_by_me") val likedByMe: Boolean = false,
)

/** Row of the `sighting_comment_list` view. */
@Serializable
data class SightingComment(
    val id: String,
    @SerialName("sighting_id") val sightingId: String,
    @SerialName("user_id") val userId: String,
    val body: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("display_name") val displayName: String = "Penjelajah",
) {
    @OptIn(ExperimentalTime::class)
    val createdAtMs: Long get() = runCatching { Instant.parse(createdAt).toEpochMilliseconds() }.getOrDefault(0L)
}

@Serializable
private data class LikeDto(@SerialName("sighting_id") val sightingId: String)

@Serializable
private data class NewCommentDto(@SerialName("sighting_id") val sightingId: String, val body: String)

/** Likes and comments on any sighting (by its server id). Every call returns null/false on failure. */
@Singleton
class SocialRepository @Inject constructor(private val supabase: SupabaseProvider) {

    val isAvailable: Boolean get() = supabase.isConfigured

    suspend fun myId(): String? = supabase.ensureUserId()

    suspend fun stats(sightingId: String): SocialStats? {
        val client = supabase.client ?: return null
        supabase.ensureUserId() ?: return null
        return runCatching {
            client.from("sighting_social").select { filter { eq("sighting_id", sightingId) } }
                .decodeSingleOrNull<SocialStats>() ?: SocialStats(sightingId)
        }.onFailure { Log.w(TAG, "stats failed", it) }.getOrNull()
    }

    suspend fun comments(sightingId: String): List<SightingComment>? {
        val client = supabase.client ?: return null
        supabase.ensureUserId() ?: return null
        return runCatching {
            client.from("sighting_comment_list").select {
                filter { eq("sighting_id", sightingId) }
                order("created_at", Order.ASCENDING)
                limit(300)
            }.decodeList<SightingComment>()
        }.onFailure { Log.w(TAG, "comments failed", it) }.getOrNull()
    }

    suspend fun setLiked(sightingId: String, liked: Boolean): Boolean {
        val client = supabase.client ?: return false
        val me = supabase.ensureUserId() ?: return false
        return runCatching {
            if (liked) {
                client.from("sighting_likes").upsert(LikeDto(sightingId)) { ignoreDuplicates = true }
            } else {
                client.from("sighting_likes").delete {
                    filter {
                        eq("sighting_id", sightingId)
                        eq("user_id", me)
                    }
                }
            }
        }.onFailure { Log.w(TAG, "like failed", it) }.isSuccess
    }

    suspend fun addComment(sightingId: String, body: String): Boolean {
        val client = supabase.client ?: return false
        supabase.ensureUserId() ?: return false
        return runCatching {
            client.from("sighting_comments").insert(NewCommentDto(sightingId, body))
        }.onFailure { Log.w(TAG, "comment failed", it) }.isSuccess
    }

    suspend fun deleteComment(commentId: String): Boolean {
        val client = supabase.client ?: return false
        supabase.ensureUserId() ?: return false
        return runCatching {
            client.from("sighting_comments").delete { filter { eq("id", commentId) } }
        }.onFailure { Log.w(TAG, "delete comment failed", it) }.isSuccess
    }

    /**
     * Emits whenever a like or comment on [sightingId] changes. Realtime can't filter deletes,
     * so events are matched here on the (full) old/new record.
     */
    fun changes(sightingId: String): Flow<Unit> {
        val client = supabase.client ?: return emptyFlow()
        return flow {
            supabase.ensureUserId() ?: return@flow
            val channel = client.channel("social-$sightingId-${UUID.randomUUID()}")
            val likes = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "sighting_likes" }
            val comments = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "sighting_comments" }
            channel.subscribe()
            try {
                merge(likes, comments).collect { action ->
                    val record = when (action) {
                        is PostgresAction.Insert -> action.record
                        is PostgresAction.Update -> action.record
                        is PostgresAction.Delete -> action.oldRecord
                        else -> null
                    }
                    if (record?.get("sighting_id")?.jsonPrimitive?.content == sightingId) emit(Unit)
                }
            } finally {
                client.realtime.removeChannel(channel)
            }
        }.retryWhen { cause, attempt ->
            if (cause is CancellationException) return@retryWhen false
            Log.w(TAG, "realtime social failed (attempt $attempt), retrying", cause)
            delay(2_000L shl attempt.toInt().coerceAtMost(4))
            true
        }
    }

    private companion object {
        const val TAG = "FaunarySocial"
    }
}
