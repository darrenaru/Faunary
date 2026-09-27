package com.faunary.app.remote

import com.faunary.app.FaunaryConfig
import com.faunary.app.data.Detection
import com.faunary.app.domain.AnimalCategory
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileDto(
    val id: String,
    @SerialName("display_name") val displayName: String,
)

/** Row written to `sightings` (the id is generated client-side so the photo path can reuse it). */
@Serializable
data class SightingDto(
    val id: String,
    @SerialName("animal_label") val animalLabel: String,
    val category: String,
    val confidence: Float,
    @SerialName("ai_label") val aiLabel: String?,
    val latitude: Double,
    val longitude: Double,
    @SerialName("location_name") val locationName: String?,
    val note: String?,
    @SerialName("photo_path") val photoPath: String,
    @SerialName("photo_width") val photoWidth: Int,
    @SerialName("photo_height") val photoHeight: Int,
    val detections: List<Detection>,
    @SerialName("taken_at_ms") val takenAtMs: Long,
)

/** Row read from the `community_sightings` view. */
@Serializable
data class CommunitySighting(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("animal_label") val animalLabel: String,
    val category: String,
    val confidence: Float = 0f,
    @SerialName("ai_label") val aiLabel: String? = null,
    val latitude: Double,
    val longitude: Double,
    @SerialName("location_name") val locationName: String? = null,
    val note: String? = null,
    @SerialName("photo_path") val photoPath: String,
    @SerialName("photo_width") val photoWidth: Int = 0,
    @SerialName("photo_height") val photoHeight: Int = 0,
    val detections: List<Detection> = emptyList(),
    @SerialName("taken_at_ms") val takenAtMs: Long,
    @SerialName("display_name") val displayName: String = "Penjelajah",
) {
    val animalCategory: AnimalCategory get() = AnimalCategory.fromName(category)
    val photoUrl: String get() = publicPhotoUrl(photoPath)
    val aspectRatio: Float get() = if (photoHeight > 0) photoWidth.toFloat() / photoHeight else 4f / 3f
    val isAiDetected: Boolean get() = aiLabel != null && confidence > 0f
}

@Serializable
data class LiveLocationDto(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

fun publicPhotoUrl(path: String): String =
    "${FaunaryConfig.SUPABASE_URL.trimEnd('/')}/storage/v1/object/public/$PHOTO_BUCKET/$path"
