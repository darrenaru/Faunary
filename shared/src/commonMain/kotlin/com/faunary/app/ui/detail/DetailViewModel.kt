package com.faunary.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.faunary.app.data.AnimalSighting
import com.faunary.app.data.SightingRepository
import com.faunary.app.domain.AnimalCategory
import com.faunary.app.remote.SocialRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data object Missing : DetailUiState
    data class Ready(val sighting: AnimalSighting) : DetailUiState
}

/** [id] is the local id of the sighting (DetailRoute.id). */
class DetailViewModel(
    private val id: Long,
    private val repository: SightingRepository,
    socialRepository: SocialRepository,
) : ViewModel() {

    val state: StateFlow<DetailUiState> = repository.observe(id)
        .map { if (it == null) DetailUiState.Missing else DetailUiState.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState.Loading)

    /** Likes and comments; the owner may also remove other people's comments on their own find. */
    val social = SightingSocial(socialRepository, viewModelScope)

    init {
        viewModelScope.launch {
            // Bound once the entry has a server id (i.e. after its first upload).
            repository.observe(id).collect { social.bind(it?.remoteId, moderator = true) }
        }
    }

    fun updateLabel(label: String, category: AnimalCategory) = viewModelScope.launch {
        repository.updateLabel(id, label.trim(), category.name)
    }

    fun updateNote(note: String) = viewModelScope.launch { repository.updateNote(id, note) }

    fun toggleFavorite() = viewModelScope.launch { repository.toggleFavorite(id) }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        repository.delete(id)
        onDone()
    }
}
