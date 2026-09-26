package com.faunary.app.ui.camera

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.FlipCameraAndroid
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.faunary.app.ui.components.PermissionCard
import com.faunary.app.ui.theme.Buttercream
import com.faunary.app.ui.theme.Canyon
import com.faunary.app.ui.theme.DeepBrown
import com.faunary.app.ui.theme.FaunaryTheme
import com.faunary.app.ui.theme.SoftCream
import com.faunary.app.util.Format
import com.faunary.app.util.LocationPermissions
import com.faunary.app.util.findActivity
import com.faunary.app.util.rememberPermissionState
import java.io.File
import kotlin.math.roundToInt

@Composable
fun CameraScreen(
    onBack: () -> Unit,
    onPhotoReady: (path: String, exifLat: Double?, exifLng: Double?) -> Unit,
    viewModel: CameraViewModel = hiltViewModel(),
) {
    val c = FaunaryTheme.colors
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val fix by viewModel.lastFix.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    // The camera UI is always dark: force light status/nav bar icons while it is shown.
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val wasLight = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            if (wasLight != null) {
                controller.isAppearanceLightStatusBars = wasLight
                controller.isAppearanceLightNavigationBars = wasLight
            }
        }
    }

    val cameraPermission = rememberPermissionState(arrayOf(Manifest.permission.CAMERA))
    val locationPermission = rememberPermissionState(LocationPermissions) { if (it) viewModel.warmUpLocation() }
    LaunchedEffect(Unit) {
        if (!cameraPermission.granted) cameraPermission.request()
        if (locationPermission.granted) viewModel.warmUpLocation()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) viewModel.import(uri) { path, exif -> onPhotoReady(path, exif?.latitude, exif?.longitude) }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF1E1814))) {
        if (cameraPermission.granted) {
            val controller = remember {
                LifecycleCameraController(context).apply {
                    setEnabledUseCases(CameraController.IMAGE_CAPTURE)
                    imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                }
            }
            DisposableEffect(lifecycleOwner) {
                controller.bindToLifecycle(lifecycleOwner)
                onDispose { controller.unbind() }
            }
            var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_AUTO) }
            var zoom by remember { mutableFloatStateOf(1f) }
            var shutterFlash by remember { mutableStateOf(false) }

            Box(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = 64.dp, bottom = 190.dp)
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(28.dp)),
            ) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            this.controller = controller
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                ViewfinderCorners(Modifier.fillMaxSize().padding(40.dp))

                // Top overlay: flash + GPS readout
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassButton(
                        when (flashMode) {
                            ImageCapture.FLASH_MODE_ON -> Icons.Rounded.FlashOn
                            ImageCapture.FLASH_MODE_OFF -> Icons.Rounded.FlashOff
                            else -> Icons.Rounded.FlashAuto
                        },
                        "Flash",
                    ) {
                        flashMode = when (flashMode) {
                            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
                            else -> ImageCapture.FLASH_MODE_AUTO
                        }
                        controller.imageCaptureFlashMode = flashMode
                    }
                    Spacer(Modifier.width(8.dp))
                    Row(
                        Modifier.weight(1f).clip(CircleShape).background(DeepBrown.copy(alpha = 0.55f)).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.MyLocation, null, Modifier.size(14.dp), tint = Buttercream)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            fix?.let { f ->
                                Format.coordinates(f.latitude, f.longitude) + (f.accuracy?.let { " · ±${it.roundToInt()}m" } ?: "")
                            } ?: if (locationPermission.granted) "Mencari sinyal GPS…" else "Lokasi nonaktif",
                            style = MaterialTheme.typography.labelMedium,
                            color = SoftCream,
                            maxLines = 1,
                        )
                    }
                }

                // Zoom selector
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).clip(CircleShape)
                        .background(DeepBrown.copy(alpha = 0.55f)).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(1f, 2f, 3f).forEach { z ->
                        val selected = zoom == z
                        Box(
                            Modifier.size(38.dp).clip(CircleShape)
                                .background(if (selected) Canyon else Color.Transparent)
                                .clickable { zoom = z; controller.setZoomRatio(z) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${z.roundToInt()}x", style = MaterialTheme.typography.labelMedium, color = SoftCream)
                        }
                    }
                }

                AnimatedVisibility(shutterFlash, enter = fadeIn(tween(60)), exit = fadeOut(tween(200))) {
                    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.7f)))
                }
                LaunchedEffect(shutterFlash) {
                    if (shutterFlash) {
                        kotlinx.coroutines.delay(90)
                        shutterFlash = false
                    }
                }
            }

            // Bottom controls
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Arahkan kamera ke satwa lalu tekan rana",
                    style = MaterialTheme.typography.bodySmall,
                    color = SoftCream.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    LabeledGlassButton(Icons.Rounded.PhotoLibrary, "Galeri") {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    ShutterButton(enabled = !busy) {
                        val file = viewModel.newCaptureFile()
                        shutterFlash = true
                        viewModel.setBusy(true)
                        controller.takePicture(
                            ImageCapture.OutputFileOptions.Builder(file).build(),
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    viewModel.setBusy(false)
                                    onPhotoReady(file.absolutePath, null, null)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    viewModel.setBusy(false)
                                    viewModel.reportError("Gagal mengambil foto: ${exception.message}")
                                    File(file.absolutePath).delete()
                                }
                            },
                        )
                    }
                    LabeledGlassButton(Icons.Rounded.FlipCameraAndroid, "Putar") {
                        controller.cameraSelector =
                            if (controller.cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) CameraSelector.DEFAULT_FRONT_CAMERA
                            else CameraSelector.DEFAULT_BACK_CAMERA
                    }
                }
            }
        } else {
            Column(
                Modifier.align(Alignment.Center).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PermissionCard(
                    icon = Icons.Rounded.CameraAlt,
                    title = "Izinkan akses kamera",
                    message = "Kamera dipakai untuk memotret satwa yang kamu temui. AI mendeteksi jenis hewannya langsung di perangkat — foto tidak diunggah ke mana pun.",
                    actionLabel = "Izinkan Kamera",
                    onAction = { cameraPermission.request() },
                    permanentlyDenied = cameraPermission.permanentlyDenied,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "atau pilih dari galeri",
                    style = MaterialTheme.typography.labelLarge,
                    color = Buttercream,
                    modifier = Modifier.clip(CircleShape).clickable {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }.padding(12.dp),
                )
            }
        }

        // Top bar
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassButton(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali", onClick = onBack)
            Spacer(Modifier.width(12.dp))
            Text("Kamera", style = MaterialTheme.typography.titleLarge, color = SoftCream)
        }

        if (busy) {
            Box(Modifier.fillMaxSize().background(DeepBrown.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.highlight)
            }
        }
    }

    val error by viewModel.error.collectAsStateWithLifecycle()
    LaunchedEffect(error) {
        error?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.reportError(null)
        }
    }
}

