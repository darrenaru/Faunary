package com.faunary.app.ui.profile

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.faunary.app.BuildConfig
import com.faunary.app.data.AnimalSighting
import com.faunary.app.data.Settings
import com.faunary.app.data.SettingsRepository
import com.faunary.app.data.SightingRepository
import com.faunary.app.data.ThemeMode
import com.faunary.app.domain.CollectionStats
import com.faunary.app.remote.SupabaseProvider
import com.faunary.app.update.UpdateCard
import com.faunary.app.update.UpdateViewModel
import com.faunary.app.update.rememberInstallAction
import com.faunary.app.ui.components.ButtonKind
import com.faunary.app.ui.components.FaunaryButton
import com.faunary.app.ui.components.FaunaryCard
import com.faunary.app.ui.components.FaunaryTextField
import com.faunary.app.ui.components.IconBadge
import com.faunary.app.ui.components.SelectableChip
import com.faunary.app.ui.map.BottomBarSpace
import com.faunary.app.ui.theme.FaunaryTheme
import com.faunary.app.util.Format
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import kotlin.math.roundToInt

data class ProfileUiState(
    val settings: Settings = Settings(),
    val stats: CollectionStats = CollectionStats(),
    val firstEntry: Long? = null,
)

@Serializable
private data class ExportEntry(
    val id: Long, val label: String, val category: String, val confidence: Float, val aiLabel: String?,
    val latitude: Double, val longitude: Double, val locationName: String?, val address: String?,
    val note: String?, val favorite: Boolean, val timestamp: Long, val photoFile: String,
)

