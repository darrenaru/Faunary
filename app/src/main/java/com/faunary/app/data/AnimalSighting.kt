package com.faunary.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.faunary.app.domain.AnimalCategory
import kotlinx.serialization.Serializable

/** A bounding box in coordinates relative to the photo (0..1). */
@Serializable
data class Detection(
    val label: String,
    val category: String,
    val confidence: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    /** Raw ML Kit label, kept so manual corrections can be measured. */
    val rawLabel: String = label,
) {
    val animalCategory: AnimalCategory get() = AnimalCategory.fromName(category)
    val hasBox: Boolean get() = right > left && bottom > top
}

@Entity(tableName = "animal_sightings")
data class AnimalSighting(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val photoPath: String,
    val photoWidth: Int,
    val photoHeight: Int,
    val animalLabel: String,
    val category: String,
    val confidence: Float,
    val boundingBoxLeft: Float,
    val boundingBoxTop: Float,
    val boundingBoxRight: Float,
    val boundingBoxBottom: Float,
    /** Every detection in the photo, including the primary one above. */
    val detections: List<Detection>,
    /** Label the AI originally proposed; null when the entry was labelled manually. */
    val aiLabel: String?,
    val latitude: Double,
    val longitude: Double,
    val locationAccuracy: Float?,
    val locationManual: Boolean,
    val locationName: String?,
    val address: String?,
    val note: String?,
    val isFavorite: Boolean = false,
    val timestamp: Long,
) {
    val animalCategory: AnimalCategory get() = AnimalCategory.fromName(category)
    val isAiDetected: Boolean get() = aiLabel != null && confidence > 0f
    val wasCorrected: Boolean get() = aiLabel != null && !aiLabel.equals(animalLabel, ignoreCase = true)
    val aspectRatio: Float get() = if (photoHeight > 0) photoWidth.toFloat() / photoHeight else 4f / 3f
}
