package com.faunary.app.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.faunary.app.data.AnimalSighting
import com.faunary.app.data.SightingRepository
import com.faunary.app.domain.CollectionStats
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class JournalUiState(
    val loaded: Boolean = false,
    val stats: CollectionStats = CollectionStats(),
    val recent: List<AnimalSighting> = emptyList(),
    val onThisDay: List<AnimalSighting> = emptyList(),
)

class JournalViewModel(repository: SightingRepository) : ViewModel() {
    val state: StateFlow<JournalUiState> = repository.observeAll().map { all ->
        JournalUiState(
            loaded = true,
            stats = CollectionStats.from(all),
            recent = all.take(12),
            onThisDay = CollectionStats.onThisDay(all),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalUiState())
}
