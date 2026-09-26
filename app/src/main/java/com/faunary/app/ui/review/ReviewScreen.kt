package com.faunary.app.ui.review

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EditLocationAlt
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.faunary.app.domain.AnimalCategory
import com.faunary.app.domain.SpeciesCatalog
import com.faunary.app.ui.components.ButtonKind
import com.faunary.app.ui.components.DetectionPhoto
import com.faunary.app.ui.components.FaunaryButton
import com.faunary.app.ui.components.FaunaryCard
import com.faunary.app.ui.components.FaunaryTextField
import com.faunary.app.ui.components.IconBadge
import com.faunary.app.ui.components.Pill
import com.faunary.app.ui.components.SectionHeader
import com.faunary.app.ui.components.SelectableChip
import com.faunary.app.ui.components.SurfaceIconButton
import com.faunary.app.ui.components.softShadow
import com.faunary.app.ui.theme.FaunaryTheme
import com.faunary.app.util.Format
import com.faunary.app.util.LocationPermissions
import com.faunary.app.util.rememberPermissionState
import kotlin.math.roundToInt

@Composable
fun ReviewScreen(
    onBack: () -> Unit,
    onPickLocation: (start: String?) -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = FaunaryTheme.colors
    val locationPermission = rememberPermissionState(LocationPermissions) { if (it) viewModel.retryLocation() }

    LaunchedEffect(state.savedId) { state.savedId?.let(onSaved) }
    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 110.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                SurfaceIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali", onBack)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Hasil deteksi", style = MaterialTheme.typography.titleLarge, color = c.foreground)
                    Text("Periksa label lalu simpan ke koleksi", style = MaterialTheme.typography.bodySmall, color = c.foregroundSecondary)
                }
            }
            Spacer(Modifier.height(8.dp))

            // Photo + boxes
            Box {
                val photo = state.photo
                if (photo != null) {
                    DetectionPhoto(
                        photoPath = photo.path,
                        aspectRatio = photo.width.toFloat() / photo.height,
                        detections = state.detections,
                        selectedIndex = state.selectedIndex,
                        onSelect = viewModel::selectDetection,
                        modifier = Modifier.softShadow(RoundedCornerShape(22.dp), 8.dp),
                    )
                } else {
                    Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(22.dp)).background(c.surfaceMuted))
                }
                androidx.compose.animation.AnimatedVisibility(state.detecting, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
                    DetectingBadge(Modifier.padding(14.dp))
                }
            }
            Spacer(Modifier.height(16.dp))

            // Detection summary
            AnimatedContent(
                targetState = state.detecting,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                label = "detect",
            ) { detecting ->
                if (!detecting) {
                    if (state.detections.isNotEmpty()) {
                        FaunaryCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconBadge(Icons.Rounded.AutoAwesome, background = c.highlight)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (state.detections.size == 1) "1 satwa terdeteksi" else "${state.detections.size} satwa terdeteksi",
                                        style = MaterialTheme.typography.titleSmall, color = c.foreground,
                                    )
                                    Text(
                                        if (state.detections.size > 1) "Pilih satwa utama untuk entri ini" else "Deteksi on-device dengan ML Kit",
                                        style = MaterialTheme.typography.bodySmall, color = c.foregroundSecondary,
                                    )
                                }
                            }
                            if (state.detections.size > 1) {
                                Spacer(Modifier.height(12.dp))
                                @OptIn(ExperimentalLayoutApi::class)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    state.detections.forEachIndexed { i, d ->
                                        SelectableChip("${d.label} · ${Format.percent(d.confidence)}", i == state.selectedIndex, { viewModel.selectDetection(i) })
                                    }
                                }
                            }
                        }
                    } else {
                        FaunaryCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconBadge(Icons.Rounded.SearchOff, background = c.badgeSoft)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Satwa tidak terdeteksi", style = MaterialTheme.typography.titleSmall, color = c.foreground)
                                    Text("Tidak apa-apa — beri label sendiri di bawah dan tetap simpan fotonya.", style = MaterialTheme.typography.bodySmall, color = c.foregroundSecondary)
                                }
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(1.dp))
                }
            }
            Spacer(Modifier.height(16.dp))

            // Label
            SectionHeader("Jenis satwa", icon = Icons.Rounded.Pets)
            Spacer(Modifier.height(10.dp))
            FaunaryTextField(state.label, viewModel::setLabel, "Contoh: Kucing Kampung", leadingIcon = Icons.Rounded.Pets)
            Spacer(Modifier.height(10.dp))
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SpeciesCatalog.quickPicks.forEach { pick ->
                    SelectableChip(pick, state.label.equals(pick, ignoreCase = true), { viewModel.setLabel(pick) })
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Kategori", style = MaterialTheme.typography.labelLarge, color = c.foregroundSecondary)
            Spacer(Modifier.height(8.dp))
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AnimalCategory.entries.forEach { cat ->
                    SelectableChip(cat.displayName, state.category == cat, { viewModel.setCategory(cat) }, leading = cat.emoji)
                }
            }
            Spacer(Modifier.height(20.dp))

            // Location
            SectionHeader("Lokasi temuan", icon = Icons.Rounded.LocationOn)
            Spacer(Modifier.height(10.dp))
            LocationCard(
                status = state.location,
                onPick = {
                    val start = (state.location as? LocationStatus.Found)?.point?.let { "${it.latitude},${it.longitude}" }
                    onPickLocation(start)
                },
                onRetry = viewModel::retryLocation,
                onGrant = { locationPermission.request() },
                permanentlyDenied = locationPermission.permanentlyDenied,
            )
            Spacer(Modifier.height(20.dp))

            // Note
            SectionHeader("Catatan", icon = Icons.AutoMirrored.Rounded.Notes)
            Spacer(Modifier.height(10.dp))
            FaunaryTextField(
                state.note, viewModel::setNote,
                "Ceritakan pertemuanmu (opsional)",
                singleLine = false, minLines = 3, imeAction = ImeAction.Default,
            )
            state.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = c.danger)
            }
        }

        // Sticky save bar
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(c.background.copy(alpha = 0.96f))
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            FaunaryButton(
                text = when {
                    state.saving -> "Menyimpan…"
                    state.detecting -> "Mendeteksi satwa…"
                    state.label.isBlank() -> "Isi jenis satwa dulu"
                    state.location !is LocationStatus.Found -> "Tentukan lokasi dulu"
                    else -> "Simpan ke Koleksi"
                },
                onClick = viewModel::save,
                enabled = state.canSave,
                icon = Icons.Rounded.Check,
                modifier = Modifier.fillMaxWidth(),
                height = 52.dp,
            )
        }
    }
}

