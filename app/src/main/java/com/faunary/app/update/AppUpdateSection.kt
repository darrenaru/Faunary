package com.faunary.app.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.faunary.app.BuildConfig
import com.faunary.app.ui.components.ButtonKind
import com.faunary.app.ui.components.FaunaryButton
import com.faunary.app.ui.components.FaunaryCard
import com.faunary.app.ui.profile.SettingTitle
import com.faunary.app.ui.theme.FaunaryTheme
import org.koin.compose.viewmodel.koinViewModel

/** The self-updater's card on the profile screen. */
@Composable
fun AppUpdateSection(updateViewModel: UpdateViewModel = koinViewModel()) {
    val update by updateViewModel.state.collectAsStateWithLifecycle()
    val installUpdate = rememberInstallAction(updateViewModel)
    val c = FaunaryTheme.colors
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
}
