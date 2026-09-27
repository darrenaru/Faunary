package com.faunary.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.russhwolf.settings.Settings as KeyValueStore

enum class ThemeMode(val displayName: String) { SYSTEM("Ikuti sistem"), LIGHT("Terang"), DARK("Gelap") }

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val explorerName: String = "Penjelajah",
    /** Terrain + extruded buildings + tilted camera. Off by default: it costs more battery/data. */
    val map3D: Boolean = false,
    /** Opt-in: publish position to other users while the app is open. */
    val shareLiveLocation: Boolean = false,
    /** Whether the "your sightings are public" notice was acknowledged. */
    val publicNoticeSeen: Boolean = false,
)

/** [prefs] is the "faunary_settings" SharedPreferences file on Android and NSUserDefaults on iOS. */
class SettingsRepository(private val prefs: KeyValueStore) {

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private fun read() = Settings(
        themeMode = runCatching { ThemeMode.valueOf(prefs.getStringOrNull(KEY_THEME) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM),
        explorerName = prefs.getStringOrNull(KEY_NAME) ?: "Penjelajah",
        map3D = prefs.getBoolean(KEY_MAP_3D, false),
        shareLiveLocation = prefs.getBoolean(KEY_SHARE_LIVE, false),
        publicNoticeSeen = prefs.getBoolean(KEY_PUBLIC_NOTICE, false),
    )

    fun setThemeMode(mode: ThemeMode) {
        prefs.putString(KEY_THEME, mode.name)
        _settings.value = read()
    }

    fun setExplorerName(name: String) {
        prefs.putString(KEY_NAME, name.trim().ifEmpty { "Penjelajah" })
        _settings.value = read()
    }

    fun setMap3D(enabled: Boolean) {
        prefs.putBoolean(KEY_MAP_3D, enabled)
        _settings.value = read()
    }

    fun setShareLiveLocation(enabled: Boolean) {
        prefs.putBoolean(KEY_SHARE_LIVE, enabled)
        _settings.value = read()
    }

    fun markPublicNoticeSeen() {
        prefs.putBoolean(KEY_PUBLIC_NOTICE, true)
        _settings.value = read()
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_NAME = "explorer_name"
        const val KEY_MAP_3D = "map_3d"
        const val KEY_SHARE_LIVE = "share_live_location"
        const val KEY_PUBLIC_NOTICE = "public_notice_seen"
    }
}
