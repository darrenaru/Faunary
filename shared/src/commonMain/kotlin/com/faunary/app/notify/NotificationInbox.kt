package com.faunary.app.notify

import com.faunary.app.remote.AppNotification
import kotlinx.coroutines.flow.StateFlow

/**
 * Likes and comments on the user's finds, kept fresh while the app is open and shown as system
 * notifications (Android: InteractionNotifier, iOS: IosInteractionNotifier).
 */
interface NotificationInbox {
    val items: StateFlow<List<AppNotification>>
    val unreadCount: StateFlow<Int>
    /** False when online features are off. */
    val isAvailable: Boolean

    /** Reloads the inbox and posts system notifications for anything new. @return false on failure. */
    suspend fun refresh(): Boolean

    fun markAllRead()

    fun markRead(id: String)
}
