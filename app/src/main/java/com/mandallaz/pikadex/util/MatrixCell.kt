package com.mandallaz.pikadex.util

import androidx.compose.ui.graphics.Color

/** Text label for one coverage-matrix cell's multiplier value. */
fun multiplierLabel(multiplier: Double): String = when (multiplier) {
    4.0 -> "×4"
    2.0 -> "×2"
    0.5 -> "×½"
    0.25 -> "×¼"
    0.0 -> "×0"
    else -> ""
}

/** Background/content color pair per bucket. The tinted buckets use fixed, explicitly dark text on
 *  a fixed, explicitly light background regardless of app theme — these pastel fills were designed
 *  as light-mode swatches, and pairing them with the *theme's* default text color meant near-white
 *  text on a light pink/blue/green background in dark mode, illegible. The neutral (1x) bucket has
 *  no fill of its own, so its text keeps following the theme's normal contrast (Color.Unspecified
 *  resolves to the current LocalContentColor). */
/**
 * Background/foreground for one matrix cell.
 *
 * The same multiplier means opposite things in the two modes — ×2 *taken* is a problem, ×2 *dealt*
 * is an advantage — so the palette keys on whether the number is good news for the player rather
 * than on the number itself. Sharing one scale between both turned the offense matrix into a wall
 * of red danger cells reporting what was actually a well-covered team.
 *
 * Blue stays reserved for a defensive immunity, the one genuinely special case; dealing ×0 is
 * simply the worst offensive outcome and reads as such.
 */
fun multiplierColors(multiplier: Double, isOffense: Boolean = false): Pair<Color, Color> {
    val bad = Color(0xFFFFCDD2) to Color(0xFFB71C1C)
    val good = Color(0xFFC8E6C9) to Color(0xFF1B5E20)
    val immune = Color(0xFFB3E5FC) to Color(0xFF01579B)
    return when {
        multiplier == 1.0 -> Color.Transparent to Color.Unspecified
        multiplier == 0.0 -> if (isOffense) bad else immune
        multiplier > 1.0 -> if (isOffense) good else bad
        else -> if (isOffense) bad else good
    }
}
