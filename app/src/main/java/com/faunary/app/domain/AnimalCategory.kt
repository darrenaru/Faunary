package com.faunary.app.domain

/** Top-level buckets used for map/gallery filters (matches the design's chip row). */
enum class AnimalCategory(val displayName: String, val emoji: String) {
    CAT("Kucing", "🐱"),
    DOG("Anjing", "🐶"),
    BIRD("Burung", "🐦"),
    WILD("Satwa Liar", "🦎"),
    OTHER("Lainnya", "🐾");

    companion object {
        fun fromName(name: String?): AnimalCategory =
            entries.firstOrNull { it.name == name } ?: OTHER
    }
}

data class Species(val displayName: String, val category: AnimalCategory, val priority: Int = 10)

/**
 * Maps English labels from ML Kit's base image-labeling model onto Indonesian names.
 * Labels not listed here are treated as "not an animal" and ignored.
 */
object SpeciesCatalog {
    private val byLabel: Map<String, Species> = mapOf(
        "cat" to Species("Kucing", AnimalCategory.CAT),
        "kitten" to Species("Anak Kucing", AnimalCategory.CAT),
        "dog" to Species("Anjing", AnimalCategory.DOG),
        "puppy" to Species("Anak Anjing", AnimalCategory.DOG),
        "bird" to Species("Burung", AnimalCategory.BIRD),
        "duck" to Species("Bebek", AnimalCategory.BIRD),
        "chicken" to Species("Ayam", AnimalCategory.BIRD),
        "penguin" to Species("Penguin", AnimalCategory.BIRD),
        "horse" to Species("Kuda", AnimalCategory.WILD),
        "cattle" to Species("Sapi", AnimalCategory.WILD),
        "bull" to Species("Sapi", AnimalCategory.WILD),
        "cow" to Species("Sapi", AnimalCategory.WILD),
        "sheep" to Species("Domba", AnimalCategory.WILD),
        "goat" to Species("Kambing", AnimalCategory.WILD),
        "monkey" to Species("Monyet", AnimalCategory.WILD),
        "butterfly" to Species("Kupu-kupu", AnimalCategory.WILD),
        "moths and butterflies" to Species("Kupu-kupu", AnimalCategory.WILD),
        "insect" to Species("Serangga", AnimalCategory.WILD, priority = 5),
        "bee" to Species("Lebah", AnimalCategory.WILD),
        "spider" to Species("Laba-laba", AnimalCategory.WILD),
        "fish" to Species("Ikan", AnimalCategory.WILD),
        "goldfish" to Species("Ikan Mas Koki", AnimalCategory.WILD),
        "turtle" to Species("Kura-kura", AnimalCategory.WILD),
        "lizard" to Species("Kadal", AnimalCategory.WILD),
        "reptile" to Species("Reptil", AnimalCategory.WILD, priority = 5),
        "snake" to Species("Ular", AnimalCategory.WILD),
        "frog" to Species("Katak", AnimalCategory.WILD),
        "crab" to Species("Kepiting", AnimalCategory.WILD),
        "bear" to Species("Beruang", AnimalCategory.WILD),
        "rabbit" to Species("Kelinci", AnimalCategory.WILD),
        "squirrel" to Species("Tupai", AnimalCategory.WILD),
        "rat" to Species("Tikus", AnimalCategory.WILD),
        "mouse" to Species("Tikus", AnimalCategory.WILD),
        "elephant" to Species("Gajah", AnimalCategory.WILD),
        "deer" to Species("Rusa", AnimalCategory.WILD),
        "pet" to Species("Hewan Peliharaan", AnimalCategory.OTHER, priority = 1),
    )

    fun lookup(label: String): Species? = byLabel[label.trim().lowercase()]

    /** Best guess category for a label typed by the user. */
    fun categoryForUserLabel(label: String): AnimalCategory {
        val l = label.lowercase()
        return when {
            listOf("kucing", "cat", "meong").any { it in l } -> AnimalCategory.CAT
            listOf("anjing", "dog", "puppy", "guguk").any { it in l } -> AnimalCategory.DOG
            listOf("burung", "bird", "bebek", "ayam", "merpati", "pipit", "elang", "kutilang", "gagak").any { it in l } -> AnimalCategory.BIRD
            byLabel.values.any { it.category == AnimalCategory.WILD && it.displayName.lowercase() in l } -> AnimalCategory.WILD
            else -> AnimalCategory.OTHER
        }
    }

    val quickPicks = listOf("Kucing", "Anjing", "Burung", "Kupu-kupu", "Kadal", "Monyet", "Tupai", "Ayam")
}
