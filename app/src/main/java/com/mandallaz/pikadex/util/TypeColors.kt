package com.mandallaz.pikadex.util

import androidx.compose.ui.graphics.Color

/** Standard per-type color palette, consistent with the games and common pokedex sites. */
object TypeColors {
    private val colors = mapOf(
        "normal" to Color(0xFFA8A878),
        "fire" to Color(0xFFF08030),
        "water" to Color(0xFF6890F0),
        "electric" to Color(0xFFF8D030),
        "grass" to Color(0xFF78C850),
        "ice" to Color(0xFF98D8D8),
        "fighting" to Color(0xFFC03028),
        "poison" to Color(0xFFA040A0),
        "ground" to Color(0xFFE0C068),
        "flying" to Color(0xFFA890F0),
        "psychic" to Color(0xFFF85888),
        "bug" to Color(0xFFA8B820),
        "rock" to Color(0xFFB8A038),
        "ghost" to Color(0xFF705898),
        "dragon" to Color(0xFF7038F8),
        "dark" to Color(0xFF705848),
        "steel" to Color(0xFFB8B8D0),
        "fairy" to Color(0xFFEE99AC),
        "stellar" to Color(0xFF40B5A8),
        "unknown" to Color(0xFF68A090)
    )

    fun of(typeName: String): Color = colors[typeName.lowercase()] ?: Color(0xFF68A090)
}

/** Colors for the 6 base stats, in the usual HP/Atk/Def/SpAtk/SpDef/Speed order. */
object StatColors {
    private val colors = mapOf(
        "hp" to Color(0xFFFF5959),
        "attack" to Color(0xFFF5AC78),
        "defense" to Color(0xFFFAE078),
        "special-attack" to Color(0xFF9DB7F5),
        "special-defense" to Color(0xFFA7DB8D),
        "speed" to Color(0xFFFA92B2)
    )

    fun of(statName: String): Color = colors[statName.lowercase()] ?: Color(0xFFB0B0B0)
}

fun String.toDisplayName(): String =
    split("-").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
