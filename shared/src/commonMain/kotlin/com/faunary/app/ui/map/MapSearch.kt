package com.faunary.app.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HolidayVillage
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Signpost
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.faunary.app.domain.AnimalCategory
import com.faunary.app.location.GeoPoint
import com.faunary.app.location.PlaceSuggestion
import com.faunary.app.location.PlaceType
import com.faunary.app.location.SearchedPlace
import com.faunary.app.ui.components.ButtonKind
import com.faunary.app.ui.components.FaunaryButton
import com.faunary.app.ui.components.FaunaryCard
import com.faunary.app.ui.components.FaunaryIcons
import com.faunary.app.ui.components.PhotoThumb
import com.faunary.app.ui.components.Pill
import com.faunary.app.ui.components.SurfaceIconButton
import com.faunary.app.ui.components.softShadow
import com.faunary.app.ui.platform.rememberCopyText
import com.faunary.app.ui.theme.FaunaryTheme
import com.faunary.app.util.Format
import com.faunary.app.util.Geo

/** Red of the searched-place marker, echoed in its card and the result icons. */
private val PlaceRed = Color(0xFFD93A34)

val PlaceType.icon: ImageVector
    get() = when (this) {
        PlaceType.POI -> Icons.Rounded.Place
        PlaceType.ADDRESS -> Icons.Rounded.Home
        PlaceType.STREET -> Icons.Rounded.Signpost
        PlaceType.AREA -> Icons.Rounded.HolidayVillage
        PlaceType.PLACE -> Icons.Rounded.LocationCity
        PlaceType.REGION -> Icons.Rounded.Map
        PlaceType.COORDINATES -> FaunaryIcons.Crosshair
    }

fun SearchedPlace.toMarker() = MapMarker(PLACE_KEY, latitude, longitude, null, AnimalCategory.OTHER, MarkerKind.PLACE, name)

/**
 * Full-screen search over the map, like the search of a maps app: one field for places, POIs,
 * streets, addresses and coordinates (Mapbox) plus finds, shared markers and explorers already on
 * the map. With nothing typed it lists recent searches.
 */
