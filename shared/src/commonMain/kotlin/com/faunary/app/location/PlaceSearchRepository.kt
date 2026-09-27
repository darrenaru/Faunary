package com.faunary.app.location

import com.faunary.app.FaunaryConfig
import com.faunary.app.util.Format
import com.faunary.app.util.getText
import com.faunary.app.util.randomUuid
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One autocomplete row: no coordinates yet, [PlaceSearchRepository.retrieve] resolves it. */
data class PlaceSuggestion(
    val id: String,
    val name: String,
    /** Address or the area it's in, e.g. "Jl. Ganesha 10, Bandung, Jawa Barat". */
    val address: String?,
    val type: PlaceType,
    /** Category of a point of interest, e.g. "Taman", "Kafe". */
    val category: String?,
    val distanceMeters: Double?,
)

/** A place picked from search, shown on the map with its own marker and card. */
@Serializable
data class SearchedPlace(
    val id: String,
    val name: String,
    val address: String? = null,
    val type: PlaceType = PlaceType.PLACE,
    val category: String? = null,
    val latitude: Double,
    val longitude: Double,
    /** Extent of a city/region (south, west, north, east), framed instead of zooming to one spot. */
    val bbox: List<Double>? = null,
)

@Serializable
enum class PlaceType(val label: String, val zoom: Double) {
    POI("Tempat", 17.0),
    ADDRESS("Alamat", 17.5),
    STREET("Jalan", 16.0),
    AREA("Kawasan", 14.0),
    PLACE("Kota", 12.0),
    REGION("Wilayah", 8.0),
    COORDINATES("Koordinat", 17.0);

    companion object {
        fun fromFeatureType(type: String?): PlaceType = when (type) {
            "poi" -> POI
            "address" -> ADDRESS
            "street" -> STREET
            "neighborhood", "locality", "district", "postcode", "block", "chome", "oaza" -> AREA
            "place", "city" -> PLACE
            "region", "prefecture", "country" -> REGION
            else -> POI
        }
    }
}

@Serializable
private data class SuggestResponse(val suggestions: List<SuggestItem> = emptyList())

@Serializable
private data class SuggestItem(
    val name: String,
    @SerialName("mapbox_id") val mapboxId: String,
    @SerialName("feature_type") val featureType: String? = null,
    @SerialName("full_address") val fullAddress: String? = null,
    @SerialName("place_formatted") val placeFormatted: String? = null,
    val address: String? = null,
    @SerialName("poi_category") val poiCategory: List<String> = emptyList(),
    val distance: Double? = null,
)

@Serializable
private data class RetrieveResponse(val features: List<RetrieveFeature> = emptyList())

@Serializable
private data class RetrieveFeature(val geometry: PointGeometry, val properties: RetrieveProperties)

@Serializable
private data class PointGeometry(val coordinates: List<Double>)

@Serializable
private data class RetrieveProperties(
    val name: String,
    @SerialName("mapbox_id") val mapboxId: String,
    @SerialName("feature_type") val featureType: String? = null,
    @SerialName("full_address") val fullAddress: String? = null,
    @SerialName("place_formatted") val placeFormatted: String? = null,
    @SerialName("poi_category") val poiCategory: List<String> = emptyList(),
    /** [west, south, east, north] */
    val bbox: List<Double>? = null,
)

/**
 * Search on the map: places, POIs, streets and addresses anywhere, via the Mapbox Search Box API
 * (same public token as the map). Typing uses suggest (cheap, no coordinates); picking a row
 * retrieves it, which ends the billing session. Picked places are kept as recent searches.
 */
class PlaceSearchRepository(
    private val http: HttpClient,
    /** Its own store ("faunary_search" preferences on Android), holding the recent searches. */
    private val prefs: Settings,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var session = randomUuid()

    private val _recent = MutableStateFlow(readRecent())
    val recent: StateFlow<List<SearchedPlace>> = _recent.asStateFlow()

    /** Results are biased towards [near] (what the map shows); distances are measured from [origin] (the user). */
    suspend fun suggest(query: String, near: GeoPoint?, origin: GeoPoint?): Result<List<PlaceSuggestion>> = withContext(Dispatchers.IO) {
        runCatching {
            val proximity = near?.let { "&proximity=${lngLat(it)}" } ?: ""
            val from = origin?.let { "&origin=${lngLat(it)}" } ?: ""
            val body = http.getText(
                "https://api.mapbox.com/search/searchbox/v1/suggest?q=${encode(query)}&language=id&limit=10" +
                    "$proximity$from&session_token=$session&access_token=${token()}",
            )
            json.decodeFromString<SuggestResponse>(body).suggestions
                // Category/brand rows ("Kafe", "Starbucks") are lists of places, not a place with coordinates.
                .filter { it.featureType != "category" && it.featureType != "brand" }
                .map {
                    PlaceSuggestion(
                        id = it.mapboxId,
                        name = it.name,
                        address = (it.fullAddress ?: it.placeFormatted)?.takeIf { a -> a.isNotBlank() && a != it.name },
                        type = PlaceType.fromFeatureType(it.featureType),
                        category = it.poiCategory.firstOrNull()?.replaceFirstChar { c -> c.titlecase() },
                        distanceMeters = it.distance,
                    )
                }
        }
    }

    suspend fun retrieve(suggestion: PlaceSuggestion): Result<SearchedPlace> = withContext(Dispatchers.IO) {
        runCatching {
            val body = http.getText(
                "https://api.mapbox.com/search/searchbox/v1/retrieve/${encode(suggestion.id)}" +
                    "?language=id&session_token=$session&access_token=${token()}",
            )
            session = randomUuid()
            val f = json.decodeFromString<RetrieveResponse>(body).features.firstOrNull() ?: error("Tempat tidak ditemukan")
            val p = f.properties
            SearchedPlace(
                id = p.mapboxId,
                name = p.name,
                address = (p.fullAddress ?: p.placeFormatted)?.takeIf { it.isNotBlank() && it != p.name } ?: suggestion.address,
                type = p.featureType?.let(PlaceType::fromFeatureType) ?: suggestion.type,
                category = p.poiCategory.firstOrNull()?.replaceFirstChar { it.titlecase() } ?: suggestion.category,
                latitude = f.geometry.coordinates[1],
                longitude = f.geometry.coordinates[0],
                bbox = p.bbox?.takeIf { it.size == 4 }?.let { (w, s, e, n) -> listOf(s, w, n, e) },
            ).also(::remember)
        }
    }

    /** Puts [place] at the top of the recent searches. */
    fun remember(place: SearchedPlace) {
        val list = (listOf(place) + _recent.value.filterNot { it.id == place.id }).take(MAX_RECENT)
        _recent.value = list
        prefs.putString(KEY_RECENT, json.encodeToString(list))
    }

    fun clearRecent() {
        _recent.value = emptyList()
        prefs.remove(KEY_RECENT)
    }

    private fun readRecent(): List<SearchedPlace> =
        runCatching { json.decodeFromString<List<SearchedPlace>>(prefs.getStringOrNull(KEY_RECENT) ?: "[]") }.getOrDefault(emptyList())

    private fun token() = FaunaryConfig.MAPBOX_ACCESS_TOKEN

    private fun encode(s: String) = s.encodeURLParameter()

    private fun lngLat(p: GeoPoint) = "${Format.decimal(p.longitude, 6)},${Format.decimal(p.latitude, 6)}"

    private companion object {
        const val KEY_RECENT = "recent_places"
        const val MAX_RECENT = 8
    }
}
