package com.faunary.app.ui.detail

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.ThumbUpOffAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.faunary.app.remote.SightingComment
import com.faunary.app.ui.components.FaunaryCard
import com.faunary.app.ui.components.FaunaryTextField
import com.faunary.app.ui.theme.FaunaryTheme
import com.faunary.app.util.Format

/** Like button, comment list and comment box. */
@Composable
fun SocialSection(
    state: SocialUi,
    onToggleLike: () -> Unit,
    onSend: (String, onSent: () -> Unit) -> Unit,
    onDelete: (SightingComment) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = FaunaryTheme.colors
    if (!state.enabled && !state.waitingForSync) return
    FaunaryCard(modifier) {
        if (state.waitingForSync) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CloudSync, null, Modifier.size(20.dp), tint = c.foregroundMuted)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Suka & komentar aktif setelah temuan ini terunggah. Pastikan kamu online.",
                    style = MaterialTheme.typography.bodyMedium, color = c.foregroundSecondary,
                )
            }
            return@FaunaryCard
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LikeButton(state.liked, state.likeCount, enabled = !state.loading && !state.failed, onClick = onToggleLike)
            Row(
                Modifier.clip(CircleShape).background(c.surfaceMuted).padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.ChatBubbleOutline, null, Modifier.size(18.dp), tint = c.foregroundSecondary)
                Spacer(Modifier.width(6.dp))
                Text("${state.comments.size} komentar", style = MaterialTheme.typography.labelLarge, color = c.foregroundSecondary)
            }
            Spacer(Modifier.weight(1f))
            if (state.loading) CircularProgressIndicator(Modifier.size(18.dp), color = c.primary, strokeWidth = 2.dp)
        }

        if (state.failed) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onRetry).padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Komentar tidak bisa dimuat.", style = MaterialTheme.typography.bodyMedium, color = c.danger, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp), tint = c.primary)
                Spacer(Modifier.width(4.dp))
                Text("Coba lagi", style = MaterialTheme.typography.labelLarge, color = c.primary)
            }
            return@FaunaryCard
        }

        if (state.comments.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                state.comments.forEach { comment ->
                    CommentRow(comment, mine = comment.userId == state.me, canDelete = state.canDelete(comment), onDelete = { onDelete(comment) })
                }
            }
        } else if (!state.loading) {
            Spacer(Modifier.height(10.dp))
            Text("Belum ada komentar. Jadilah yang pertama!", style = MaterialTheme.typography.bodyMedium, color = c.foregroundMuted)
        }

        Spacer(Modifier.height(14.dp))
        var draft by rememberSaveable { mutableStateOf("") }
        FaunaryTextField(
            value = draft,
            onValueChange = { draft = it.take(MAX_COMMENT_LENGTH) },
            placeholder = "Tulis komentar…",
            singleLine = false,
            imeAction = ImeAction.Default,
            trailing = {
                val canSend = draft.isNotBlank() && !state.sending
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(if (canSend) c.primary else c.surfaceMuted)
                        .clickable(enabled = canSend) { onSend(draft) { draft = "" } },
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.sending) CircularProgressIndicator(Modifier.size(16.dp), color = c.onPrimary, strokeWidth = 2.dp)
                    else Icon(Icons.AutoMirrored.Rounded.Send, "Kirim komentar", Modifier.size(18.dp), tint = if (canSend) c.onPrimary else c.foregroundMuted)
                }
            },
        )
        if (state.sendFailed) {
            Spacer(Modifier.height(6.dp))
            Text("Komentar gagal terkirim. Periksa koneksi lalu coba lagi.", style = MaterialTheme.typography.bodySmall, color = c.danger)
        }
    }
}

@Composable
private fun LikeButton(liked: Boolean, count: Int, enabled: Boolean, onClick: () -> Unit) {
    val c = FaunaryTheme.colors
    val bg by animateColorAsState(if (liked) c.primary else c.surfaceMuted, tween(180), label = "likeBg")
    val fg by animateColorAsState(if (liked) c.onPrimary else c.foregroundSecondary, tween(180), label = "likeFg")
    Row(
        Modifier.clip(CircleShape).background(bg).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (liked) Icons.Rounded.ThumbUp else Icons.Rounded.ThumbUpOffAlt, if (liked) "Batal suka" else "Suka", Modifier.size(18.dp), tint = fg)
        Spacer(Modifier.width(6.dp))
        Text(if (count > 0) "$count suka" else "Suka", style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@Composable
private fun CommentRow(comment: SightingComment, mine: Boolean, canDelete: Boolean, onDelete: () -> Unit) {
    val c = FaunaryTheme.colors
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(if (mine) c.highlight else c.badgeInfo),
            contentAlignment = Alignment.Center,
        ) {
            Text(comment.displayName.take(1).uppercase(), style = MaterialTheme.typography.labelLarge, color = if (mine) c.onHighlight else c.foreground)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (mine) "${comment.displayName} (kamu)" else comment.displayName,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = c.foreground,
                )
                Spacer(Modifier.width(6.dp))
                Text(Format.relative(comment.createdAtMs), style = MaterialTheme.typography.labelMedium, color = c.foregroundMuted)
            }
            Spacer(Modifier.height(2.dp))
            Text(comment.body, style = MaterialTheme.typography.bodyMedium, color = c.foreground)
        }
        if (canDelete) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, "Hapus komentar", Modifier.size(16.dp), tint = c.foregroundMuted)
            }
        }
    }
}
