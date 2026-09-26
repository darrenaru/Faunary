package com.faunary.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.PinDrop
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.faunary.app.remote.CommunityRepository
import com.faunary.app.remote.CommunitySighting
import com.faunary.app.ui.components.ButtonKind
import com.faunary.app.ui.components.DetectionPhoto
import com.faunary.app.ui.components.EmptyState
import com.faunary.app.ui.components.FaunaryButton
import com.faunary.app.ui.components.FaunaryCard
import com.faunary.app.ui.components.IconBadge
import com.faunary.app.ui.components.Pill
import com.faunary.app.ui.components.SurfaceIconButton
import com.faunary.app.ui.components.icon
import com.faunary.app.ui.components.softShadow
import com.faunary.app.ui.navigation.CommunityDetailRoute
import com.faunary.app.ui.theme.FaunaryTheme
import com.faunary.app.util.Format
import com.faunary.app.util.openDirections
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CommunityDetailState {
    data object Loading : CommunityDetailState
    data object Missing : CommunityDetailState
    data class Ready(val sighting: CommunitySighting) : CommunityDetailState
}

@HiltViewModel
class CommunityDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: CommunityRepository,
) : ViewModel() {
    private val id = savedStateHandle.toRoute<CommunityDetailRoute>().id
    private val _state = MutableStateFlow<CommunityDetailState>(CommunityDetailState.Loading)
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = repository.sighting(id)?.let { CommunityDetailState.Ready(it) } ?: CommunityDetailState.Missing
        }
    }
}

/** Read-only view of someone else's sighting. */
@Composable
fun CommunityDetailScreen(onBack: () -> Unit, viewModel: CommunityDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = FaunaryTheme.colors
    Box(Modifier.fillMaxSize().background(c.background)) {
        when (val s = state) {
            CommunityDetailState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = c.primary)
            CommunityDetailState.Missing -> Column(Modifier.align(Alignment.Center)) {
                EmptyState(Icons.Rounded.CloudOff, "Temuan tidak tersedia", "Mungkin sudah dihapus pemiliknya, atau kamu sedang offline.")
                FaunaryButton("Kembali", onBack, Modifier.align(Alignment.CenterHorizontally), kind = ButtonKind.Ghost)
            }
            is CommunityDetailState.Ready -> Content(s.sighting, onBack)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Content(s: CommunitySighting, onBack: () -> Unit) {
    val c = FaunaryTheme.colors
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SurfaceIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali", onBack)
            Spacer(Modifier.width(12.dp))
            Text("Temuan komunitas", style = MaterialTheme.typography.titleLarge, color = c.foreground)
        }
        Spacer(Modifier.height(8.dp))
        DetectionPhoto(
            photoPath = s.photoUrl,
            aspectRatio = s.aspectRatio,
            detections = s.detections,
            modifier = Modifier.softShadow(RoundedCornerShape(22.dp), 8.dp),
        )
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.animalLabel, style = MaterialTheme.typography.displaySmall, color = c.foreground)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(c.badgeInfo), contentAlignment = Alignment.Center) {
                        Text(s.displayName.take(1).uppercase(), style = MaterialTheme.typography.labelSmall, color = c.foreground)
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("Ditemukan oleh ${s.displayName}", style = MaterialTheme.typography.bodyMedium, color = c.foregroundSecondary)
                }
            }
            IconBadge(s.animalCategory.icon, background = c.badgeInfo, size = 48.dp)
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.isAiDetected) Pill("Akurasi AI ${Format.percent(s.confidence)}", icon = Icons.Rounded.Verified)
            Pill(s.animalCategory.displayName, leading = s.animalCategory.emoji, color = c.badgeNature)
        }
        Spacer(Modifier.height(16.dp))

        FaunaryCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Schedule, null, Modifier.size(18.dp), tint = c.brand)
                Spacer(Modifier.width(8.dp))
                Text("Waktu", style = MaterialTheme.typography.labelLarge, color = c.foregroundSecondary)
            }
            Spacer(Modifier.height(8.dp))
            Text(Format.fullDate(s.takenAtMs).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium, color = c.foreground)
            Text("Pukul ${Format.time(s.takenAtMs)} · ${Format.relative(s.takenAtMs)}", style = MaterialTheme.typography.bodyMedium, color = c.foregroundSecondary)
        }
        Spacer(Modifier.height(12.dp))

        FaunaryCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.PinDrop, null, Modifier.size(18.dp), tint = c.brand)
                Spacer(Modifier.width(8.dp))
                Text("Lokasi", style = MaterialTheme.typography.labelLarge, color = c.foregroundSecondary)
            }
            Spacer(Modifier.height(8.dp))
            Text(s.locationName ?: "Nama lokasi tidak tersedia", style = MaterialTheme.typography.titleMedium, color = c.foreground)
            Text(Format.coordinates(s.latitude, s.longitude), style = MaterialTheme.typography.labelLarge, color = c.brand)
            Spacer(Modifier.height(12.dp))
            FaunaryButton(
                "Rute ke Sini", { context.openDirections(s.latitude, s.longitude, s.animalLabel) },
                Modifier.fillMaxWidth(), icon = Icons.Rounded.Directions, height = 44.dp,
            )
        }

        s.note?.let { note ->
            Spacer(Modifier.height(12.dp))
            FaunaryCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Rounded.Notes, null, Modifier.size(18.dp), tint = c.brand)
                    Spacer(Modifier.width(8.dp))
                    Text("Catatan penemu", style = MaterialTheme.typography.labelLarge, color = c.foregroundSecondary)
                }
                Spacer(Modifier.height(8.dp))
                Text("“$note”", style = MaterialTheme.typography.bodyLarge, color = c.foreground)
            }
        }
    }
}
