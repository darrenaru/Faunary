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

/** [exif] carries "lat,lng" from an imported photo's EXIF when available. */
@Serializable
data class ReviewRoute(val photoPath: String, val exif: String? = null)

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

/** [start] is an optional "lat,lng" to centre the picker on. */
@Serializable
data class LocationPickerRoute(val start: String? = null)

const val NO_ID = -1L

/** SavedStateHandle key used by the location picker to hand back "lat,lng". */
const val PICKED_LOCATION_KEY = "picked_location"

fun encodeLatLng(lat: Double, lng: Double) = "$lat,$lng"

fun decodeLatLng(value: String?): Pair<Double, Double>? {
    val parts = value?.split(",") ?: return null
    if (parts.size != 2) return null
    val lat = parts[0].toDoubleOrNull() ?: return null
    val lng = parts[1].toDoubleOrNull() ?: return null
    return lat to lng
}
