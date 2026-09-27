package com.faunary.app.ui.navigation

import com.faunary.app.location.GeoPoint

/** "lat,lng,accuracy,timeMs" (accuracy may be empty). */
fun encodeFix(fix: GeoPoint) = "${fix.latitude},${fix.longitude},${fix.accuracy ?: ""},${fix.timeMs}"

fun decodeFix(value: String?): GeoPoint? {
    val parts = value?.split(",")?.takeIf { it.size == 4 } ?: return null
    val lat = parts[0].toDoubleOrNull() ?: return null
    val lng = parts[1].toDoubleOrNull() ?: return null
    return GeoPoint(lat, lng, parts[2].toFloatOrNull(), parts[3].toLongOrNull() ?: 0L)
}

const val NO_ID = -1L

fun encodeLatLng(lat: Double, lng: Double) = "$lat,$lng"

fun decodeLatLng(value: String?): Pair<Double, Double>? {
    val parts = value?.split(",") ?: return null
    if (parts.size != 2) return null
    val lat = parts[0].toDoubleOrNull() ?: return null
    val lng = parts[1].toDoubleOrNull() ?: return null
    return lat to lng
}