@Composable
private fun DetectingBadge(modifier: Modifier = Modifier) {
    val c = FaunaryTheme.colors
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0.5f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulseAlpha",
    )
    Row(
        modifier.clip(RoundedCornerShape(50)).background(c.surface.copy(alpha = 0.95f)).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp).alpha(pulse), tint = c.primary)
        Spacer(Modifier.width(8.dp))
        Text("Mendeteksi satwa…", style = MaterialTheme.typography.labelLarge, color = c.foreground)
    }
}

@Composable
private fun LocationCard(
    status: LocationStatus,
    onPick: () -> Unit,
    onRetry: () -> Unit,
    onGrant: () -> Unit,
    permanentlyDenied: Boolean,
) {
    val c = FaunaryTheme.colors
    FaunaryCard {
        when (status) {
            LocationStatus.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(22.dp), color = c.primary, strokeWidth = 2.5.dp)
                Spacer(Modifier.width(12.dp))
                Text("Mengambil lokasi GPS…", style = MaterialTheme.typography.bodyMedium, color = c.foregroundSecondary)
            }
            is LocationStatus.Found -> {
                Row(verticalAlignment = Alignment.Top) {
                    IconBadge(if (status.manual) Icons.Rounded.EditLocationAlt else Icons.Rounded.GpsFixed, background = c.badgeNature)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            status.place?.shortName ?: "Titik lokasi tersimpan",
                            style = MaterialTheme.typography.titleSmall, color = c.foreground,
                        )
                        Text(
                            Format.coordinates(status.point.latitude, status.point.longitude),
                            style = MaterialTheme.typography.bodySmall, color = c.foregroundSecondary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Pill(
                            when {
                                status.manual -> "Ditandai manual"
                                status.fromExif -> "Dari metadata foto"
                                else -> "GPS" + (status.point.accuracy?.let { " ±${it.roundToInt()}m" } ?: "")
                            },
                            color = c.badgeSoft, contentColor = c.foregroundSecondary,
                        )
                    }
                }
                val weak = !status.manual && (status.point.accuracy ?: 0f) > 50f
                if (weak) {
                    Spacer(Modifier.height(10.dp))
                    Text("Akurasi GPS rendah. Pertimbangkan untuk menandai lokasi di peta.", style = MaterialTheme.typography.bodySmall, color = c.warning)
                }
                Spacer(Modifier.height(12.dp))
                FaunaryButton("Ubah di Peta", onPick, Modifier.fillMaxWidth(), kind = ButtonKind.Ghost, icon = Icons.Rounded.EditLocationAlt, height = 44.dp)
            }
            LocationStatus.NoPermission, LocationStatus.Unavailable -> {
                Row(verticalAlignment = Alignment.Top) {
                    IconBadge(Icons.Rounded.LocationOff, background = c.badgeSoft)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (status == LocationStatus.NoPermission) "Izin lokasi belum diberikan" else "GPS tidak tersedia",
                            style = MaterialTheme.typography.titleSmall, color = c.foreground,
                        )
                        Text(
                            if (status == LocationStatus.NoPermission) "Izinkan lokasi untuk mencatat titik otomatis, atau tandai sendiri di peta."
                            else "Sinyal lemah atau GPS mati. Coba lagi atau tandai lokasinya di peta.",
                            style = MaterialTheme.typography.bodySmall, color = c.foregroundSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (status == LocationStatus.NoPermission && !permanentlyDenied) {
                        FaunaryButton("Izinkan", onGrant, Modifier.weight(1f), kind = ButtonKind.Secondary, height = 44.dp)
                    } else if (status == LocationStatus.Unavailable) {
                        FaunaryButton("Coba Lagi", onRetry, Modifier.weight(1f), kind = ButtonKind.Secondary, icon = Icons.Rounded.Refresh, height = 44.dp)
                    }
                    FaunaryButton("Tandai di Peta", onPick, Modifier.weight(1f), icon = Icons.Rounded.EditLocationAlt, height = 44.dp)
                }
            }
        }
    }
}
