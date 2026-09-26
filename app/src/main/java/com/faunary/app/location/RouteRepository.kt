package com.faunary.app.location

import android.content.Context
import com.faunary.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class TravelMode(val label: String, val profile: String) {
    WALKING("Jalan kaki", "walking"),
    DRIVING("Berkendara", "driving"),
}

data class Route(
    /** (latitude, longitude) along the route, origin first. */
    val points: List<Pair<Double, Double>>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val mode: TravelMode,
)

@Serializable
private data class DirectionsResponse(val code: String, val routes: List<DirectionsRoute> = emptyList())

@Serializable
private data class DirectionsRoute(val distance: Double, val duration: Double, val geometry: Geometry)

@Serializable
private data class Geometry(val coordinates: List<List<Double>>)

/** Turn-by-turn free routes drawn inside the app, via the Mapbox Directions API (same public token as the map). */
@Singleton
class RouteRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun route(from: GeoPoint, toLat: Double, toLng: Double, mode: TravelMode): Result<Route> = withContext(Dispatchers.IO) {
        runCatching {
            val token = context.getString(R.string.mapbox_access_token)
            val coords = String.format(Locale.US, "%.6f,%.6f;%.6f,%.6f", from.longitude, from.latitude, toLng, toLat)
            val url = URL(
                "https://api.mapbox.com/directions/v5/mapbox/${mode.profile}/$coords" +
                    "?geometries=geojson&overview=full&alternatives=false&access_token=$token",
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
            }
            val body = try {
                if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
            val parsed = json.decodeFromString<DirectionsResponse>(body)
            val r = parsed.routes.firstOrNull() ?: error("Rute tidak ditemukan (${parsed.code})")
            Route(
                points = r.geometry.coordinates.map { it[1] to it[0] },
                distanceMeters = r.distance,
                durationSeconds = r.duration,
                mode = mode,
            )
        }
    }
}
