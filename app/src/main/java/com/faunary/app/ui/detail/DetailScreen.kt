package com.faunary.app.ui.detail

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditLocationAlt
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.PinDrop
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.faunary.app.BuildConfig
import com.faunary.app.data.AnimalSighting
import com.faunary.app.data.SyncState
import com.faunary.app.domain.AnimalCategory
import com.faunary.app.domain.SpeciesCatalog
import com.faunary.app.ui.components.ButtonKind
import com.faunary.app.ui.components.DetectionPhoto
import com.faunary.app.ui.components.EmptyState
import com.faunary.app.ui.components.FaunaryButton
import com.faunary.app.ui.components.FaunaryCard
import com.faunary.app.ui.components.FaunaryTextField
import com.faunary.app.ui.components.IconBadge
import com.faunary.app.ui.components.Pill
import com.faunary.app.ui.components.SelectableChip
import com.faunary.app.ui.components.SurfaceIconButton
import com.faunary.app.ui.components.icon
import com.faunary.app.ui.components.softShadow
import com.faunary.app.ui.theme.FaunaryTheme
import com.faunary.app.util.Format
import com.faunary.app.util.openDirections
import java.io.File
import kotlin.math.roundToInt

@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onShowOnMap: (Long) -> Unit,
    onEditLocation: (start: String) -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = FaunaryTheme.colors
    Box(Modifier.fillMaxSize().background(c.background)) {
        when (val s = state) {
            DetailUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = c.primary)
            DetailUiState.Missing -> Column(Modifier.align(Alignment.Center)) {
                EmptyState(Icons.Rounded.PinDrop, "Temuan tidak ditemukan", "Entri ini mungkin sudah dihapus.")
                FaunaryButton("Kembali", onBack, Modifier.align(Alignment.CenterHorizontally), kind = ButtonKind.Ghost)
            }
            is DetailUiState.Ready -> DetailContent(s.sighting, viewModel, onBack, onShowOnMap, onEditLocation)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(
    s: AnimalSighting,
    viewModel: DetailViewModel,
    onBack: () -> Unit,
    onShowOnMap: (Long) -> Unit,
    onEditLocation: (String) -> Unit,
) {
    val c = FaunaryTheme.colors
    val context = LocalContext.current
    var showBoxes by rememberSaveable { mutableStateOf(true) }
    var selected by rememberSaveable { mutableStateOf(0) }
    var editLabel by rememberSaveable { mutableStateOf(false) }
    var editNote by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SurfaceIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali", onBack)
            Spacer(Modifier.width(12.dp))
            Text("Detail temuan", style = MaterialTheme.typography.titleLarge, color = c.foreground, modifier = Modifier.weight(1f))
            SurfaceIconButton(Icons.Rounded.Share, "Bagikan foto", {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(s.photoPath))
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, "${s.animalLabel} — ditemukan ${Format.fullDate(s.timestamp)}" + (s.locationName?.let { " di $it" } ?: ""))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Bagikan temuan"))
            })
            Spacer(Modifier.width(8.dp))
            SurfaceIconButton(
                if (s.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                "Favorit", { viewModel.toggleFavorite() }, tint = c.primary,
            )
        }
        Spacer(Modifier.height(8.dp))

        Box {
            DetectionPhoto(
                photoPath = s.photoPath,
                aspectRatio = s.aspectRatio,
                detections = s.detections,
                showBoxes = showBoxes,
                selectedIndex = selected,
                onSelect = { selected = it },
                modifier = Modifier.softShadow(RoundedCornerShape(22.dp), 8.dp),
            )
            if (s.detections.any { it.hasBox }) {
                Row(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .clip(CircleShape)
                        .background(c.surface.copy(alpha = 0.92f))
                        .clickable { showBoxes = !showBoxes }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.CenterFocusStrong, null, Modifier.size(16.dp), tint = c.brand)
                    Spacer(Modifier.width(6.dp))
                    Text(if (showBoxes) "Kotak deteksi: on" else "Kotak deteksi: off", style = MaterialTheme.typography.labelMedium, color = c.foreground)
                }
            }
        }
        Spacer(Modifier.height(18.dp))

        // Title + badges
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.animalLabel, style = MaterialTheme.typography.displaySmall, color = c.foreground)
                if (!s.animalLabel.equals(s.animalCategory.displayName, ignoreCase = true)) {
                    Text(s.animalCategory.displayName, style = MaterialTheme.typography.bodyMedium, color = c.foregroundSecondary)
                }
            }
            IconBadge(s.animalCategory.icon, background = c.highlight, size = 48.dp)
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.isAiDetected) Pill("Akurasi AI ${Format.percent(s.confidence)}", icon = Icons.Rounded.Verified)
            if (s.wasCorrected) Pill("Dikoreksi dari \"${s.aiLabel}\"", icon = Icons.Rounded.AutoFixHigh, color = c.badgeInfo)
            if (!s.isAiDetected) Pill("Label manual", icon = Icons.Rounded.Edit, color = c.badgeSoft)
            Pill(s.animalCategory.displayName, leading = s.animalCategory.emoji, color = c.badgeNature)
            if (BuildConfig.SUPABASE_URL.isNotBlank()) {
                if (s.syncState == SyncState.SYNCED) Pill("Publik di peta", icon = Icons.Rounded.Public, color = c.badgeInfo)
                else Pill("Menunggu unggah", icon = Icons.Rounded.CloudUpload, color = c.badgeSoft)
            }
        }

        if (s.detections.size > 1) {
            Spacer(Modifier.height(16.dp))
            FaunaryCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.AutoAwesome, background = c.highlight, size = 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("${s.detections.size} satwa dalam foto ini", style = MaterialTheme.typography.titleSmall, color = c.foreground)
                }
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.detections.forEachIndexed { i, d ->
                        SelectableChip("${d.label} · ${Format.percent(d.confidence)}", i == selected, { selected = i; showBoxes = true })
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // Time
        FaunaryCard {
            CardTitle(Icons.Rounded.Schedule, "Waktu")
            Spacer(Modifier.height(10.dp))
            Text(Format.fullDate(s.timestamp).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium, color = c.foreground)
            Text("Pukul ${Format.time(s.timestamp)}", style = MaterialTheme.typography.bodyMedium, color = c.foregroundSecondary)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(Format.dayPart(s.timestamp), icon = Icons.Rounded.WbSunny, color = c.badgeSoft, contentColor = c.foregroundSecondary)
                Pill(Format.relative(s.timestamp), color = c.badgeSoft, contentColor = c.foregroundSecondary)
            }
        }
        Spacer(Modifier.height(12.dp))

        // Location
        FaunaryCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardTitle(Icons.Rounded.PinDrop, "Lokasi & koordinat", Modifier.weight(1f))
                Pill(
                    if (s.locationManual) "Manual" else "GPS" + (s.locationAccuracy?.let { " ±${it.roundToInt()}m" } ?: ""),
                    icon = if (s.locationManual) Icons.Rounded.EditLocationAlt else Icons.Rounded.GpsFixed,
                    color = c.badgeNature,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(s.locationName ?: "Nama lokasi tidak tersedia", style = MaterialTheme.typography.titleMedium, color = c.foreground)
            s.address?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.foregroundSecondary) }
            Spacer(Modifier.height(6.dp))
            Text(Format.coordinates(s.latitude, s.longitude), style = MaterialTheme.typography.labelLarge, color = c.brand)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FaunaryButton("Lihat di Peta", { onShowOnMap(s.id) }, Modifier.weight(1f), kind = ButtonKind.Secondary, icon = Icons.Rounded.Map, height = 44.dp)
                FaunaryButton("Rute", { context.openDirections(s.latitude, s.longitude, s.animalLabel) }, Modifier.weight(1f), icon = Icons.Rounded.Directions, height = 44.dp)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Ubah lokasi",
                style = MaterialTheme.typography.labelLarge, color = c.primary,
                modifier = Modifier.align(Alignment.CenterHorizontally).clip(CircleShape)
                    .clickable { onEditLocation("${s.latitude},${s.longitude}") }.padding(10.dp),
            )
        }
        Spacer(Modifier.height(12.dp))

        // Note
        FaunaryCard(onClick = { editNote = true }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardTitle(Icons.AutoMirrored.Rounded.Notes, "Catatan pribadi", Modifier.weight(1f))
                Text("Ubah", style = MaterialTheme.typography.labelLarge, color = c.primary)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                s.note?.let { "“$it”" } ?: "Belum ada catatan. Ketuk untuk menambahkan cerita pertemuanmu.",
                style = MaterialTheme.typography.bodyLarge,
                color = if (s.note != null) c.foreground else c.foregroundMuted,
            )
        }
        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FaunaryButton("Koreksi Label", { editLabel = true }, Modifier.weight(1f), kind = ButtonKind.Secondary, icon = Icons.Rounded.AutoFixHigh)
            FaunaryButton("Edit Catatan", { editNote = true }, Modifier.weight(1f), kind = ButtonKind.Ghost, icon = Icons.Rounded.Edit)
        }
        Spacer(Modifier.height(10.dp))
        FaunaryButton("Hapus Temuan Ini", { confirmDelete = true }, Modifier.fillMaxWidth(), kind = ButtonKind.Danger, icon = Icons.Rounded.DeleteOutline)
    }

    if (editLabel) {
        EditLabelDialog(s, onDismiss = { editLabel = false }) { label, cat ->
            viewModel.updateLabel(label, cat)
            editLabel = false
        }
    }
    if (editNote) {
        EditNoteDialog(s.note.orEmpty(), onDismiss = { editNote = false }) {
            viewModel.updateNote(it)
            editNote = false
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = c.surface,
            shape = RoundedCornerShape(28.dp),
            icon = { Icon(Icons.Rounded.DeleteOutline, null, tint = c.danger) },
            title = { Text("Hapus temuan?", color = c.foreground) },
            text = {
                val where = if (BuildConfig.SUPABASE_URL.isNotBlank()) "dari perangkat ini dan dari peta publik" else "dari perangkat ini"
                Text("Foto, lokasi, dan catatan untuk ${s.animalLabel} akan dihapus permanen $where.", color = c.foregroundSecondary)
            },
            confirmButton = {
                FaunaryButton("Hapus", { confirmDelete = false; viewModel.delete(onBack) }, kind = ButtonKind.Danger, height = 44.dp)
            },
            dismissButton = { FaunaryButton("Batal", { confirmDelete = false }, kind = ButtonKind.Ghost, height = 44.dp) },
        )
    }
}

