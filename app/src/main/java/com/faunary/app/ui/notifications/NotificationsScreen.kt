package com.faunary.app.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.faunary.app.data.SightingRepository
import com.faunary.app.notify.InteractionNotifier
import com.faunary.app.remote.AppNotification
import com.faunary.app.ui.components.EmptyState
import com.faunary.app.ui.components.PhotoThumb
import com.faunary.app.ui.components.SurfaceIconButton
import com.faunary.app.ui.theme.FaunaryTheme
import com.faunary.app.util.Format
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notifier: InteractionNotifier,
    private val sightings: SightingRepository,
) : ViewModel() {
    val items = notifier.items
    val unreadCount = notifier.unreadCount
    val available get() = notifier.isAvailable

    fun refresh() = viewModelScope.launch { notifier.refresh() }

    fun markAllRead() = notifier.markAllRead()

    /**
     * Opens the sighting behind a notification: the user's own entry when it is on this phone,
     * otherwise the server copy (e.g. after reinstalling).
     */
    fun open(sightingId: String, notificationId: String?, onOwn: (Long) -> Unit, onRemote: (String) -> Unit) {
        notificationId?.let(notifier::markRead)
        viewModelScope.launch {
            val local = sightings.idForRemote(sightingId)
            if (local != null) onOwn(local) else onRemote(sightingId)
        }
    }
}

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenOwn: (Long) -> Unit,
    onOpenRemote: (String) -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val c = FaunaryTheme.colors
    LaunchedEffect(Unit) { viewModel.refresh() }
    // Everything on screen counts as seen once the user leaves (dots stay visible while reading).
    DisposableEffect(Unit) { onDispose { viewModel.markAllRead() } }

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SurfaceIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali", onBack)
            Spacer(Modifier.width(12.dp))
            Text("Notifikasi", style = MaterialTheme.typography.titleLarge, color = c.foreground)
        }
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    Icons.Rounded.NotificationsNone, "Belum ada notifikasi",
                    if (viewModel.available) "Saat orang lain menyukai atau mengomentari foto satwamu, kabarnya muncul di sini."
                    else "Fitur online belum aktif di aplikasi ini.",
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                modifier = Modifier.navigationBarsPadding(),
            ) {
                items(items, key = { it.id }) { n ->
                    NotificationRow(n) { viewModel.open(n.sightingId, n.id, onOpenOwn, onOpenRemote) }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: AppNotification, onClick: () -> Unit) {
    val c = FaunaryTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (n.isRead) c.background else c.highlight.copy(alpha = 0.25f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Actor avatar with a small like/comment badge.
        Box {
            Box(Modifier.size(46.dp).clip(CircleShape).background(c.badgeInfo), contentAlignment = Alignment.Center) {
                Text(n.actorName.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = c.foreground)
            }
            Box(
                Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp).size(22.dp).clip(CircleShape)
                    .background(c.background).padding(2.dp).clip(CircleShape).background(if (n.isLike) c.primary else c.info),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (n.isLike) Icons.Rounded.ThumbUp else Icons.Rounded.ChatBubble, null, Modifier.size(11.dp), tint = c.onPrimary)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(n.actorName) }
                    append(if (n.isLike) " menyukai foto ${n.animalLabel}-mu" else " mengomentari foto ${n.animalLabel}-mu")
                },
                style = MaterialTheme.typography.bodyMedium, color = c.foreground,
            )
            n.commentBody?.let {
                Text("“$it”", style = MaterialTheme.typography.bodySmall, color = c.foregroundSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(2.dp))
            Text(Format.relative(n.createdAtMs), style = MaterialTheme.typography.labelMedium, color = c.foregroundMuted)
        }
        Spacer(Modifier.width(10.dp))
        PhotoThumb(n.photoUrl, Modifier.size(52.dp), RoundedCornerShape(12.dp), n.animalLabel)
        if (!n.isRead) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(8.dp).clip(CircleShape).background(c.primary))
        }
    }
}
