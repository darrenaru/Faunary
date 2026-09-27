package com.faunary.app.ml

import com.faunary.app.data.Detection
import com.faunary.app.domain.AnimalCategory
import com.faunary.app.remote.SupabaseProvider
import com.faunary.app.util.Log
import io.github.jan.supabase.functions.functions
import io.ktor.client.plugins.timeout
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

/**
 * Online animal identification: the photo goes to the `identify-animal` Supabase Edge Function,
 * which asks Gemini (the API key stays on the server). Much more specific than the on-device model
 * (species and breeds, named in Indonesian).
 */
class CloudAnimalDetector(
    private val supabase: SupabaseProvider,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** What the service saw, or null when it can't be reached. */
    data class Result(
        val detections: List<Detection>,
        /** No real animal, only a picture of one (screen, poster, toy, statue). */
        val depictionOnly: Boolean,
    )

    /** [jpeg] is the photo downscaled to [MAX_SIDE] px at [JPEG_QUALITY] (each platform encodes it). */
    suspend fun detect(jpeg: ByteArray): Result? {
        val client = supabase.client ?: return null
        return withTimeoutOrNull(TIMEOUT_MS) {
            runCatching {
                // Signs in anonymously if needed: the function only accepts signed-in users.
                supabase.ensureUserId() ?: return@runCatching null
                val image = Base64.encode(jpeg)
                val response = client.functions.invoke("identify-animal") {
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(IdentifyRequest(image)))
                    // Gemini can take 10+ s; the client's default request timeout is only 10 s.
                    timeout { requestTimeoutMillis = TIMEOUT_MS }
                }
                if (!response.status.isSuccess()) {
                    Log.w(TAG, "identify-animal failed: ${response.status}")
                    return@runCatching null
                }
                val body = json.decodeFromString<IdentifyResponse>(response.bodyAsText())
                Result(body.animals.map { it.toDetection() }, body.depictionOnly)
            }.onFailure { Log.w(TAG, "identify-animal unavailable", it) }.getOrNull()
        }
    }

    @Serializable
    private data class IdentifyRequest(val image: String, val mimeType: String = "image/jpeg")

    @Serializable
    private data class IdentifyResponse(val animals: List<Animal> = emptyList(), val depictionOnly: Boolean = false)

    @Serializable
    private data class Animal(
        val name: String,
        val scientificName: String = "",
        val category: String = "OTHER",
        val confidence: Float = 0f,
        /** [ymin, xmin, ymax, xmax], normalised to 0–1000. */
        val box: List<Int> = emptyList(),
    ) {
        fun toDetection(): Detection {
            val b = box.takeIf { it.size == 4 }?.map { (it / 1000f).coerceIn(0f, 1f) }
            return Detection(
                label = name.trim(),
                category = AnimalCategory.fromName(category).name,
                confidence = confidence.coerceIn(0f, 1f),
                left = b?.get(1) ?: 0f,
                top = b?.get(0) ?: 0f,
                right = b?.get(3) ?: 0f,
                bottom = b?.get(2) ?: 0f,
                rawLabel = scientificName.trim().ifEmpty { name.trim() },
            )
        }
    }

    companion object {
        private const val TAG = "CloudAnimalDetector"
        private const val TIMEOUT_MS = 20_000L
        /** Longest side of the uploaded JPEG: plenty for identification and quick to upload. */
        const val MAX_SIDE = 1024
        const val JPEG_QUALITY = 85
    }
}
