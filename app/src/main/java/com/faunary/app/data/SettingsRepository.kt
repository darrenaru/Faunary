package com.faunary.app.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode(val displayName: String) { SYSTEM("Ikuti sistem"), LIGHT("Terang"), DARK("Gelap") }

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Detections below this confidence are discarded (0..1). */
    val minConfidence: Float = 0.5f,
    val explorerName: String = "Penjelajah",
    /** Terrain + extruded buildings + tilted camera. Off by default: it costs more battery/data. */
    val map3D: Boolean = false,
    /** Opt-in: publish position to other users while the app is open. */
    val shareLiveLocation: Boolean = false,
    /** Whether the "your sightings are public" notice was acknowledged. */
    val publicNoticeSeen: Boolean = false,
)

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("faunary_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private fun read() = Settings(
        themeMode = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM),
        minConfidence = prefs.getFloat(KEY_MIN_CONFIDENCE, 0.5f),
        explorerName = prefs.getString(KEY_NAME, null) ?: "Penjelajah",
        map3D = prefs.getBoolean(KEY_MAP_3D, false),
        shareLiveLocation = prefs.getBoolean(KEY_SHARE_LIVE, false),
        publicNoticeSeen = prefs.getBoolean(KEY_PUBLIC_NOTICE, false),
    )

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME, mode.name) }
        _settings.value = read()
    }

    fun setMinConfidence(value: Float) {
        prefs.edit { putFloat(KEY_MIN_CONFIDENCE, value) }
        _settings.value = read()
    }

    fun setExplorerName(name: String) {
        prefs.edit { putString(KEY_NAME, name.trim().ifEmpty { "Penjelajah" }) }
        _settings.value = read()
    }

    fun setMap3D(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_MAP_3D, enabled) }
        _settings.value = read()
    }

    fun setShareLiveLocation(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SHARE_LIVE, enabled) }
        _settings.value = read()
    }

    fun markPublicNoticeSeen() {
        prefs.edit { putBoolean(KEY_PUBLIC_NOTICE, true) }
        _settings.value = read()
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_MIN_CONFIDENCE = "min_confidence"
        const val KEY_NAME = "explorer_name"
        const val KEY_MAP_3D = "map_3d"
        const val KEY_SHARE_LIVE = "share_live_location"
        const val KEY_PUBLIC_NOTICE = "public_notice_seen"
    }
}
