package com.faunary.app.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.faunary.app.data.localPhotoPath
import com.faunary.app.location.IosLocationSource
import com.faunary.app.util.currentTimeMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import org.koin.compose.koinInject
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIViewController

private object IosSharer : Sharer {
    override fun sharePhoto(path: String, text: String, title: String) {
        present(listOf(NSURL.fileURLWithPath(localPhotoPath(path)), text))
    }

    override fun shareFile(fileName: String, mimeType: String, content: String, title: String) {
        val path = NSTemporaryDirectory() + fileName
        NSString.create(string = content).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        present(listOf(NSURL.fileURLWithPath(path)))
    }

    private fun present(items: List<Any>) {
        val controller = UIActivityViewController(activityItems = items, applicationActivities = null)
        topViewController()?.presentViewController(controller, animated = true, completion = null)
    }
}

/** The view controller currently on screen, to present system sheets (share, camera) over it. */
@Suppress("DEPRECATION")
internal fun topViewController(): UIViewController? {
    var top = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}

@Composable
actual fun rememberSharer(): Sharer = IosSharer

@Composable
actual fun rememberOpenAppSettings(): () -> Unit = remember {
    {
        NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let {
            UIApplication.sharedApplication.openURL(it, options = emptyMap<Any?, Any>(), completionHandler = null)
        }
    }
}

@Composable
actual fun rememberLocationPermission(onResult: (Boolean) -> Unit): LocationPermission {
    val source = koinInject<IosLocationSource>()
    // Read here so the caller recomposes when access changes (e.g. to show "Buka Pengaturan").
    val access by source.access.collectAsState()
    val currentOnResult by rememberUpdatedState(onResult)
    LaunchedEffect(source) {
        source.access.drop(1).collect { currentOnResult(source.hasPermission()) }
    }
    return remember(source, access) {
        object : LocationPermission {
            override val granted = source.hasPermission()
            override val permanentlyDenied = source.isDenied
            override fun request() = source.requestPermission()
        }
    }
}

/** iPhone has no system back button; screens are left with their own back arrow. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

/** Messages shown by [rememberShowMessage]; the iOS app shell draws the latest one (see MessageHost in IosApp.kt). */
object IosMessages {
    private val _current = MutableStateFlow<Pair<String, Long>?>(null)
    val current: StateFlow<Pair<String, Long>?> = _current.asStateFlow()

    fun show(text: String) {
        _current.value = text to currentTimeMillis()
    }

    fun clear() {
        _current.value = null
    }
}

@Composable
actual fun rememberShowMessage(): (String) -> Unit = remember { { IosMessages.show(it) } }

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    DisposableEffect(enabled) {
        UIApplication.sharedApplication.idleTimerDisabled = enabled
        onDispose { UIApplication.sharedApplication.idleTimerDisabled = false }
    }
}
