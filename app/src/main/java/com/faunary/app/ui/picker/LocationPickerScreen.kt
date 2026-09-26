package com.faunary.app.ui.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.faunary.app.location.GeoPoint
import com.faunary.app.location.LocationRepository
import com.faunary.app.ui.components.FaunaryButton
import com.faunary.app.ui.components.FaunaryCard
import com.faunary.app.ui.components.LocateButton
import com.faunary.app.ui.components.SurfaceIconButton
import com.faunary.app.ui.components.softShadow
import com.faunary.app.ui.map.DefaultCenter
import com.faunary.app.ui.map.FaunaMap
import com.faunary.app.ui.map.rememberFaunaMapController
import com.faunary.app.ui.theme.FaunaryTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocationPickerViewModel @Inject constructor(private val location: LocationRepository) : ViewModel() {
    val lastFix: StateFlow<GeoPoint?> = location.lastFix
    fun hasPermission() = location.hasPermission()
    fun locate(onFix: (GeoPoint) -> Unit) = viewModelScope.launch { location.currentLocation()?.let(onFix) }
}

@Composable
fun LocationPickerScreen(
    start: Pair<Double, Double>?,
    onBack: () -> Unit,
    onPicked: (Double, Double) -> Unit,
    viewModel: LocationPickerViewModel = hiltViewModel(),
) {
    val c = FaunaryTheme.colors
    val controller = rememberFaunaMapController()
    val fix by viewModel.lastFix.collectAsStateWithLifecycle()
    val initial = start?.let { GeoPoint(it.first, it.second) } ?: fix ?: DefaultCenter

    Box(Modifier.fillMaxSize().background(c.background)) {
        FaunaMap(
            markers = emptyList(),
            controller = controller,
            initialCenter = initial,
            initialZoom = 16.0,
            showUserLocation = viewModel.hasPermission(),
            ornamentBottomPadding = 170.dp,
            darkTheme = c.isDark,
            modifier = Modifier.fillMaxSize(),
        )

        // Fixed centre pin: the map moves underneath it.
        Column(Modifier.align(Alignment.Center).offset(y = (-28).dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.softShadow(CircleShape, 8.dp).size(44.dp).clip(CircleShape).background(c.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.LocationOn, null, Modifier.size(26.dp), tint = c.onPrimary)
            }
            Box(Modifier.size(width = 3.dp, height = 12.dp).background(c.primary))
            Box(Modifier.size(8.dp).clip(CircleShape).background(c.brand.copy(alpha = 0.4f)))
        }

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SurfaceIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali", onBack)
            Spacer(Modifier.weight(1f))
            if (viewModel.hasPermission()) {
                LocateButton({ viewModel.locate { controller.flyTo(it.latitude, it.longitude, 17.0) } })
            }
        }

        FaunaryCard(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp),
        ) {
            Text("Tandai lokasi temuan", style = MaterialTheme.typography.titleMedium, color = c.foreground)
            Spacer(Modifier.height(4.dp))
            Text(
                "Geser peta sampai pin berada tepat di tempat kamu bertemu satwa ini.",
                style = MaterialTheme.typography.bodyMedium, color = c.foregroundSecondary,
            )
            Spacer(Modifier.height(14.dp))
            FaunaryButton(
                "Pilih Titik Ini",
                onClick = {
                    val center = controller.center() ?: initial
                    onPicked(center.latitude, center.longitude)
                },
                icon = Icons.Rounded.Check,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
