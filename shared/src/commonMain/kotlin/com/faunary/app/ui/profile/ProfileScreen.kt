package com.faunary.app.ui.profile

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
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.faunary.app.data.ThemeMode
import com.faunary.app.ui.components.ButtonKind
import com.faunary.app.ui.components.FaunaryButton
import com.faunary.app.ui.components.FaunaryCard
import com.faunary.app.ui.components.FaunaryTextField
import com.faunary.app.ui.components.IconBadge
import com.faunary.app.ui.components.SelectableChip
import com.faunary.app.ui.map.BottomBarSpace
import com.faunary.app.ui.platform.AppInfo
import com.faunary.app.ui.platform.rememberSharer
import com.faunary.app.ui.theme.FaunaryTheme
import com.faunary.app.util.Format
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
/** [updateSection] is the platform's app-update card (Android's self-updater); iOS updates through the store. */
fun ProfileScreen(
    viewModel: ProfileViewModel = koinViewModel(),
    updateSection: (@Composable () -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val appInfo = koinInject<AppInfo>()
    val sharer = rememberSharer()
    val scope = rememberCoroutineScope()
    val c = FaunaryTheme.colors
    var editName by rememberSaveable { mutableStateOf(false) }

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
                    Text("Relief bukit, gedung, landmark, dan pohon 3D dengan kamera miring. Lebih boros baterai dan data.", style = MaterialTheme.typography.bodySmall, color = c.foregroundSecondary)
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
                        Text("Penjelajah lain melihat posisimu dan rute yang sedang kamu tuju selama aplikasi terbuka.", style = MaterialTheme.typography.bodySmall, color = c.foregroundSecondary)
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
            SettingTitle(Icons.Rounded.FileDownload, "Ekspor koleksi", "Simpan data temuan sebagai file JSON")
            Spacer(Modifier.height(12.dp))
            FaunaryButton(
                "Ekspor JSON",
                { scope.launch { sharer.shareFile("faunary-koleksi.json", "application/json", viewModel.exportJson(), "Ekspor koleksi") } },
                Modifier.fillMaxWidth(),
                kind = ButtonKind.Secondary, icon = Icons.Rounded.FileDownload, enabled = state.stats.total > 0, height = 46.dp,
            )
        }

        updateSection?.invoke()

        FaunaryCard {
            SettingTitle(Icons.Rounded.Lock, "Privasi", null)
            Spacer(Modifier.height(8.dp))
            Text(
                if (viewModel.online) {
                    "Temuan (foto, jenis, catatan, dan lokasi) dibagikan publik ke peta komunitas. Favorit tetap pribadi di perangkat ini. Untuk mengenali jenis hewan, foto dikirim ke layanan AI (Google Gemini); tanpa internet, deteksi berjalan di HP-mu."
                } else {
                    "Semua foto, lokasi, dan catatan disimpan hanya di perangkat ini. Deteksi AI berjalan offline."
                },
                style = MaterialTheme.typography.bodyMedium, color = c.foregroundSecondary,
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Info, null, Modifier.size(14.dp), tint = c.foregroundMuted)
            Spacer(Modifier.width(6.dp))
            Text("Faunary v${appInfo.versionName}", style = MaterialTheme.typography.labelMedium, color = c.foregroundMuted)
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

/** Icon badge + title (+ subtitle) heading a settings card. */
@Composable
fun SettingTitle(icon: ImageVector, title: String, subtitle: String?) {
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
