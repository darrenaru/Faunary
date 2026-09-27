package com.faunary.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

class LocationRepository(
    private val context: Context,
    private val fused: FusedLocationProviderClient,
) : LocationSource {
    private val _lastFix = MutableStateFlow<GeoPoint?>(null)
    /** Most recent fix, used for the "GPS 4m" accuracy chip and to centre the map. */
    override val lastFix: StateFlow<GeoPoint?> = _lastFix.asStateFlow()

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Android 12+ lets users grant only an approximate (≈ km) location; finds need the precise one. */
    override fun hasPreciseLocation(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(timeoutMs: Long): GeoPoint? {
        if (!hasPermission()) return null
        val cts = CancellationTokenSource()
        val fresh = runCatching {
            withTimeoutOrNull(timeoutMs) {
                fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
            }
        }.getOrNull()
        if (fresh == null) cts.cancel()
        val loc = fresh ?: runCatching { fused.lastLocation.await() }.getOrNull() ?: return null
        return GeoPoint(loc.latitude, loc.longitude, if (loc.hasAccuracy()) loc.accuracy else null)
            .also { _lastFix.value = it }
    }

    @SuppressLint("MissingPermission")
    override suspend fun lastKnown(): GeoPoint? {
        if (!hasPermission()) return null
        val loc = runCatching { fused.lastLocation.await() }.getOrNull() ?: return null
        return GeoPoint(loc.latitude, loc.longitude, if (loc.hasAccuracy()) loc.accuracy else null)
            .also { _lastFix.value = it }
    }

    /** Continuous high-accuracy GPS fixes (camera, review, navigation); stops when the collector goes away. */
    @SuppressLint("MissingPermission")
    override fun updates(intervalMs: Long): Flow<GeoPoint> = callbackFlow {
        if (!hasPermission()) {
            close()
            return@callbackFlow
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    val p = GeoPoint(it.latitude, it.longitude, if (it.hasAccuracy()) it.accuracy else null, it.time)
                    _lastFix.value = p
                    trySend(p)
                }
            }
        }
        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { fused.removeLocationUpdates(callback) }
    }

    override suspend fun reverseGeocode(lat: Double, lng: Double): Place? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.forLanguageTag("id-ID"))
        val address: Address? = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                withTimeoutOrNull(6_000) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) { cont.resume(addresses.firstOrNull()) }
                            override fun onError(errorMessage: String?) { cont.resume(null) }
                        })
                    }
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()
                }
            }
        }.getOrNull()
        address ?: return null

        val area = address.subLocality ?: address.locality ?: address.subAdminArea
        val street = address.thoroughfare
        val short = listOfNotNull(street, area).distinct().joinToString(", ")
            .ifEmpty { address.featureName ?: address.adminArea ?: return null }
        return Place(short, address.getAddressLine(0))
    }
}