@Composable
private fun CardTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, modifier: Modifier = Modifier) {
    val c = FaunaryTheme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = c.brand)
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.labelLarge, color = c.foregroundSecondary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditLabelDialog(s: AnimalSighting, onDismiss: () -> Unit, onSave: (String, AnimalCategory) -> Unit) {
    val c = FaunaryTheme.colors
    var label by rememberSaveable { mutableStateOf(s.animalLabel) }
    var category by rememberSaveable { mutableStateOf(s.animalCategory) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Koreksi label", color = c.foreground) },
        text = {
            Column {
                if (s.aiLabel != null) {
                    Text("AI mendeteksi: ${s.aiLabel} (${Format.percent(s.confidence)})", style = MaterialTheme.typography.bodySmall, color = c.foregroundSecondary)
                    Spacer(Modifier.height(10.dp))
                }
                FaunaryTextField(label, {
                    label = it
                    category = SpeciesCatalog.categoryForUserLabel(it).takeIf { cat -> cat != AnimalCategory.OTHER } ?: category
                }, "Jenis satwa")
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AnimalCategory.entries.forEach { cat ->
                        SelectableChip(cat.displayName, category == cat, { category = cat }, leading = cat.emoji)
                    }
                }
            }
        },
        confirmButton = { FaunaryButton("Simpan", { onSave(label, category) }, enabled = label.isNotBlank(), height = 44.dp) },
        dismissButton = { FaunaryButton("Batal", onDismiss, kind = ButtonKind.Ghost, height = 44.dp) },
    )
}

@Composable
private fun EditNoteDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val c = FaunaryTheme.colors
    var note by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Catatan pribadi", color = c.foreground) },
        text = {
            FaunaryTextField(note, { note = it }, "Ceritakan pertemuanmu…", singleLine = false, minLines = 4, imeAction = ImeAction.Default)
        },
        confirmButton = { FaunaryButton("Simpan", { onSave(note) }, height = 44.dp) },
        dismissButton = { FaunaryButton("Batal", onDismiss, kind = ButtonKind.Ghost, height = 44.dp) },
    )
}
