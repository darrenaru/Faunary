package com.faunary.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.hilt.work.HiltWorker
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.faunary.app.MainActivity
import com.faunary.app.R
import com.faunary.app.remote.AppNotification
import com.faunary.app.remote.NotificationRepository
import com.faunary.app.util.hasPermission
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Likes/comments on the user's finds: keeps the in-app inbox current and posts a system
 * notification (who + the photo) for each new one. Realtime while the app is open, and a
 * WorkManager check every ~15 minutes otherwise (there is no push server).
 */
@Singleton
class InteractionNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: NotificationRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("faunary_notifications", Context.MODE_PRIVATE)
    private val postLock = Mutex()

    private val _items = MutableStateFlow<List<AppNotification>>(emptyList())
    val items: StateFlow<List<AppNotification>> = _items.asStateFlow()
    val unreadCount: StateFlow<Int> = _items.map { list -> list.count { !it.isRead } }
        .stateIn(scope, SharingStarted.Eagerly, 0)

    val isAvailable: Boolean get() = repo.isAvailable

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        if (!repo.isAvailable) return
        createChannel()
        NotificationWorker.schedule(context)
        ProcessLifecycleOwner.get().lifecycle.currentStateFlow
            .map { it.isAtLeast(Lifecycle.State.STARTED) }
            .flatMapLatest { foreground ->
                if (foreground) flow {
                    emit(Unit)
                    emitAll(repo.changes())
                } else emptyFlow()
            }
            .onEach { refresh() }
            .launchIn(scope)
    }

    /** Reloads the inbox and posts system notifications for anything new. @return false on failure. */
    suspend fun refresh(): Boolean {
        val list = repo.list() ?: return false
        _items.value = list
        postNew(list)
        return true
    }

    fun markAllRead() {
        val now = System.currentTimeMillis().toString()
        _items.update { list -> list.map { if (it.isRead) it else it.copy(readAt = now) } }
        NotificationManagerCompat.from(context).cancelAll()
        scope.launch { repo.markAllRead() }
    }

    fun markRead(id: String) {
        _items.update { list -> list.map { if (it.id == id && !it.isRead) it.copy(readAt = "now") else it } }
        NotificationManagerCompat.from(context).cancel(id.hashCode())
        scope.launch { repo.markRead(id) }
    }

    private suspend fun postNew(list: List<AppNotification>) = postLock.withLock {
        val since = prefs.getLong(KEY_NOTIFIED_UNTIL, 0L)
        // First run: don't replay history, only the last day.
        val floor = if (since == 0L) System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1) else since
        val fresh = list.filter { !it.isRead && it.createdAtMs > floor }.sortedBy { it.createdAtMs }
        val newest = list.maxOfOrNull { it.createdAtMs } ?: return@withLock
        prefs.edit { putLong(KEY_NOTIFIED_UNTIL, maxOf(floor, newest)) }
        if (fresh.isEmpty() || !canPost()) return@withLock
        fresh.forEach { post(it) }
        // Several at once: Android bundles them under a summary that opens the inbox.
        val unread = list.count { !it.isRead }
        if (unread > 1) postSummary(unread)
    }

    private fun canPost(): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    @Suppress("MissingPermission") // checked in canPost()
    private suspend fun post(n: AppNotification) {
        val photo = loadBitmap(n.photoUrl)
        val text = if (n.isLike) "Ketuk untuk melihat temuanmu." else "“${n.commentBody.orEmpty().take(200)}”"
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_paw)
            .setColor(0xFFDF6D41.toInt())
            .setContentTitle(n.headline)
            .setContentText(text)
            .setWhen(n.createdAtMs)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setGroup(GROUP)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openIntent(n))
        if (photo != null) {
            builder.setLargeIcon(photo)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(photo)
                    .bigLargeIcon(null as Bitmap?)
                    .setSummaryText(text),
            )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(text))
        }
        NotificationManagerCompat.from(context).notify(n.id.hashCode(), builder.build())
    }

    @Suppress("MissingPermission")
    private fun postSummary(unread: Int) {
        val summary = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_paw)
            .setColor(0xFFDF6D41.toInt())
            .setContentTitle("$unread interaksi baru")
            .setContentText("Suka dan komentar di temuanmu")
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setContentIntent(inboxIntent())
            .build()
        NotificationManagerCompat.from(context).notify(SUMMARY_ID, summary)
    }

    private fun openIntent(n: AppNotification): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_SIGHTING)
            .putExtra(EXTRA_SIGHTING_ID, n.sightingId)
            .putExtra(EXTRA_NOTIFICATION_ID, n.id)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, n.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun inboxIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_INBOX)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, SUMMARY_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private suspend fun loadBitmap(url: String): Bitmap? = runCatching {
        val request = ImageRequest.Builder(context).data(url).allowHardware(false).size(1024).build()
        (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
    }.getOrNull()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Suka & komentar", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Saat orang lain menyukai atau mengomentari foto satwamu"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_OPEN_SIGHTING = "com.faunary.app.OPEN_SIGHTING"
        const val ACTION_OPEN_INBOX = "com.faunary.app.OPEN_INBOX"
        const val EXTRA_SIGHTING_ID = "sighting_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        private const val CHANNEL_ID = "interactions"
        private const val GROUP = "faunary_interactions"
        private const val SUMMARY_ID = 1001
        private const val KEY_NOTIFIED_UNTIL = "notified_until"
    }
}

/** Background check for new likes/comments while the app is closed (WorkManager minimum: 15 min). */
@HiltWorker
class NotificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notifier: InteractionNotifier,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = if (notifier.refresh()) Result.success() else Result.retry()

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NotificationWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("faunary-notifications", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
