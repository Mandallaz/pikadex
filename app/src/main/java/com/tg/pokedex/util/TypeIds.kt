package com.tg.pokedex.util

/** Stable PokeAPI ids for the 18 standard types, used when only the type name is at hand
 * (e.g. the weakness/resistance matrix) and not the full resource with its url. */
object TypeIds {
    private val ids = mapOf(
        "normal" to 1, "fighting" to 2, "flying" to 3, "poison" to 4,
        "ground" to 5, "rock" to 6, "bug" to 7, "ghost" to 8,
        "steel" to 9, "fire" to 10, "water" to 11, "grass" to 12,
        "electric" to 13, "psychic" to 14, "ice" to 15, "dragon" to 16,
        "dark" to 17, "fairy" to 18
    )

    fun of(typeName: String): Int = ids[typeName.lowercase()] ?: 0

    val standardTypeNames: List<String> get() = ids.keys.toList()
}
