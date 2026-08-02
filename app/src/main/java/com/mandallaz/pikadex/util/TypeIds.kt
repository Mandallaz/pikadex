package com.mandallaz.pikadex.util

/** Stable PokeAPI ids for the 18 standard types, used when only the type name is at hand
 * (e.g. the weakness/resistance matrix) and not the full resource with its url.
 *
 * Map insertion order is the canonical Pokedex type-chart reading order (Normal, Fire, Water,
 * Electric, Grass, Ice, Fighting, Poison, Ground, Flying, Psychic, Bug, Rock, Ghost, Dragon,
 * Dark, Steel, Fairy) rather than PokeAPI's own id order, since [standardTypeNames] drives the
 * display order for the type filter row and the team matchup matrix. */
object TypeIds {
    private val ids = linkedMapOf(
        "normal" to 1, "fire" to 10, "water" to 11, "electric" to 13,
        "grass" to 12, "ice" to 15, "fighting" to 2, "poison" to 4,
        "ground" to 5, "flying" to 3, "psychic" to 14, "bug" to 7,
        "rock" to 6, "ghost" to 8, "dragon" to 16, "dark" to 17,
        "steel" to 9, "fairy" to 18
    )

    fun of(typeName: String): Int = ids[typeName.lowercase()] ?: 0

    val standardTypeNames: List<String> get() = ids.keys.toList()
}
