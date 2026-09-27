package com.faunary.app.ui.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.faunary.app.data.PhotoStorage
import com.faunary.app.location.GeoPoint
import com.faunary.app.location.GpsAccuracy
import com.faunary.app.location.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.io.File
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val photos: PhotoStorage,
    private val location: LocationRepository,
) : ViewModel() {

    /** Bumped when location permission is granted, to (re)start the GPS stream. */
    private val permissionGrants = MutableStateFlow(0)

    /**
     * Live high-accuracy GPS while the camera is on screen (it stops in the background), so the
     * receiver has locked on by the time the shutter is pressed and the photo gets a precise, fresh fix.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val fix: StateFlow<GeoPoint?> = permissionGrants
        .flatMapLatest { if (location.hasPermission()) location.updates() else emptyFlow() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun newCaptureFile(): File = photos.newCaptureFile()

    fun setBusy(value: Boolean) {
        _busy.value = value
    }

    fun reportError(message: String?) {
        _error.value = message
    }

    fun onLocationPermissionGranted() = permissionGrants.update { it + 1 }

    /** The fix to pin the photo on, if the camera had a fresh one when the shutter was pressed. */
    fun captureFix(): GeoPoint? =
        fix.value?.takeIf { System.currentTimeMillis() - it.timeMs <= GpsAccuracy.CAPTURE_MAX_AGE_MS }
}
