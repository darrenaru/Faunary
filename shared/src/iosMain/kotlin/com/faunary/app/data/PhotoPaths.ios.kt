package com.faunary.app.data

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/** The app-private Documents folder; the database and photos live here. Its path changes between installs. */
internal fun documentsDirectory(): String {
    val url = NSFileManager.defaultManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask).first() as NSURL
    return url.path!!
}

/** Photos folder inside [documentsDirectory]. */
internal fun photosDirectory(): String = "${documentsDirectory()}/photos"

actual fun localPhotoPath(stored: String): String {
    if (stored.startsWith("http") || SystemFileSystem.exists(Path(stored))) return stored
    return "${photosDirectory()}/${stored.substringAfterLast('/')}"
}
