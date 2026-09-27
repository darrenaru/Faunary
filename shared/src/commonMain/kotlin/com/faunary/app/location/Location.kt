package com.faunary.app.location

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** [accuracy] is the 68% radius in metres; [timeMs] is when the fix was taken (0 = unknown). */
data class GeoPoint(val latitude: Double, val longitude: Double, val accuracy: Float? = null, val timeMs: Long = 0L)

data class Place(val shortName: String, val fullAddress: String?)

/** How precise a find's location must be: finds are pinned exactly where the animal was photographed. */
object GpsAccuracy {
    /** Good enough to stop refining (typical open-sky GPS). */
    const val GOOD_METERS = 10f
    /** A find can only be saved with a fix at least this precise. */
    const val MAX_SAVE_METERS = 30f
    /** A fix from the camera is only used for the photo if it is at most this old at the shutter press. */
    const val CAPTURE_MAX_AGE_MS = 10_000L
}

/** The device position as the shared view models need it (FusedLocation on Android, CoreLocation on iOS). */
interface LocationSource {
    /** Most recent fix, used for the "GPS 4m" accuracy chip and to centre the map. */
    val lastFix: StateFlow<GeoPoint?>

    fun hasPermission(): Boolean

    /** Android 12+ and iOS 14+ let users grant only an approximate (≈ km) location; finds need the precise one. */
    fun hasPreciseLocation(): Boolean

    /** Last position the system already knows (instant, may be a few minutes old); null if none. */
    suspend fun lastKnown(): GeoPoint?

    /** A fresh fix, or null when none arrives within [timeoutMs]. */
    suspend fun currentLocation(timeoutMs: Long = 8_000): GeoPoint?

    /** Continuous high-accuracy fixes (camera, review, navigation); stops when the collector goes away. */
    fun updates(intervalMs: Long = 1_000): Flow<GeoPoint>

    /** Street/area name for a position, in Indonesian where available; null when unknown or offline. */
    suspend fun reverseGeocode(lat: Double, lng: Double): Place?
}
