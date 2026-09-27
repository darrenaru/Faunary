package com.faunary.app.ui.detail

import com.faunary.app.remote.SightingComment
import com.faunary.app.remote.SocialRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val MAX_COMMENT_LENGTH = 500

data class SocialUi(
    /** False when online features are off, or the entry isn't uploaded yet ([waitingForSync]). */
    val enabled: Boolean = false,
    val waitingForSync: Boolean = false,
    val loading: Boolean = true,
    val failed: Boolean = false,
    val likeCount: Int = 0,
    val liked: Boolean = false,
    val comments: List<SightingComment> = emptyList(),
    val me: String? = null,
    /** The viewer owns the sighting, so they may remove anyone's comment on it. */
    val moderator: Boolean = false,
    val sending: Boolean = false,
    val sendFailed: Boolean = false,
) {
    fun canDelete(comment: SightingComment) = moderator || comment.userId == me
}

/** Likes/comments for one sighting, shared by the own and community detail view models. */
class SightingSocial(private val repo: SocialRepository, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(SocialUi())
    val state: StateFlow<SocialUi> = _state.asStateFlow()
    private var boundId: String? = null
    private var job: Job? = null

    /** [remoteId] null = not uploaded yet (own entry waiting for sync). Re-binding the same id is a no-op. */
    fun bind(remoteId: String?, moderator: Boolean) {
        if (!repo.isAvailable) {
            _state.value = SocialUi(enabled = false, loading = false)
            return
        }
        if (remoteId == null) {
            boundId = null
            job?.cancel()
            _state.value = SocialUi(enabled = false, waitingForSync = true, loading = false)
            return
        }
        if (remoteId == boundId) return
        boundId = remoteId
        job?.cancel()
        _state.value = SocialUi(enabled = true, moderator = moderator)
        job = scope.launch {
            _state.update { it.copy(me = repo.myId()) }
            refresh()
            repo.changes(remoteId).collect { refresh() }
        }
    }

    fun retry() = scope.launch {
        _state.update { it.copy(loading = true, failed = false) }
        refresh()
    }

    private suspend fun refresh() {
        val id = boundId ?: return
        val stats = repo.stats(id)
        val comments = repo.comments(id)
        _state.update {
            if (stats == null || comments == null) it.copy(loading = false, failed = it.comments.isEmpty() && it.likeCount == 0)
            else it.copy(loading = false, failed = false, likeCount = stats.likeCount, liked = stats.likedByMe, comments = comments)
        }
    }

    /** Optimistic: flips at once, rolls back if the server refuses. */
    fun toggleLike() {
        val id = boundId ?: return
        val before = _state.value
        val liked = !before.liked
        _state.update { it.copy(liked = liked, likeCount = (it.likeCount + if (liked) 1 else -1).coerceAtLeast(0)) }
        scope.launch {
            if (!repo.setLiked(id, liked)) _state.update { it.copy(liked = before.liked, likeCount = before.likeCount) }
        }
    }

    /** @param onSent called when the comment was stored, so the input can be cleared. */
    fun send(text: String, onSent: () -> Unit) {
        val id = boundId ?: return
        val body = text.trim().take(MAX_COMMENT_LENGTH)
        if (body.isEmpty() || _state.value.sending) return
        _state.update { it.copy(sending = true, sendFailed = false) }
        scope.launch {
            val ok = repo.addComment(id, body)
            _state.update { it.copy(sending = false, sendFailed = !ok) }
            if (ok) {
                onSent()
                refresh()
            }
        }
    }

    fun delete(comment: SightingComment) {
        _state.update { s -> s.copy(comments = s.comments.filterNot { it.id == comment.id }) }
        scope.launch { if (!repo.deleteComment(comment.id)) refresh() }
    }
}
