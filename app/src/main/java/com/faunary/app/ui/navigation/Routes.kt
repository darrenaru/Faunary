package com.faunary.app.ui.navigation

import kotlinx.serialization.Serializable

/** [routeTo] ("lat,lng") + [routeLabel] open the map with an in-app route already drawn. */
@Serializable
data class MapRoute(val focusId: Long = NO_ID, val routeTo: String? = null, val routeLabel: String? = null)

@Serializable
data object GalleryRoute

@Serializable
data object JournalRoute

@Serializable
data object ProfileRoute

@Serializable
data object CameraRoute

/**
 * [photoPath] is the fresh camera capture to review; [capturedAt] is when the shutter was pressed and
 * [captureFix] the GPS fix at that moment ([encodeFix]), if the camera had a fresh one.
 */
@Serializable
data class ReviewRoute(val photoPath: String, val capturedAt: Long, val captureFix: String? = null)

@Serializable
data class DetailRoute(val id: Long)

/** Someone else's sighting, by server id. */
@Serializable
data class CommunityDetailRoute(val id: String)

/** Likes and comments on the user's finds. */
@Serializable
data object NotificationsRoute

/** What a tapped system notification asks the app to open. */
sealed interface NotificationOpen {
    /** [sightingId] is the server id; [notificationId] is marked read. */
    data class Sighting(val sightingId: String, val notificationId: String?) : NotificationOpen
    data object Inbox : NotificationOpen
}
