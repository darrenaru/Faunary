package com.faunary.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.faunary.app.remote.CommunityRepository
import com.faunary.app.remote.CommunitySighting
import com.faunary.app.remote.SocialRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CommunityDetailState {
    data object Loading : CommunityDetailState
    data object Missing : CommunityDetailState
    data class Ready(val sighting: CommunitySighting) : CommunityDetailState
}

/** [id] is the server id of someone else's sighting (CommunityDetailRoute.id). */
class CommunityDetailViewModel(
    private val id: String,
    repository: CommunityRepository,
    socialRepository: SocialRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<CommunityDetailState>(CommunityDetailState.Loading)
    val state = _state.asStateFlow()
    val social = SightingSocial(socialRepository, viewModelScope)

    init {
        viewModelScope.launch {
            val sighting = repository.sighting(id)
            _state.value = sighting?.let { CommunityDetailState.Ready(it) } ?: CommunityDetailState.Missing
            if (sighting != null) social.bind(sighting.id, moderator = false)
        }
    }
}
