package com.faunary.app.ui.navigation

import com.faunary.app.location.GeoPoint
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

const val NO_ID = -1L

fun encodeLatLng(lat: Double, lng: Double) = "$lat,$lng"

/** "lat,lng,accuracy,timeMs" (accuracy may be empty). */
fun encodeFix(fix: GeoPoint) = "${fix.latitude},${fix.longitude},${fix.accuracy ?: ""},${fix.timeMs}"

fun decodeFix(value: String?): GeoPoint? {
    val parts = value?.split(",")?.takeIf { it.size == 4 } ?: return null
    val lat = parts[0].toDoubleOrNull() ?: return null
    val lng = parts[1].toDoubleOrNull() ?: return null
    return GeoPoint(lat, lng, parts[2].toFloatOrNull(), parts[3].toLongOrNull() ?: 0L)
}

fun decodeLatLng(value: String?): Pair<Double, Double>? {
    val parts = value?.split(",") ?: return null
    if (parts.size != 2) return null
    val lat = parts[0].toDoubleOrNull() ?: return null
    val lng = parts[1].toDoubleOrNull() ?: return null
    return lat to lng
}
