package com.faunary.app.location

import kotlinx.cinterop.useContents
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreLocation.CLAccuracyAuthorization
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLGeocoder
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.CLPlacemark
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.Foundation.NSError
import platform.Foundation.NSLocale
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject
import kotlin.coroutines.resume

/** CoreLocation-backed [LocationSource]. Used from the main thread only (Compose and viewModelScope). */
class IosLocationSource : LocationSource {
    private val manager = CLLocationManager()
    private val _lastFix = MutableStateFlow<GeoPoint?>(null)
    override val lastFix: StateFlow<GeoPoint?> = _lastFix.asStateFlow()
    private val waiters = mutableListOf<CancellableContinuation<GeoPoint?>>()
    private val fixes = MutableSharedFlow<GeoPoint>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var updateCollectors = 0

    /** Location access as the user last set it; [precise] is false for "approximate location". */
    data class Access(val status: CLAuthorizationStatus, val precise: Boolean)

    private val _access = MutableStateFlow(currentAccess())
    /** Changes whenever the user answers a prompt or changes access (or precision) in Settings. */
    val access: StateFlow<Access> = _access.asStateFlow()

    // Held in a property: CLLocationManager keeps only a weak reference to its delegate.
    private val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
            val latest = didUpdateLocations.filterIsInstance<CLLocation>().map { it.toGeoPoint() }
            latest.forEach { fixes.tryEmit(it) }
            val fix = latest.lastOrNull() ?: return
            _lastFix.value = fix
            finish(fix)
        }

        override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) = finish(null)

        override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
            _access.value = currentAccess()
            if (hasPermission()) manager.requestLocation()
        }
    }

    init {
        manager.delegate = delegate
        manager.desiredAccuracy = kCLLocationAccuracyBest
    }

    private fun currentAccess() = Access(manager.authorizationStatus, hasPreciseLocation())

    /** True once the user blocked location for this app; only system Settings can undo it. */
    val isDenied: Boolean
        get() = manager.authorizationStatus.let { it == kCLAuthorizationStatusDenied || it == kCLAuthorizationStatusRestricted }

    /**
     * Shows the system "while using the app" prompt the first time. When access was given but only
     * approximately, asks for precise location once (for this session) instead.
     */
    fun requestPermission() {
        when {
            manager.authorizationStatus == kCLAuthorizationStatusNotDetermined -> manager.requestWhenInUseAuthorization()
            hasPermission() && !hasPreciseLocation() ->
                manager.requestTemporaryFullAccuracyAuthorizationWithPurposeKey(FULL_ACCURACY_PURPOSE) { _ -> }
        }
    }

    override fun hasPermission(): Boolean = manager.authorizationStatus.let {
        it == kCLAuthorizationStatusAuthorizedWhenInUse || it == kCLAuthorizationStatusAuthorizedAlways
    }

    override fun hasPreciseLocation(): Boolean =
        hasPermission() && manager.accuracyAuthorization == CLAccuracyAuthorization.CLAccuracyAuthorizationFullAccuracy

    override suspend fun lastKnown(): GeoPoint? =
        if (hasPermission()) manager.location?.toGeoPoint()?.also { _lastFix.value = it } else null

    override suspend fun currentLocation(timeoutMs: Long): GeoPoint? {
        if (!hasPermission()) return null
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                waiters += cont
                cont.invokeOnCancellation { waiters.remove(cont) }
                manager.requestLocation()
            }
        }
    }

    /** One CoreLocation stream shared by every collector; it stops when the last one goes away. */
    override fun updates(intervalMs: Long): Flow<GeoPoint> = flow {
        if (!hasPermission()) return@flow
        if (updateCollectors++ == 0) manager.startUpdatingLocation()
        try {
            emitAll(fixes)
        } finally {
            if (--updateCollectors == 0) manager.stopUpdatingLocation()
        }
    }

    override suspend fun reverseGeocode(lat: Double, lng: Double): Place? = withTimeoutOrNull(GEOCODE_TIMEOUT_MS) {
        val geocoder = CLGeocoder()
        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { geocoder.cancelGeocode() }
            geocoder.reverseGeocodeLocation(
                CLLocation(latitude = lat, longitude = lng),
                preferredLocale = NSLocale(localeIdentifier = "id_ID"),
            ) { placemarks, _ ->
                if (cont.isActive) cont.resume((placemarks?.firstOrNull() as? CLPlacemark)?.toPlace())
            }
        }
    }

    private fun finish(fix: GeoPoint?) {
        val pending = waiters.toList()
        waiters.clear()
        pending.forEach { if (it.isActive) it.resume(fix) }
    }

    private fun CLLocation.toGeoPoint(): GeoPoint = coordinate.useContents {
        GeoPoint(
            latitude = latitude,
            longitude = longitude,
            accuracy = horizontalAccuracy.takeIf { it >= 0 }?.toFloat(),
            timeMs = (timestamp.timeIntervalSince1970 * 1000).toLong(),
        )
    }

    /** Same shape as Android's Geocoder result: "Street, Area" plus the full address. */
    private fun CLPlacemark.toPlace(): Place? {
        val area = subLocality ?: locality ?: subAdministrativeArea
        val short = listOfNotNull(thoroughfare, area).distinct().joinToString(", ")
            .ifEmpty { name ?: administrativeArea ?: return null }
        val full = listOfNotNull(name, subLocality, locality, administrativeArea, postalCode, country)
            .distinct().joinToString(", ").ifEmpty { null }
        return Place(short, full)
    }

    private companion object {
        /** Key in Info.plist's NSLocationTemporaryUsageDescriptionDictionary (see iosApp/project.yml). */
        const val FULL_ACCURACY_PURPOSE = "FindLocation"
        const val GEOCODE_TIMEOUT_MS = 6_000L
    }
}
