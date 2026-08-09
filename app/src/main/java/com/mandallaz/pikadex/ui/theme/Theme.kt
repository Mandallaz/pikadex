package com.mandallaz.pikadex.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val PokedexRed = Color(0xFFE3350D)
private val PokedexRedDark = Color(0xFFB92B0C)
private val PokedexBlue = Color(0xFF2A75BB)
private val PokedexYellow = Color(0xFFFFCB05)

private val LightColors = lightColorScheme(
    primary = PokedexRed,
    onPrimary = Color.White,
    secondary = PokedexBlue,
    tertiary = PokedexYellow,
    background = Color(0xFFF7F7F8),
    surface = Color.White
)

private val DarkColors = darkColorScheme(
    primary = PokedexRedDark,
    onPrimary = Color.White,
    secondary = PokedexBlue,
    tertiary = PokedexYellow,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E)
)

/** Same as [DarkColors] but background/surface dropped to pure black (BACKLOG.md F19) — Material's
 *  dark grey (`#121212`/`#1E1E1E`) still lights up every pixel on an AMOLED panel; true black turns
 *  those pixels off entirely, which is the whole point of an AMOLED mode. */
private val AmoledDarkColors = DarkColors.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color.Black
)

/** Pure selection logic, kept separate from [PokeDexTheme] so it's testable without a Compose
 *  runtime: dynamic color (wallpaper-derived, API 31+) isn't covered here since it needs a
 *  [android.content.Context] and is resolved directly in the composable below. */
internal fun selectColorScheme(darkTheme: Boolean, amoledBlack: Boolean): ColorScheme = when {
    darkTheme && amoledBlack -> AmoledDarkColors
    darkTheme -> DarkColors
    else -> LightColors
}

@Composable
fun PokeDexTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Defaults off: on API 31+ this derives the whole scheme from the user's wallpaper, which used
    // to silently replace PikaDex's own red/blue/yellow brand palette above with whatever colors
    // happen to be dynamic-themed that day — a Pokédex that doesn't reliably look like a Pokédex.
    // Left as a parameter (not deleted) in case a future settings toggle wants to offer it as an
    // opt-in.
    dynamicColor: Boolean = false,
    // Settings-backed (DisplaySettings.amoledEnabled) — see F19 in BACKLOG.md.
    amoledBlack: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> selectColorScheme(darkTheme, amoledBlack)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
