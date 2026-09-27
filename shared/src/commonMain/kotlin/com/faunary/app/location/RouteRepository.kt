package com.faunary.app.location

import com.faunary.app.FaunaryConfig
import com.faunary.app.util.Format
import com.faunary.app.util.getText
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class TravelMode(val label: String, val profile: String) {
    WALKING("Jalan kaki", "walking"),
    DRIVING("Berkendara", "driving"),
}

/** One manoeuvre of a route; the manoeuvre happens at ([latitude], [longitude]), then the step runs [distanceMeters]. */
data class RouteStep(
    /** Spoken/visible instruction in Indonesian, e.g. "Belok kiri ke Jalan Diponegoro". */
    val instruction: String,
    /** Mapbox manoeuvre type: depart, turn, arrive, roundabout, fork, merge, … */
    val type: String,
    /** Mapbox manoeuvre modifier: left, slight right, uturn, straight, … */
    val modifier: String?,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Double,
    val durationSeconds: Double,
)

data class Route(
    /** (latitude, longitude) along the route, origin first. */
    val points: List<Pair<Double, Double>>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val mode: TravelMode,
    val steps: List<RouteStep> = emptyList(),
)

@Serializable
private data class DirectionsResponse(val code: String, val routes: List<DirectionsRoute> = emptyList())

@Serializable
private data class DirectionsRoute(
    val distance: Double,
    val duration: Double,
    val geometry: Geometry,
    val legs: List<Leg> = emptyList(),
)

@Serializable
private data class Leg(val steps: List<Step> = emptyList())

@Serializable
private data class Step(val distance: Double, val duration: Double, val maneuver: Maneuver)

@Serializable
private data class Maneuver(
    val type: String,
    val modifier: String? = null,
    val instruction: String = "",
    val location: List<Double>,
)

@Serializable
private data class Geometry(val coordinates: List<List<Double>>)

/** Turn-by-turn free routes drawn inside the app, via the Mapbox Directions API (same public token as the map). */
class RouteRepository(private val http: HttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun route(from: GeoPoint, toLat: Double, toLng: Double, mode: TravelMode): Result<Route> = withContext(Dispatchers.IO) {
        runCatching {
            val token = FaunaryConfig.MAPBOX_ACCESS_TOKEN
            val coords = "${lngLat(from.longitude, from.latitude)};${lngLat(toLng, toLat)}"
            val body = http.getText(
                "https://api.mapbox.com/directions/v5/mapbox/${mode.profile}/$coords" +
                    "?geometries=geojson&overview=full&alternatives=false&steps=true&language=id&access_token=$token",
            )
            val parsed = json.decodeFromString<DirectionsResponse>(body)
            val r = parsed.routes.firstOrNull() ?: error("Rute tidak ditemukan (${parsed.code})")
            Route(
                points = r.geometry.coordinates.map { it[1] to it[0] },
                distanceMeters = r.distance,
                durationSeconds = r.duration,
                mode = mode,
                steps = r.legs.flatMap { it.steps }.map { st ->
                    RouteStep(
                        instruction = st.maneuver.instruction,
                        type = st.maneuver.type,
                        modifier = st.maneuver.modifier,
                        latitude = st.maneuver.location[1],
                        longitude = st.maneuver.location[0],
                        distanceMeters = st.distance,
                        durationSeconds = st.duration,
                    )
                },
            )
        }
    }
}

private fun lngLat(lng: Double, lat: Double) = "${Format.decimal(lng, 6)},${Format.decimal(lat, 6)}"
