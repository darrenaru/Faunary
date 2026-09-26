package com.faunary.app.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.faunary.app.ui.components.ButtonKind
import com.faunary.app.ui.components.FaunaryButton
import com.faunary.app.ui.components.FaunaryCard
import com.faunary.app.ui.components.IconBadge
import com.faunary.app.ui.components.SurfaceIconButton
import com.faunary.app.ui.theme.FaunaryTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updates: UpdateRepository,
    private val scheduler: UpdateScheduler,
    private val installer: UpdateInstaller,
) : ViewModel() {
    val state: StateFlow<UpdateState> = updates.state

    /** Hidden for this session after the user closes the banner. */
    val bannerDismissed = MutableStateFlow(false)

    fun checkNow() = scheduler.checkNow()
    fun downloadNow() = scheduler.downloadNow()
    fun setAllowMobileData(allow: Boolean) {
        updates.setAllowMobileData(allow)
        if (allow && state.value.hasUpdate && !state.value.ready) scheduler.downloadNow()
    }

    fun canInstall() = installer.canInstall()
    fun installPermissionIntent() = installer.openInstallPermissionSettings()
    fun install() = viewModelScope.launch { installer.install() }
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> String.format(Locale.forLanguageTag("id-ID"), "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

/** Compact card used on the map (when there's something actionable) and in Profile (always). */
@Composable
fun UpdateCard(
    state: UpdateState,
    onInstall: () -> Unit,
    onDownloadNow: () -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
) {
    val c = FaunaryTheme.colors
    FaunaryCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.SystemUpdate, background = c.highlight)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (state.ready) "Versi ${state.availableVersion} siap dipasang" else "Versi ${state.availableVersion} tersedia",
                    style = MaterialTheme.typography.titleSmall, color = c.foreground,
                )
                val sizeText = formatBytes(state.downloadSize) + if (state.isPatch) " · hanya bagian yang berubah" else ""
                Text(
                    when {
                        state.ready -> state.notes.ifBlank { "Ketuk Pasang untuk memperbarui." }
                        state.downloading -> {
                            val pct = if (state.downloadSize > 0) (state.downloadedBytes * 100 / state.downloadSize).coerceIn(0, 100) else 0
                            "Mengunduh $pct% · ${formatBytes(state.downloadedBytes)} dari ${formatBytes(state.downloadSize)}"
                        }
                        state.error != null -> state.error
                        state.waitingForWifi -> "$sizeText · menunggu Wi-Fi"
                        else -> sizeText
                    },
                    style = MaterialTheme.typography.bodySmall, color = c.foregroundSecondary, maxLines = 2,
                )
            }
            if (onDismiss != null) {
                SurfaceIconButton(Icons.Rounded.Close, "Tutup", onDismiss, size = 36.dp)
            }
        }
        if (state.downloading && state.downloadSize > 0) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { (state.downloadedBytes.toFloat() / state.downloadSize).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = c.primary, trackColor = c.surfaceMuted,
            )
        }
        if (state.ready || (!state.downloading && state.waitingForWifi)) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.ready) {
                    FaunaryButton("Pasang", onInstall, Modifier.weight(1f), height = 44.dp)
                } else {
                    FaunaryButton("Unduh pakai data seluler", onDownloadNow, Modifier.weight(1f), kind = ButtonKind.Secondary, height = 44.dp)
                }
            }
        }
    }
}

/** Starts install, first sending the user to the one-time "install unknown apps" setting if needed. */
@Composable
fun rememberInstallAction(viewModel: UpdateViewModel): () -> Unit {
    val context = LocalContext.current
    return {
        if (viewModel.canInstall()) viewModel.install()
        else context.startActivity(viewModel.installPermissionIntent())
    }
}

/**
 * Full-screen gate shown instead of the app while [UpdateState.mandatory]: no close button and no
 * way around it (Back just leaves the app). "Update Sekarang" downloads if needed, then installs.
 */
@Composable
fun MandatoryUpdateScreen(viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val install = rememberInstallAction(viewModel)
    val c = FaunaryTheme.colors
    // Tapped before the download finished: install as soon as it's ready.
    var installWhenReady by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.ready, installWhenReady) {
        if (state.ready && installWhenReady) {
            installWhenReady = false
            install()
        }
    }
    val busy = state.downloading || (installWhenReady && !state.ready)

    Box(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.size(96.dp).clip(CircleShape).background(c.highlight), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.SystemUpdate, null, Modifier.size(48.dp), tint = c.onHighlight)
            }
            Spacer(Modifier.height(24.dp))
            Text("Pembaruan wajib", style = MaterialTheme.typography.headlineMedium, color = c.foreground, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "Versi ${state.availableVersion ?: "terbaru"} diperlukan untuk melanjutkan. Perbarui Faunary agar tetap bisa memakai aplikasi.",
                style = MaterialTheme.typography.bodyLarge, color = c.foregroundSecondary, textAlign = TextAlign.Center,
            )
            if (state.notes.isNotBlank()) {
                Spacer(Modifier.height(20.dp))
                FaunaryCard(Modifier.fillMaxWidth()) {
                    Text("Yang baru", style = MaterialTheme.typography.labelLarge, color = c.foregroundSecondary)
                    Spacer(Modifier.height(6.dp))
                    Text(state.notes, style = MaterialTheme.typography.bodyMedium, color = c.foreground)
                }
            }
            Spacer(Modifier.height(24.dp))
            if (state.downloading && state.downloadSize > 0) {
                LinearProgressIndicator(
                    progress = { (state.downloadedBytes.toFloat() / state.downloadSize).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = c.primary, trackColor = c.surfaceMuted,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Mengunduh ${formatBytes(state.downloadedBytes)} dari ${formatBytes(state.downloadSize)}",
                    style = MaterialTheme.typography.bodySmall, color = c.foregroundSecondary,
                )
                Spacer(Modifier.height(16.dp))
            } else {
                state.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = c.danger, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                }
            }
            FaunaryButton(
                text = if (busy) "Mengunduh pembaruan…" else "Update Sekarang",
                onClick = {
                    if (state.ready) install()
                    else {
                        installWhenReady = true
                        viewModel.downloadNow()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.SystemUpdate,
                enabled = !busy,
            )
            if (!state.ready && !busy && state.downloadSize > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Ukuran unduhan ${formatBytes(state.downloadSize)}" + if (state.isPatch) " (hanya bagian yang berubah)" else "",
                    style = MaterialTheme.typography.bodySmall, color = c.foregroundMuted, textAlign = TextAlign.Center,
                )
            }
        }
    }
}
