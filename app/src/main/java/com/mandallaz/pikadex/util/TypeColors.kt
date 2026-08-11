package com.mandallaz.pikadex.util

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.mandallaz.pikadex.R

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

/** B39 — [typeName]'s @StringRes translated name, or null for a type with no dedicated
 *  translation (PokeAPI's "unknown" placeholder, or any type introduced upstream after this map
 *  was written). Plain-Kotlin (no Compose dependency), same reasoning as [SortStat.labelRes] for
 *  why this lives here rather than resolving the string directly: the actual lookup happens at
 *  render time via [com.mandallaz.pikadex.ui.components.localizedTypeName]. */
private val TYPE_NAME_RES = mapOf(
    "normal" to R.string.type_normal,
    "fire" to R.string.type_fire,
    "water" to R.string.type_water,
    "electric" to R.string.type_electric,
    "grass" to R.string.type_grass,
    "ice" to R.string.type_ice,
    "fighting" to R.string.type_fighting,
    "poison" to R.string.type_poison,
    "ground" to R.string.type_ground,
    "flying" to R.string.type_flying,
    "psychic" to R.string.type_psychic,
    "bug" to R.string.type_bug,
    "rock" to R.string.type_rock,
    "ghost" to R.string.type_ghost,
    "dragon" to R.string.type_dragon,
    "dark" to R.string.type_dark,
    "steel" to R.string.type_steel,
    "fairy" to R.string.type_fairy,
    "stellar" to R.string.type_stellar
)

@StringRes
fun typeNameRes(typeName: String): Int? = TYPE_NAME_RES[typeName.lowercase()]

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

// PokeAPI's slugs lose punctuation the real name has (Ho-Oh, Porygon-Z, Mime Jr., Farfetch'd...),
// so a blind "split on hyphen, title-case each word, join with spaces" turns them into wrong new
// names ("Ho Oh", "Porygon Z") rather than just ugly ones. Covers the handful of species where
// this actually changes the displayed name; everything else (moves, abilities, ordinary species)
// still falls through to the generic rule below.
private val SPECIAL_DISPLAY_NAMES = mapOf(
    "hp" to "HP",
    "ho-oh" to "Ho-Oh",
    "porygon-z" to "Porygon-Z",
    "mime-jr" to "Mime Jr.",
    "mr-mime" to "Mr. Mime",
    "mr-rime" to "Mr. Rime",
    "farfetchd" to "Farfetch'd",
    "sirfetchd" to "Sirfetch'd",
    "nidoran-f" to "Nidoran♀",
    "nidoran-m" to "Nidoran♂",
    "type-null" to "Type: Null",
    "jangmo-o" to "Jangmo-o",
    "hakamo-o" to "Hakamo-o",
    "kommo-o" to "Kommo-o",
    "flabebe" to "Flabébé"
)

fun String.toDisplayName(): String {
    SPECIAL_DISPLAY_NAMES[lowercase()]?.let { return it }
    return split("-").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
}
