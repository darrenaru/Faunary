package com.faunary.app

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.faunary.app.data.SettingsRepository
import com.faunary.app.data.ThemeMode
import com.faunary.app.notify.InteractionNotifier
import com.faunary.app.ui.navigation.FaunaryNavHost
import com.faunary.app.ui.navigation.NotificationOpen
import com.faunary.app.ui.theme.FaunaryTheme
import com.faunary.app.update.MandatoryUpdateScreen
import com.faunary.app.update.UpdateRepository
import com.faunary.app.update.UpdateScheduler
import com.faunary.app.util.hasPermission
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val settingsRepository: SettingsRepository by inject()
    private val updateRepository: UpdateRepository by inject()
    private val updateScheduler: UpdateScheduler by inject()

    /** Set when a notification was tapped; the nav host opens it and clears it. */
    private val notificationOpen = MutableStateFlow<NotificationOpen?>(null)

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleNotificationIntent(intent)
        askNotificationPermissionOnce()
        enableEdgeToEdge()
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle()
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            FaunaryTheme(darkTheme = dark) {
                // A required update replaces the whole app until it is installed.
                val update by updateRepository.state.collectAsStateWithLifecycle()
                if (update.mandatory) MandatoryUpdateScreen()
                else FaunaryNavHost(notificationOpen = notificationOpen)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Returning to the app re-checks (at most every 30 min), so a required update is enforced
        // without waiting for a cold start or the 12-hour periodic check.
        if (System.currentTimeMillis() - updateRepository.state.value.lastChecked > 30 * 60_000L) updateScheduler.checkNow()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        notificationOpen.value = when (intent?.action) {
            InteractionNotifier.ACTION_OPEN_SIGHTING -> intent.getStringExtra(InteractionNotifier.EXTRA_SIGHTING_ID)?.let {
                NotificationOpen.Sighting(it, intent.getStringExtra(InteractionNotifier.EXTRA_NOTIFICATION_ID))
            }
            InteractionNotifier.ACTION_OPEN_INBOX -> NotificationOpen.Inbox
            else -> return
        }
    }

    /** Android 13+: ask once (on first launch) so like/comment notifications can be shown. */
    private fun askNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (hasPermission(Manifest.permission.POST_NOTIFICATIONS)) return
        val prefs = getSharedPreferences("faunary_notifications", MODE_PRIVATE)
        if (prefs.getBoolean("permission_asked", false)) return
        prefs.edit { putBoolean("permission_asked", true) }
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
