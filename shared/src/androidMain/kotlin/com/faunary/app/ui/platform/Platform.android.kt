package com.faunary.app.ui.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.FileProvider
import com.faunary.app.util.LocationPermissions
import com.faunary.app.util.rememberPermissionState
import java.io.File

private class AndroidSharer(private val context: Context) : Sharer {
    override fun sharePhoto(path: String, text: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uriFor(File(path)))
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    override fun shareFile(fileName: String, mimeType: String, content: String, title: String) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName).apply { writeText(content) }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uriFor(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    private fun uriFor(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Composable
actual fun rememberSharer(): Sharer {
    val context = LocalContext.current
    return remember(context) { AndroidSharer(context) }
}

@Composable
actual fun rememberOpenAppSettings(): () -> Unit {
    val context = LocalContext.current
    return remember(context) {
        {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
        }
    }
}

@Composable
actual fun rememberLocationPermission(onResult: (Boolean) -> Unit): LocationPermission =
    rememberPermissionState(LocationPermissions, onResult)

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

@Composable
actual fun rememberShowMessage(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) { { text -> Toast.makeText(context, text, Toast.LENGTH_SHORT).show() } }
}

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}
