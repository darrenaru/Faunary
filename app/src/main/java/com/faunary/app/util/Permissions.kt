package com.faunary.app.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

val LocationPermissions = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

/** Small permission helper: tracks grant state and whether the user blocked the dialog for good. */
class PermissionState(granted: Boolean) {
    var granted by mutableStateOf(granted)
        internal set
    var permanentlyDenied by mutableStateOf(false)
        internal set
    internal var launch: () -> Unit = {}

    fun request() = launch()
}

@Composable
fun rememberPermissionState(
    permissions: Array<String>,
    onResult: (Boolean) -> Unit = {},
): PermissionState {
    val context = LocalContext.current
    val state = remember { PermissionState(permissions.any { context.hasPermission(it) }) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val ok = result.values.any { it }
        val activity = context.findActivity()
        state.granted = ok
        state.permanentlyDenied = !ok && activity != null &&
            permissions.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
        onResult(ok)
    }
    state.launch = { launcher.launch(permissions) }
    return state
}

fun Context.hasPermission(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
