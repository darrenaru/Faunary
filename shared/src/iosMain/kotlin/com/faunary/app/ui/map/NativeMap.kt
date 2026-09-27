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

    /**
     * The in-app route (lat,lng pairs), drawn as a [color] line on a wider [casingColor] one; an empty
     * list removes it. Colours are "#RRGGBB". Called every frame while the route is drawn in.
     */
    fun setRoute(coordinates: List<Double>, color: String, casingColor: String)

    /** Other explorers' routes, one lat,lng list each, semi-transparent. */
    fun setSharedRoutes(routes: List<List<Double>>, color: String)

    /**
     * Replaces the circles of one [layer]: [LAYER_PULSES] and [LAYER_DEPLOYS] (under everything),
     * [LAYER_SHARED_DESTS] (with the shared routes) or [LAYER_ROUTE_TIP] (above the route, under markers).
     * Called every frame while animating.
     */
    fun setCircles(layer: String, circles: List<NativeCircle>)

    /** A shared marker being deployed, as a view pinned at its tip, animated by [updateDeployPin]. */
    fun showDeployPin(id: String, latitude: Double, longitude: Double, image: UIImage)

    /** Moves the pin up by [offsetY] points (negative = up), scaled from its tip, at [alpha]. */
    fun updateDeployPin(id: String, offsetY: Double, scale: Double, alpha: Double)

    fun removeDeployPin(id: String)

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

/** A circle drawn by the map (radius and stroke in points). */
class NativeCircle(
    val latitude: Double,
    val longitude: Double,
    val radius: Double,
    val color: String,
    val opacity: Double,
    val strokeColor: String = color,
    val strokeWidth: Double = 0.0,
    val strokeOpacity: Double = 0.0,
)

const val LAYER_PULSES = "pulses"
const val LAYER_DEPLOYS = "deploys"
const val LAYER_SHARED_DESTS = "shared-dests"
const val LAYER_ROUTE_TIP = "route-tip"

fun interface NativeMapFactory {
    fun createMapView(): NativeMapView
}

/** Set by Swift before the first map is shown. */
object NativeMaps {
    var factory: NativeMapFactory? = null
}
