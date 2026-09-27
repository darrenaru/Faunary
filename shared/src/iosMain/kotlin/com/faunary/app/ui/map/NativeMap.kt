package com.faunary.app.ui.map

import platform.UIKit.UIImage
import platform.UIKit.UIView

/*
 * Bridge to the Mapbox Maps SDK for iOS, which is Swift-only and so can't be called from Kotlin.
 * Swift implements these (iosApp/iosApp/FaunaMapbox.swift) and registers its factory in [NativeMaps]
 * at launch; everything else (markers, grouping, camera logic) stays in Kotlin.
 *
 * Coordinates are plain numbers so the Swift side stays small: lists are flattened lat,lng pairs, and
 * paddings and margins are in points.
 */

/** One Mapbox MapView. All calls happen on the main thread. */
interface NativeMapView {
    val view: UIView
    var listener: NativeMapListener?

    /** Loads a full style (see [MapStyle.json]); markers and lines are re-added once it has loaded. */
    fun setStyleJson(json: String)

    /** Moves the camera; [durationMs] 0 jumps. */
    fun setCamera(
        latitude: Double,
        longitude: Double,
        zoom: Double,
        bearing: Double,
        pitch: Double,
        topPadding: Double,
        bottomPadding: Double,
        durationMs: Double,
    )

    /** Eases the camera to show all [coordinates] (lat,lng,lat,lng…) inside the given padding. */
    fun fitCoordinates(coordinates: List<Double>, top: Double, left: Double, bottom: Double, right: Double, pitch: Double, maxZoom: Double)

    /** [latitude, longitude, zoom, bearing, pitch]. */
    fun cameraState(): List<Double>

    /** Replaces every marker. */
    fun setMarkers(markers: List<NativeMarker>)

    /** The in-app route (lat,lng pairs); an empty list removes it. [color] is "#RRGGBB". */
    fun setRoute(coordinates: List<Double>, color: String)

    /** Other explorers' routes, one lat,lng list each. */
    fun setSharedRoutes(routes: List<List<Double>>, color: String)

    /** The blue location puck (Mapbox's own, with the compass heading). */
    fun setUserLocationVisible(visible: Boolean)

    /** Room below the Mapbox logo and attribution, for the bottom bar. */
    fun setOrnamentBottomMargin(points: Double)
}

/** Map events, forwarded from Swift. */
interface NativeMapListener {
    fun onMarkerTap(key: String)
    fun onMapTap()
    fun onMapLongPress(latitude: Double, longitude: Double)
    fun onCameraChanged(latitude: Double, longitude: Double, zoom: Double)
    fun onCameraIdle(south: Double, west: Double, north: Double, east: Double)

    /** The user started dragging the map. */
    fun onUserPan()
}

/** A marker image drawn in Kotlin ([IosMarkerArt]); [imageId] is stable for identical images. */
class NativeMarker(
    val key: String,
    val latitude: Double,
    val longitude: Double,
    val image: UIImage,
    val imageId: String,
    /** True for pins whose tip marks the spot; false centres the image on it. */
    val anchorBottom: Boolean,
    /** Higher draws on top. */
    val sortKey: Double,
)

fun interface NativeMapFactory {
    fun createMapView(): NativeMapView
}

/** Set by Swift before the first map is shown. */
object NativeMaps {
    var factory: NativeMapFactory? = null
}
