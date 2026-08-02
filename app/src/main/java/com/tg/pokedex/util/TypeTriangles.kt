package com.tg.pokedex.util

/**
 * A handful of well-known cyclic type match-ups: types listed in beats-order (A beats B, B beats
 * C, C beats A). Verified against the real damage_relations data before hardcoding, since these
 * are a curated/named reference set rather than every 3-cycle the 18-type chart contains.
 */
data class TypeTriangle(val title: String, val types: List<String>, val note: String)

object TypeTriangles {
    val ALL = listOf(
        TypeTriangle(
            title = "The Classic Starter Triangle",
            types = listOf("fire", "grass", "water"),
            note = "Fire beats Grass, Grass beats Water, and Water beats Fire — the very first type match-up every trainer learns."
        ),
        TypeTriangle(
            title = "Dark / Psychic / Fighting",
            types = listOf("dark", "psychic", "fighting"),
            note = "Fighting beats Dark, Dark beats Psychic, and Psychic beats Fighting."
        ),
        TypeTriangle(
            title = "Fire / Steel / Rock",
            types = listOf("fire", "steel", "rock"),
            note = "Fire beats Steel, Steel beats Rock, and Rock beats Fire."
        ),
        TypeTriangle(
            title = "Ground / Poison / Grass",
            types = listOf("ground", "poison", "grass"),
            note = "Ground beats Poison, Poison beats Grass, and Grass beats Ground."
        ),
        TypeTriangle(
            title = "Fighting / Rock / Flying",
            types = listOf("fighting", "rock", "flying"),
            note = "Fighting beats Rock, Rock beats Flying, and Flying beats Fighting."
        )
    )
}
