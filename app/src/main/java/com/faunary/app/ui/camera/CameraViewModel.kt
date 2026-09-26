package com.faunary.app.ui.camera

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.faunary.app.data.ExifLocation
import com.faunary.app.data.PhotoStorage
import com.faunary.app.location.GeoPoint
import com.faunary.app.location.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val photos: PhotoStorage,
    private val location: LocationRepository,
) : ViewModel() {

    val lastFix: StateFlow<GeoPoint?> = location.lastFix

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

    /** Starts a GPS fix early so the review screen gets coordinates instantly. */
    fun warmUpLocation() = viewModelScope.launch { location.currentLocation() }

    fun import(uri: Uri, onReady: (String, ExifLocation?) -> Unit) = viewModelScope.launch {
        _busy.value = true
        runCatching { photos.importFromUri(uri) }
            .onSuccess { (file, exif) -> onReady(file.absolutePath, exif) }
            .onFailure { _error.value = "Foto tidak bisa dibuka" }
        _busy.value = false
    }
}