@Composable
private fun ViewfinderCorners(modifier: Modifier) {
    Canvas(modifier) {
        val len = 28.dp.toPx()
        val stroke = 3.dp.toPx()
        val color = Buttercream.copy(alpha = 0.85f)
        val w = size.width
        val h = size.height
        listOf(
            Offset(0f, 0f) to (1 to 1), Offset(w, 0f) to (-1 to 1),
            Offset(0f, h) to (1 to -1), Offset(w, h) to (-1 to -1),
        ).forEach { (o, d) ->
            drawLine(color, o, Offset(o.x + len * d.first, o.y), stroke, StrokeCap.Round)
            drawLine(color, o, Offset(o.x, o.y + len * d.second), stroke, StrokeCap.Round)
        }
    }
}

@Composable
private fun GlassButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(DeepBrown.copy(alpha = 0.55f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(22.dp), tint = SoftCream)
    }
}

@Composable
private fun LabeledGlassButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(SoftCream.copy(alpha = 0.14f))
                .border(1.dp, SoftCream.copy(alpha = 0.2f), RoundedCornerShape(16.dp)).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, Modifier.size(24.dp), tint = SoftCream)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = SoftCream.copy(alpha = 0.85f))
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, tween(120), label = "shutter")
    Box(
        Modifier
            .scale(scale)
            .size(84.dp)
            .clip(CircleShape)
            .background(Canyon.copy(alpha = 0.25f))
            .border(3.dp, Canyon, CircleShape)
            .clickable(enabled = enabled, onClickLabel = "Ambil foto") {
                pressed = true
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(Canyon), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.CameraAlt, null, Modifier.size(28.dp), tint = SoftCream)
        }
    }
    LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(140)
            pressed = false
        }
    }
}
