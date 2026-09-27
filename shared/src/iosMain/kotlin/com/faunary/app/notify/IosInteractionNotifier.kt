package com.faunary.app.notify

import com.faunary.app.remote.AppNotification
import com.faunary.app.remote.NotificationRepository
import com.faunary.app.util.appInForeground
import com.faunary.app.util.currentTimeMillis
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
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
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

/** What a tapped notification asks the app to open. */
sealed interface NotificationOpen {
    /** [sightingId] is the server id; [notificationId] is marked read. */
    data class Sighting(val sightingId: String, val notificationId: String?) : NotificationOpen
    data object Inbox : NotificationOpen
}

/**
 * iOS counterpart of Android's InteractionNotifier: keeps the inbox fresh (realtime while the app is
 * open, a background refresh otherwise) and shows local notifications for new likes and comments.
 */
class IosInteractionNotifier(
    private val repo: NotificationRepository,
    private val prefs: Settings,
) : NotificationInbox {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val postLock = Mutex()
    private val center = UNUserNotificationCenter.currentNotificationCenter()

    private val _items = MutableStateFlow<List<AppNotification>>(emptyList())
    override val items: StateFlow<List<AppNotification>> = _items.asStateFlow()
    override val unreadCount: StateFlow<Int> = _items.map { list -> list.count { !it.isRead } }
        .stateIn(scope, SharingStarted.Eagerly, 0)
    override val isAvailable: Boolean get() = repo.isAvailable

    private val _pendingOpen = MutableStateFlow<NotificationOpen?>(null)
    /** Set when a notification was tapped; the app shell opens it and calls [consumeOpen]. */
    val pendingOpen: StateFlow<NotificationOpen?> = _pendingOpen.asStateFlow()

    // Held in a property: the notification center keeps only a weak reference to its delegate.
    private val delegate = object : NSObject(), UNUserNotificationCenterDelegateProtocol {
        override fun userNotificationCenter(
            center: UNUserNotificationCenter,
            didReceiveNotificationResponse: UNNotificationResponse,
            withCompletionHandler: () -> Unit,
        ) {
            val info = didReceiveNotificationResponse.notification.request.content.userInfo
            val sightingId = info[KEY_SIGHTING_ID] as? String
            _pendingOpen.value = sightingId?.let { NotificationOpen.Sighting(it, info[KEY_NOTIFICATION_ID] as? String) }
                ?: NotificationOpen.Inbox
            withCompletionHandler()
        }

        // Shown as a banner even while the app is open, as on Android.
        override fun userNotificationCenter(
            center: UNUserNotificationCenter,
            willPresentNotification: UNNotification,
            withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
        ) {
            withCompletionHandler(
                UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionList or UNNotificationPresentationOptionSound,
            )
        }
    }

    /** Must run while the app launches, so a tap that cold-starts the app still reaches [pendingOpen]. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        center.delegate = delegate
        if (!repo.isAvailable) return
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
        ) { _, _ -> }
        appInForeground()
            .flatMapLatest { foreground ->
                if (foreground) flow {
                    emit(Unit)
                    emitAll(repo.changes())
                } else emptyFlow()
            }
            .onEach { refresh() }
            .launchIn(scope)
    }

    fun consumeOpen() {
        _pendingOpen.value = null
    }

    override suspend fun refresh(): Boolean {
        val list = repo.list() ?: return false
        _items.value = list
        postNew(list)
        updateBadge()
        return true
    }

    override fun markAllRead() {
        val now = currentTimeMillis().toString()
        _items.update { list -> list.map { if (it.isRead) it else it.copy(readAt = now) } }
        center.removeAllDeliveredNotifications()
        updateBadge()
        scope.launch { repo.markAllRead() }
    }

    override fun markRead(id: String) {
        _items.update { list -> list.map { if (it.id == id && !it.isRead) it.copy(readAt = "now") else it } }
        center.removeDeliveredNotificationsWithIdentifiers(listOf(id))
        updateBadge()
        scope.launch { repo.markRead(id) }
    }

    private suspend fun postNew(list: List<AppNotification>) = postLock.withLock {
        val since = prefs.getLong(KEY_NOTIFIED_UNTIL, 0L)
        // First run: don't replay history, only the last day.
        val floor = if (since == 0L) currentTimeMillis() - DAY_MS else since
        val fresh = list.filter { !it.isRead && it.createdAtMs > floor }.sortedBy { it.createdAtMs }
        val newest = list.maxOfOrNull { it.createdAtMs } ?: return@withLock
        prefs.putLong(KEY_NOTIFIED_UNTIL, maxOf(floor, newest))
        fresh.forEach { post(it) }
    }

    private fun post(n: AppNotification) {
        val content = UNMutableNotificationContent().apply {
            setTitle(n.headline)
            setBody(if (n.isLike) "Ketuk untuk melihat temuanmu." else "“${n.commentBody.orEmpty().take(200)}”")
            setSound(UNNotificationSound.defaultSound)
            // Grouped in Notification Center like Android's bundle.
            setThreadIdentifier(THREAD_ID)
            setUserInfo(mapOf(KEY_SIGHTING_ID to n.sightingId, KEY_NOTIFICATION_ID to n.id))
        }
        center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(n.id, content, trigger = null), withCompletionHandler = null)
    }

    private fun updateBadge() {
        center.setBadgeCount(_items.value.count { !it.isRead }.toLong(), withCompletionHandler = null)
    }

    private companion object {
        const val KEY_NOTIFIED_UNTIL = "notified_until"
        const val KEY_SIGHTING_ID = "sighting_id"
        const val KEY_NOTIFICATION_ID = "notification_id"
        const val THREAD_ID = "faunary_interactions"
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
