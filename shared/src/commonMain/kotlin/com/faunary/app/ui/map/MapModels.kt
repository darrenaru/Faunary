package com.faunary.app.ui.map

import com.faunary.app.domain.AnimalCategory
import com.faunary.app.location.GeoPoint

/**
 * Map layers, drawn bottom to top in declaration order. Live explorers sit at the bottom:
 * people usually stand where they just took a photo, and the animal photo must stay visible and tappable.
 * Shared markers (PIN) are drawn one by one; only photos are merged into stacks. The searched
 * place (PLACE) sits on top of everything.
 */
enum class MarkerKind { LIVE, PIN, OWN, COMMUNITY, PLACE }

data class MapMarker(
    /** Unique across layers, e.g. "own:12", "com:<uuid>", "live:<uuid>". */
    val key: String,
    val latitude: Double,
    val longitude: Double,
    /** Local file path or https URL; null draws the category icon instead. */
    val photo: String?,
    val category: AnimalCategory,
    val kind: MarkerKind = MarkerKind.OWN,
    /** Name shown under live-user markers. */
    val label: String? = null,
    /** When the photo was taken; the newest photo sits on top of a stack. */
    val time: Long = 0L,
    /** Icon of a shared marker ([MarkerKind.PIN]). */
    val pinIcon: PinIcon? = null,
)

/**
 * Someone else's route shown on the map: the line still ahead of them and where it ends. [startedAt]
 * (their clock, ms) identifies the route; one that has just started is drawn in with an animation.
 */
data class SharedRouteLine(
    val key: String,
    val points: List<Pair<Double, Double>>,
    val destLat: Double,
    val destLng: Double,
    val startedAt: Long,
)

/** Default camera when there is no data or GPS yet (Taman Suropati, Jakarta — as in the design). */
val DefaultCenter = GeoPoint(-6.1990, 106.8322)