private val ExportJson = Json { prettyPrint = true }

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val repository: SightingRepository,
    private val supabase: SupabaseProvider,
) : ViewModel() {

    val online: Boolean get() = supabase.isConfigured

    val state: StateFlow<ProfileUiState> = combine(settingsRepo.settings, repository.observeAll()) { s, all ->
        ProfileUiState(s, CollectionStats.from(all), all.minOfOrNull { it.timestamp })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState(settingsRepo.settings.value))

    fun setTheme(mode: ThemeMode) = settingsRepo.setThemeMode(mode)
    fun setMap3D(enabled: Boolean) = settingsRepo.setMap3D(enabled)
    fun setMinConfidence(v: Float) = settingsRepo.setMinConfidence(v)
    fun setName(name: String) {
        settingsRepo.setExplorerName(name)
        viewModelScope.launch { supabase.syncProfile() }
    }

    fun setShareLive(enabled: Boolean) = settingsRepo.setShareLiveLocation(enabled)

    /** Writes the collection as JSON and opens the share sheet. */
    fun export(context: Context) = viewModelScope.launch {
        val file = withContext(Dispatchers.IO) {
            val entries = repository.getAll().map { it.toExport() }
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            File(dir, "faunary-koleksi.json").apply {
                writeText(ExportJson.encodeToString(entries))
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Ekspor koleksi"))
    }

    private fun AnimalSighting.toExport() = ExportEntry(
        id, animalLabel, category, confidence, aiLabel, latitude, longitude,
        locationName, address, note, isFavorite, timestamp, File(photoPath).name,
    )
}

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val update by updateViewModel.state.collectAsStateWithLifecycle()
    val installUpdate = rememberInstallAction(updateViewModel)
    val c = FaunaryTheme.colors
    val context = LocalContext.current
    var editName by rememberSaveable { mutableStateOf(false) }
    var confidence by remember(state.settings.minConfidence) { mutableFloatStateOf(state.settings.minConfidence) }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = BottomBarSpace + 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text("Profil", style = MaterialTheme.typography.labelLarge, color = c.primary)
            Text("Penjelajah", style = MaterialTheme.typography.displaySmall, color = c.foreground)
        }

        FaunaryCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(c.highlight), contentAlignment = Alignment.Center) {
                    Text(
                        state.settings.explorerName.take(1).uppercase(),
                        style = MaterialTheme.typography.displaySmall, color = c.onHighlight,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.settings.explorerName, style = MaterialTheme.typography.titleLarge, color = c.foreground)
                    Text(
                        state.firstEntry?.let { "Menjelajah sejak ${Format.monthYear(it)}" } ?: "Belum ada temuan",
                        style = MaterialTheme.typography.bodySmall, color = c.foregroundSecondary,
                    )
                }
                Box(Modifier.size(40.dp).clip(CircleShape).clickable { editName = true }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Edit, "Ubah nama", tint = c.brand)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row {
                MiniStat("${state.stats.total}", "Temuan", Modifier.weight(1f))
                MiniStat("${state.stats.distinctSpecies}", "Jenis", Modifier.weight(1f))
                MiniStat("${state.stats.distinctSpots}", "Titik", Modifier.weight(1f))
            }
        }

        FaunaryCard {
            SettingTitle(Icons.Rounded.DarkMode, "Tampilan", "Mode gelap memakai nuansa cokelat hangat")
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { m ->
                    SelectableChip(m.displayName, state.settings.themeMode == m, { viewModel.setTheme(m) })
                }
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = c.border)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Peta 3D", style = MaterialTheme.typography.titleSmall, color = c.foreground)
                    Text("Relief bukit, gedung 3D, dan kamera miring. Lebih boros baterai.", style = MaterialTheme.typography.bodySmall, color = c.foregroundSecondary)
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = state.settings.map3D,
                    onCheckedChange = viewModel::setMap3D,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = c.onPrimary, checkedTrackColor = c.primary,
                        uncheckedThumbColor = c.foregroundMuted, uncheckedTrackColor = c.surfaceMuted, uncheckedBorderColor = c.border,
                    ),
                )
            }
        }

        if (viewModel.online) {
            FaunaryCard {
                SettingTitle(Icons.Rounded.Public, "Komunitas", "Temuanmu tampil publik di peta sebagai \"${state.settings.explorerName}\"")
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Bagikan lokasi live", style = MaterialTheme.typography.titleSmall, color = c.foreground)
                        Text("Penjelajah lain melihat posisimu selama aplikasi terbuka.", style = MaterialTheme.typography.bodySmall, color = c.foregroundSecondary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = state.settings.shareLiveLocation,
                        onCheckedChange = viewModel::setShareLive,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = c.onPrimary, checkedTrackColor = c.primary,
                            uncheckedThumbColor = c.foregroundMuted, uncheckedTrackColor = c.surfaceMuted, uncheckedBorderColor = c.border,
                        ),
                    )
                }
            }
        }

        FaunaryCard {
            SettingTitle(Icons.Rounded.AutoAwesome, "Ambang keyakinan AI", "Deteksi di bawah nilai ini diabaikan")
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = confidence,
                    onValueChange = { confidence = it },
                    onValueChangeFinished = { viewModel.setMinConfidence(confidence) },
                    valueRange = 0.3f..0.9f,
                    steps = 5,
                    colors = SliderDefaults.colors(thumbColor = c.primary, activeTrackColor = c.primary, inactiveTrackColor = c.surfaceMuted),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Text("${(confidence * 100).roundToInt()}%", style = MaterialTheme.typography.titleMedium, color = c.foreground)
            }
        }

        FaunaryCard {
            SettingTitle(Icons.Rounded.FileDownload, "Ekspor koleksi", "Simpan data temuan sebagai file JSON")
            Spacer(Modifier.height(12.dp))
            FaunaryButton(
                "Ekspor JSON", { viewModel.export(context) }, Modifier.fillMaxWidth(),
                kind = ButtonKind.Secondary, icon = Icons.Rounded.FileDownload, enabled = state.stats.total > 0, height = 46.dp,
            )
        }

        FaunaryCard {
            SettingTitle(
                Icons.Rounded.SystemUpdate, "Pembaruan aplikasi",
                "Versi ${BuildConfig.VERSION_NAME}" + when {
                    BuildConfig.DEBUG -> " · build pengembangan"
                    update.hasUpdate -> " · versi ${update.availableVersion} tersedia"
                    update.lastChecked > 0 -> " · sudah terbaru"
                    else -> ""
                },
            )
            if (update.hasUpdate) {
                Spacer(Modifier.height(12.dp))
                UpdateCard(update, onInstall = installUpdate, onDownloadNow = updateViewModel::downloadNow)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Unduh lewat data seluler", style = MaterialTheme.typography.titleSmall, color = c.foreground)
                    Text("Nonaktif: pembaruan hanya diunduh saat terhubung Wi-Fi.", style = MaterialTheme.typography.bodySmall, color = c.foregroundSecondary)
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = update.allowMobileData,
                    onCheckedChange = updateViewModel::setAllowMobileData,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = c.onPrimary, checkedTrackColor = c.primary,
                        uncheckedThumbColor = c.foregroundMuted, uncheckedTrackColor = c.surfaceMuted, uncheckedBorderColor = c.border,
                    ),
                )
            }
            if (!BuildConfig.DEBUG) {
                Spacer(Modifier.height(10.dp))
                FaunaryButton("Cek Pembaruan", updateViewModel::checkNow, Modifier.fillMaxWidth(), kind = ButtonKind.Ghost, height = 44.dp)
            }
        }

        FaunaryCard {
            SettingTitle(Icons.Rounded.Lock, "Privasi", null)
            Spacer(Modifier.height(8.dp))
            Text(
                if (viewModel.online) {
                    "Temuan (foto, jenis, catatan, dan lokasi) dibagikan publik ke peta komunitas. Favorit tetap pribadi di perangkat ini. Deteksi AI berjalan offline di HP-mu."
                } else {
                    "Semua foto, lokasi, dan catatan disimpan hanya di perangkat ini. Deteksi AI berjalan offline."
                },
                style = MaterialTheme.typography.bodyMedium, color = c.foregroundSecondary,
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Info, null, Modifier.size(14.dp), tint = c.foregroundMuted)
            Spacer(Modifier.width(6.dp))
            Text("Faunary v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium, color = c.foregroundMuted)
        }
    }

    if (editName) {
        var name by rememberSaveable { mutableStateOf(state.settings.explorerName) }
        AlertDialog(
            onDismissRequest = { editName = false },
            containerColor = c.surface,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Nama penjelajah", color = c.foreground) },
            text = { FaunaryTextField(name, { name = it.take(32) }, "Nama kamu") },
            confirmButton = { FaunaryButton("Simpan", { viewModel.setName(name); editName = false }, height = 44.dp) },
            dismissButton = { FaunaryButton("Batal", { editName = false }, kind = ButtonKind.Ghost, height = 44.dp) },
        )
    }
}

@Composable
private fun MiniStat(value: String, label: String, modifier: Modifier) {
    val c = FaunaryTheme.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = c.foreground)
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.foregroundSecondary)
    }
}

@Composable
private fun SettingTitle(icon: ImageVector, title: String, subtitle: String?) {
    val c = FaunaryTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.foreground)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.foregroundSecondary)
        }
    }
}