@Composable
fun MapSearchOverlay(
    search: SearchUi,
    recent: List<SearchedPlace>,
    here: GeoPoint?,
    onQuery: (String) -> Unit,
    onPickPlace: (PlaceSuggestion) -> Unit,
    onPickRecent: (SearchedPlace) -> Unit,
    onPickCoordinates: (GeoPoint) -> Unit,
    onPickOnMap: (MapSelection) -> Unit,
    onClearRecent: () -> Unit,
    onClose: () -> Unit,
) {
    val c = FaunaryTheme.colors
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    val text = search.query.trim()

    // "Search" on the keyboard opens the best match: coordinates, else the first place, else the first find.
    val submit = {
        keyboard?.hide()
        when {
            search.coordinates != null -> onPickCoordinates(search.coordinates)
            search.places.isNotEmpty() -> onPickPlace(search.places.first())
            search.onMap.isNotEmpty() -> onPickOnMap(search.onMap.first())
        }
    }

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SurfaceIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Tutup pencarian", onClose, background = Color.Transparent)
            Spacer(Modifier.width(4.dp))
            val shape = RoundedCornerShape(24.dp)
            BasicTextField(
                value = search.query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.foreground),
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                modifier = Modifier.weight(1f).focusRequester(focus),
                decorationBox = { inner ->
                    Row(
                        Modifier.softShadow(shape, 4.dp).clip(shape).background(c.surface)
                            .border(1.5.dp, c.primary, shape)
                            .height(50.dp).padding(start = 14.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Search, null, Modifier.size(22.dp), tint = c.foregroundMuted)
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.weight(1f)) {
                            if (search.query.isEmpty()) {
                                Text(
                                    "Cari tempat, alamat, jalan, satwa…", style = MaterialTheme.typography.bodyLarge,
                                    color = c.foregroundMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            inner()
                        }
                        if (search.loading) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = c.primary, strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        if (search.query.isNotEmpty()) {
                            Box(
                                Modifier.size(38.dp).clip(CircleShape).clickable(onClickLabel = "Hapus teks") { onQuery("") },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.Close, "Hapus teks", Modifier.size(20.dp), tint = c.foregroundSecondary)
                            }
                        }
                    }
                },
            )
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        ) {
            if (text.isEmpty()) {
                if (recent.isNotEmpty()) {
                    item("recentHeader") { SectionTitle("Pencarian terakhir", action = "Hapus semua", onAction = onClearRecent) }
                    items(recent, key = { "recent:" + it.id }) { p ->
                        ResultRow(
                            leading = { ResultIcon(Icons.Rounded.History, c.surfaceMuted, c.foregroundSecondary) },
                            title = p.name,
                            subtitle = p.address ?: p.type.label,
                            trailing = distanceTo(here, p.latitude, p.longitude),
                            onClick = { onPickRecent(p) },
                        )
                    }
                } else {
                    item("hint") { SearchHint() }
                }
                return@LazyColumn
            }

            search.coordinates?.let { point ->
                item("coords") {
                    ResultRow(
                        leading = { ResultIcon(FaunaryIcons.Crosshair, PlaceRed.copy(alpha = 0.12f), PlaceRed) },
                        title = Format.coordinates(point.latitude, point.longitude),
                        subtitle = "Buka titik koordinat ini di peta",
                        trailing = distanceTo(here, point.latitude, point.longitude),
                        onClick = { onPickCoordinates(point) },
                    )
                }
            }

            if (search.onMap.isNotEmpty()) {
                item("onMapHeader") { SectionTitle("Di peta Faunary") }
                items(search.onMap, key = { "map:" + it.key }) { sel ->
                    val (lat, lng) = sel.latLng
                    OnMapRow(sel, distanceTo(here, lat, lng)) { onPickOnMap(sel) }
                }
            }

            if (search.places.isNotEmpty()) {
                item("placesHeader") { SectionTitle("Tempat & alamat") }
                items(search.places, key = { "place:" + it.id }) { p ->
                    ResultRow(
                        leading = { ResultIcon(p.type.icon, PlaceRed.copy(alpha = 0.12f), PlaceRed) },
                        title = p.name,
                        subtitle = listOfNotNull(p.category ?: p.type.label, p.address).joinToString(" · "),
                        trailing = p.distanceMeters?.let(Format::distance),
                        onClick = { onPickPlace(p) },
                    )
                }
            }

            val nothing = search.places.isEmpty() && search.onMap.isEmpty() && search.coordinates == null
            when {
                search.error != null -> item("error") {
                    SearchMessage(Icons.Rounded.WifiOff, search.error, if (search.onMap.isEmpty()) null else "Hasil di peta tetap ditampilkan di atas.")
                }
                nothing && !search.loading && text.length >= 2 -> item("empty") {
                    SearchMessage(Icons.Rounded.SearchOff, "Tidak ada hasil untuk “$text”", "Coba kata lain, nama jalan, atau koordinat seperti -6.2, 106.8.")
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    val c = FaunaryTheme.colors
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = c.foregroundSecondary, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action, style = MaterialTheme.typography.labelLarge, color = c.primary,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onAction).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ResultIcon(icon: ImageVector, background: Color, tint: Color) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(22.dp), tint = tint)
    }
}

@Composable
private fun ResultRow(
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    trailing: String?,
    onClick: () -> Unit,
    subtitleColor: Color = FaunaryTheme.colors.foregroundSecondary,
) {
    val c = FaunaryTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = subtitleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(trailing, style = MaterialTheme.typography.labelMedium, color = c.foregroundMuted)
        }
    }
}

