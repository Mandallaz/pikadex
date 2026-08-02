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

/**
 * Colors a base-stat bar by how that value ranks against every other Pokemon's same stat,
 * rather than a fixed per-stat hue (which said nothing about whether e.g. a Speed of 5 was good
 * or bad). A diverging red -> yellow -> green sweep, low percentile = red ("bad"), high = green
 * ("good"), with yellow as the distinguishable midpoint — a plain red-to-green blend collapses
 * for red-green color blindness (the most common form), so the hue sweeps through yellow instead
 * of mixing directly, keeping both ends distinguishable under deuteranopia/protanopia.
 */
object StatColors {
    /** [percentile] in 0.0..1.0 — the fraction of all Pokemon this value is greater than or equal to. */
    fun forPercentile(percentile: Double): Color {
        val hue = (percentile.coerceIn(0.0, 1.0) * 120.0).toFloat() // 0=red, 60=yellow, 120=green
        val hsv = floatArrayOf(hue, 0.75f, 0.85f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }
}

fun String.toDisplayName(): String =
    split("-").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
