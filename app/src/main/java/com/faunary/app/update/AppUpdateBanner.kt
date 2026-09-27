package com.faunary.app.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/** The self-updater's banner on the map, while an update downloads or is ready to install. */
@Composable
fun AppUpdateBanner(modifier: Modifier = Modifier, updateViewModel: UpdateViewModel = koinViewModel()) {
    val update by updateViewModel.state.collectAsStateWithLifecycle()
    val updateDismissed by updateViewModel.bannerDismissed.collectAsStateWithLifecycle()
    val installUpdate = rememberInstallAction(updateViewModel)
    AnimatedVisibility(
        visible = update.hasUpdate && !updateDismissed && (update.ready || update.downloading || update.waitingForWifi),
        enter = fadeIn(tween(200)), exit = fadeOut(tween(150)),
    ) {
        UpdateCard(
            state = update,
            onInstall = installUpdate,
            onDownloadNow = updateViewModel::downloadNow,
            onDismiss = { updateViewModel.bannerDismissed.value = true },
            modifier = modifier,
        )
    }
}
