package com.faunary.app.domain

import com.faunary.app.data.AnimalSighting
import com.faunary.app.util.Geo
import com.faunary.app.util.currentTimeMillis
import com.faunary.app.util.localDateTime

data class LabelCount(val label: String, val category: AnimalCategory, val count: Int)

data class CollectionStats(
    val total: Int = 0,
    val thisWeek: Int = 0,
    val distinctSpots: Int = 0,
    val distinctSpecies: Int = 0,
    val byCategory: Map<AnimalCategory, Int> = emptyMap(),
    val topLabels: List<LabelCount> = emptyList(),
    val streakDays: Int = 0,
    val topArea: String? = null,
    /** Share of AI detections accepted without manual correction. */
    val aiAcceptance: Float? = null,
) {
    companion object {
        fun from(items: List<AnimalSighting>, now: Long = currentTimeMillis()): CollectionStats {
            if (items.isEmpty()) return CollectionStats()
            val weekAgo = now - 7 * 24 * 60 * 60 * 1000L
            val aiItems = items.filter { it.aiLabel != null }
            return CollectionStats(
                total = items.size,
                thisWeek = items.count { it.timestamp >= weekAgo },
                distinctSpots = items.map { Geo.cellKey(it.latitude, it.longitude) }.toSet().size,
                distinctSpecies = items.map { it.animalLabel.lowercase() }.toSet().size,
                byCategory = items.groupingBy { it.animalCategory }.eachCount(),
                topLabels = items.groupBy { it.animalLabel }
                    .map { (label, list) -> LabelCount(label, list.first().animalCategory, list.size) }
                    .sortedByDescending { it.count }
                    .take(5),
                streakDays = streak(items.map { it.timestamp }, now),
                topArea = items.mapNotNull { it.locationName?.substringAfterLast(", ") }
                    .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key,
                aiAcceptance = if (aiItems.isEmpty()) null else aiItems.count { !it.wasCorrected }.toFloat() / aiItems.size,
            )
        }

        /** Consecutive days (ending today or yesterday) that have at least one entry. */
        private fun streak(timestamps: List<Long>, now: Long): Int {
            val days = timestamps.map { dayIndex(it) }.toSet()
            var day = dayIndex(now)
            if (day !in days) day -= 1
            var count = 0
            while (day in days) { count++; day-- }
            return count
        }

        private fun dayIndex(ts: Long): Long = localDateTime(ts).date.toEpochDays()

        /** Entries recorded on today's calendar date in previous years ("on this day"). */
        fun onThisDay(items: List<AnimalSighting>, now: Long = currentTimeMillis()): List<AnimalSighting> {
            val today = localDateTime(now).date
            return items.filter {
                val d = localDateTime(it.timestamp).date
                d.year < today.year && d.month == today.month && d.day == today.day
            }
        }
    }
}
