package com.faunary.app.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

/** Build facts the UI shows; each platform module provides one through Koin. */
data class AppInfo(val versionName: String, val debug: Boolean)

/** Opens the platform share sheet. */
interface Sharer {
    /** Shares a photo from app storage, with [text] as the caption. */
    fun sharePhoto(path: String, text: String, title: String)

    /** Writes [content] to a temporary [fileName] and shares that file. */
    fun shareFile(fileName: String, mimeType: String, content: String, title: String)
}

@Composable
expect fun rememberSharer(): Sharer

/** Opens this app's page in the system settings (to grant a permission that was denied for good). */
@Composable
expect fun rememberOpenAppSettings(): () -> Unit

/** Asks for location access (when in use); [permanentlyDenied] means only system Settings can grant it now. */
interface LocationPermission {
    /** Observable: reading it in composition recomposes when access changes. */
    val granted: Boolean
    val permanentlyDenied: Boolean
    fun request()
}

/** [onResult] runs with the new grant state after a request, and when it changes in Settings. */
@Composable
expect fun rememberLocationPermission(onResult: (Boolean) -> Unit = {}): LocationPermission

/** System back (Android's back button/gesture); iOS has no system back, so it does nothing there. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

/** A short message at the bottom of the screen (a Toast on Android). */
@Composable
expect fun rememberShowMessage(): (String) -> Unit

/** Keeps the screen from dimming while [enabled] (turn-by-turn navigation). */
@Composable
expect fun KeepScreenOn(enabled: Boolean)

/** Copies [text] to the clipboard and confirms it with [confirmation]. */
@Composable
fun rememberCopyText(): (text: String, confirmation: String) -> Unit {
    val clipboard = LocalClipboardManager.current
    val showMessage = rememberShowMessage()
    return { text, confirmation ->
        clipboard.setText(AnnotatedString(text))
        showMessage(confirmation)
    }
}
