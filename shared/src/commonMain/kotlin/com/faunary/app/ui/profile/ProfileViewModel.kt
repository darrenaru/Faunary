package com.faunary.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.faunary.app.data.AnimalSighting
import com.faunary.app.data.Settings
import com.faunary.app.data.SettingsRepository
import com.faunary.app.data.SightingRepository
import com.faunary.app.data.ThemeMode
import com.faunary.app.domain.CollectionStats
import com.faunary.app.remote.SupabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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

class ProfileViewModel(
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
    fun setName(name: String) {
        settingsRepo.setExplorerName(name)
        viewModelScope.launch { supabase.syncProfile() }
    }

    fun setShareLive(enabled: Boolean) = settingsRepo.setShareLiveLocation(enabled)

    /** The whole collection as pretty-printed JSON (photos are referenced by file name). */
    suspend fun exportJson(): String = withContext(Dispatchers.Default) {
        ExportJson.encodeToString(repository.getAll().map { it.toExport() })
    }

    private fun AnimalSighting.toExport() = ExportEntry(
        id, animalLabel, category, confidence, aiLabel, latitude, longitude,
        locationName, address, note, isFavorite, timestamp, photoPath.substringAfterLast('/'),
    )
}