@Composable
private fun OnMapRow(sel: MapSelection, distance: String?, onClick: () -> Unit) {
    val c = FaunaryTheme.colors
    when (sel) {
        is MapSelection.Own -> ResultRow(
            leading = { PhotoThumb(sel.sighting.photoPath, Modifier.size(44.dp), RoundedCornerShape(14.dp), sel.sighting.animalLabel) },
            title = sel.sighting.animalLabel,
            subtitle = listOfNotNull("Temuanmu", sel.sighting.locationName).joinToString(" · "),
            trailing = distance, onClick = onClick, subtitleColor = c.primary,
        )
        is MapSelection.Community -> ResultRow(
            leading = { PhotoThumb(sel.sighting.photoUrl, Modifier.size(44.dp), RoundedCornerShape(14.dp), sel.sighting.animalLabel) },
            title = sel.sighting.animalLabel,
            subtitle = listOfNotNull("oleh ${sel.sighting.displayName}", sel.sighting.locationName).joinToString(" · "),
            trailing = distance, onClick = onClick, subtitleColor = c.info,
        )
        is MapSelection.Pin -> {
            val icon = PinIcon.fromKey(sel.pin.icon)
            ResultRow(
                leading = { ResultIcon(icon.glyph(), icon.color.copy(alpha = 0.14f), icon.color) },
                title = sel.pin.displayTitle,
                subtitle = if (sel.mine) "Penandamu" else "Penanda oleh ${sel.pin.displayName}",
                trailing = distance, onClick = onClick,
            )
        }
        is MapSelection.Live -> ResultRow(
            leading = {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(c.secondary.copy(alpha = 0.25f)).border(2.dp, c.secondary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(sel.user.name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = c.foreground)
                }
            },
            title = sel.user.name,
            subtitle = "Sedang menjelajah",
            trailing = distance, onClick = onClick, subtitleColor = c.success,
        )
    }
}

@Composable
private fun SearchHint() {
    val c = FaunaryTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = 32.dp, start = 16.dp, end = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ResultIcon(Icons.Rounded.Search, c.surfaceMuted, c.brand)
        Spacer(Modifier.height(12.dp))
        Text("Cari apa saja di peta", style = MaterialTheme.typography.titleMedium, color = c.foreground)
        Spacer(Modifier.height(4.dp))
        Text(
            "Tempat, nama jalan, alamat, kota, koordinat, juga satwa, penanda, dan penjelajah yang ada di peta.",
            style = MaterialTheme.typography.bodyMedium, color = c.foregroundSecondary, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SearchMessage(icon: ImageVector, title: String, message: String?) {
    val c = FaunaryTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = 24.dp, start = 16.dp, end = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(32.dp), tint = c.foregroundMuted)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = c.foreground, textAlign = TextAlign.Center)
        if (message != null) {
            Spacer(Modifier.height(4.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = c.foregroundSecondary, textAlign = TextAlign.Center)
        }
    }
}

private fun distanceTo(here: GeoPoint?, lat: Double, lng: Double): String? =
    here?.let { Format.distance(Geo.distanceMeters(it.latitude, it.longitude, lat, lng)) }

/** Card of the place picked in search: what and where it is, how far, and a route there. */
@Composable
fun SearchedPlaceCard(
    place: SearchedPlace,
    here: GeoPoint?,
    onRoute: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = FaunaryTheme.colors
    val copyText = rememberCopyText()
    FaunaryCard(modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            ResultIcon(place.type.icon, PlaceRed.copy(alpha = 0.12f), PlaceRed)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(place.name, style = MaterialTheme.typography.titleLarge, color = c.foreground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill(place.category ?: place.type.label, icon = place.type.icon)
                    distanceTo(here, place.latitude, place.longitude)?.let {
                        Text("$it darimu", style = MaterialTheme.typography.labelMedium, color = c.foregroundMuted)
                    }
                }
            }
            SurfaceIconButton(Icons.Rounded.Close, "Tutup", onClose, size = 40.dp, background = c.surfaceMuted)
        }
        place.address?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = c.foregroundSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FaunaryButton(
                "Salin", {
                    val text = place.address?.let { "${place.name}, $it" }
                        ?: "${place.name} (${Format.coordinates(place.latitude, place.longitude)})"
                    copyText(text, "Lokasi disalin")
                },
                Modifier.weight(1f), kind = ButtonKind.Secondary, icon = Icons.Rounded.ContentCopy, height = 46.dp,
            )
            FaunaryButton("Rute ke Sini", onRoute, Modifier.weight(1.4f), icon = Icons.Rounded.Directions, height = 46.dp)
        }
    }
}
