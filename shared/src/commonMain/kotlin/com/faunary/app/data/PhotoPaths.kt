package com.faunary.app.data

/**
 * Where a stored photo path ([AnimalSighting.photoPath]) is on this device now. iOS moves the app's
 * container on every update or reinstall, so saved absolute paths go stale there; Android's don't.
 */
expect fun localPhotoPath(stored: String): String
